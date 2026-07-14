"use client";

import { useEffect, useState } from "react";

// User-tunable view settings for the problem page (editor + statement),
// persisted in localStorage so they stick across problems and visits.
export interface ViewSettings {
  editorFontSize: number;
  editorFontFamily: string; // "" = Monaco default
  wordWrap: boolean;
  statementFontSize: number;
}

export const DEFAULT_VIEW_SETTINGS: ViewSettings = {
  editorFontSize: 14,
  editorFontFamily: "",
  wordWrap: false,
  statementFontSize: 15,
};

// Local font stacks only — no webfont download; falls back to any installed one.
export const EDITOR_FONT_OPTIONS: { label: string; value: string }[] = [
  { label: "기본", value: "" },
  { label: "D2Coding", value: "'D2Coding', 'D2Coding ligature', monospace" },
  { label: "JetBrains Mono", value: "'JetBrains Mono', monospace" },
  { label: "Fira Code", value: "'Fira Code', monospace" },
  { label: "Consolas", value: "Consolas, 'Courier New', monospace" },
];

const STORAGE_KEY = "algoj-view-settings";

export function clampFontSize(n: number, min = 11, max = 24): number {
  return Math.min(max, Math.max(min, n));
}

export function useViewSettings(): [
  ViewSettings,
  (patch: Partial<ViewSettings>) => void,
] {
  const [settings, setSettings] = useState<ViewSettings>(DEFAULT_VIEW_SETTINGS);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (raw) setSettings({ ...DEFAULT_VIEW_SETTINGS, ...JSON.parse(raw) });
    } catch {
      // corrupt value — fall back to defaults
    }
  }, []);

  const update = (patch: Partial<ViewSettings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch };
      try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        // storage full/blocked — settings just won't persist
      }
      return next;
    });
  };

  return [settings, update];
}
