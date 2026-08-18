"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

import type { ParsedProblem } from "@/lib/problem-file";

/**
 * State of a bulk problem upload, kept in localStorage.
 *
 * Not an account setting: it describes files that only exist in this browser
 * and an upload that only this tab is running, so it must not follow the user
 * to another device. It is persisted because an upload can take minutes — the
 * admin leaves the page, and without this the batch (and the fact that a
 * half-uploaded problem was left private) vanished with the component.
 */

export type ImportItemStatus =
  | "parsed"
  | "parse_error"
  | "uploading"
  | "done"
  | "failed";

/** What the server currently holds for an item's problem. */
export interface ImportServerState {
  isPublic: boolean;
  caseCount: number;
  draftCaseCount: number;
  checkedAt: number;
}

export interface ImportItem {
  key: number;
  fileName: string;
  parsed?: ParsedProblem;
  // Set when the parsed file was too big to keep in localStorage. The row still
  // shows what happened, but retrying needs the file dropped in again.
  parsedDropped?: boolean;
  status: ImportItemStatus;
  message?: string;
  createdId?: number;
  // Live progress text ("케이스 3/5 생성 중").
  progress?: string;
  server?: ImportServerState;
}

interface ImportBatchState {
  items: ImportItem[];
  nextKey: number;
  // True while an upload loop is running. Persisted: if it is still true on a
  // fresh page load, the loop died with the old page.
  running: boolean;
  addItems: (items: Omit<ImportItem, "key">[]) => void;
  patchItem: (key: number, patch: Partial<ImportItem>) => void;
  removeItem: (key: number) => void;
  setItems: (items: ImportItem[]) => void;
  setRunning: (running: boolean) => void;
  clear: () => void;
}

// Keep the whole batch under a couple of MB — localStorage is ~5MB per origin
// and the auth store shares it.
const PERSISTED_PARSED_BUDGET = 1_500_000;

// A quota error during an upload must not take the upload down with it.
const safeStorage = createJSONStorage(() => ({
  getItem: (name: string) => localStorage.getItem(name),
  setItem: (name: string, value: string) => {
    try {
      localStorage.setItem(name, value);
    } catch {
      // Batch too large to persist — the in-memory run carries on.
    }
  },
  removeItem: (name: string) => localStorage.removeItem(name),
}));

/** Drops what can't be kept, largest-cost-last, so small batches persist whole. */
function persistableItems(items: ImportItem[]): ImportItem[] {
  let budget = PERSISTED_PARSED_BUDGET;
  return items.map((it) => {
    if (!it.parsed) return it;
    // Images are already on S3 once the problem exists; never re-persist them.
    const parsed =
      it.createdId != null && it.parsed.assets
        ? { ...it.parsed, assets: undefined }
        : it.parsed;
    const size = JSON.stringify(parsed).length;
    if (size <= budget) {
      budget -= size;
      return { ...it, parsed };
    }
    return { ...it, parsed: undefined, parsedDropped: true };
  });
}

export const useImportBatchStore = create<ImportBatchState>()(
  persist(
    (set) => ({
      items: [],
      nextKey: 1,
      running: false,
      addItems: (incoming) =>
        set((state) => {
          let key = state.nextKey;
          const added = incoming.map((it) => ({ ...it, key: key++ }));
          return { items: [...state.items, ...added], nextKey: key };
        }),
      patchItem: (key, patch) =>
        set((state) => ({
          items: state.items.map((it) =>
            it.key === key ? { ...it, ...patch } : it,
          ),
        })),
      removeItem: (key) =>
        set((state) => ({ items: state.items.filter((it) => it.key !== key) })),
      setItems: (items) => set({ items }),
      setRunning: (running) => set({ running }),
      clear: () => set({ items: [], running: false }),
    }),
    {
      name: "algoj-import-batch",
      storage: safeStorage,
      partialize: (state) => ({
        items: persistableItems(state.items),
        nextKey: state.nextKey,
        running: state.running,
      }),
    },
  ),
);

// Whether an upload loop is alive *in this JS context*. Module scope, so it
// survives the page component unmounting on a client-side navigation but is
// false after a reload — which is exactly how a dead run is detected.
let liveRun = false;

export const beginRun = () => {
  liveRun = true;
  useImportBatchStore.getState().setRunning(true);
};

export const endRun = () => {
  liveRun = false;
  useImportBatchStore.getState().setRunning(false);
};

export const isRunLive = () => liveRun;
