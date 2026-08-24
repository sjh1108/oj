"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, NotebookPen } from "lucide-react";
import { toast } from "sonner";

import { Textarea } from "@/components/ui/textarea";
import { useAuthStore } from "@/lib/auth-store";
import { problemsApi } from "@/lib/problems-api";
import { cn } from "@/lib/utils";
import type { ProblemNoteResponse } from "@/types/api";

// 메모 한 편의 상한 — 서버(ProblemNote.MAX_LENGTH)와 같은 값이어야 한다.
const MAX_LENGTH = 4000;

// 타이핑이 멈춘 뒤 저장까지의 대기. 코드 draft(300ms, localStorage)보다 길게 잡는다
// — 이쪽은 매번 네트워크를 타므로 글자마다 보내면 낭비다.
const SAVE_DELAY_MS = 1000;

// 펼침/접힘은 기기에 매인 보기 상태라 localStorage에 둔다(메모 본문은 계정에 저장).
const OPEN_KEY = "algoj-memo-open";

type SaveState = "idle" | "saving" | "saved";

function readOpenPreference(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(OPEN_KEY) === "1";
  } catch {
    return false;
  }
}

/**
 * 문제 페이지 에디터 아래에 붙는 문제별 메모.
 *
 * 저장 버튼이 없다 — 타이핑이 멈추면 자동으로 보내고, 저장 전에 페이지를 벗어나면
 * 남은 입력을 즉시 밀어낸다(코드 draft의 flushDraft와 같은 방식).
 */
export function MemoPanel({ problemId }: { problemId: number }) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isLoggedIn = Boolean(accessToken);
  const qc = useQueryClient();
  const queryKey = ["problem-note", problemId] as const;

  const [open, setOpen] = useState(false);
  const [value, setValue] = useState("");
  const [saveState, setSaveState] = useState<SaveState>("idle");

  // 저장 대기 타이머와 "아직 안 보낸 값" — 언마운트 때 이 값을 즉시 보낸다.
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pending = useRef<string | null>(null);
  const savedTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const note = useQuery({
    queryKey,
    queryFn: () => problemsApi.getNote(problemId),
    enabled: isLoggedIn,
    staleTime: 60 * 1000,
  });

  const save = useMutation({
    mutationFn: (content: string) => problemsApi.saveNote(problemId, content),
    onSuccess: (saved) => {
      qc.setQueryData<ProblemNoteResponse>(queryKey, saved);
      setSaveState("saved");
      if (savedTimer.current) clearTimeout(savedTimer.current);
      savedTimer.current = setTimeout(() => setSaveState("idle"), 2000);
    },
    onError: () => {
      setSaveState("idle");
      toast.error("메모를 저장하지 못했습니다");
    },
  });

  // 서버에서 받아온 메모를 채운다. 메모가 이미 있으면 펼친 채로 연다 —
  // 적어둔 것을 못 보고 지나치는 게 가장 나쁘다.
  //
  // 문제당 딱 한 번만 채운다. 저장이 끝나면 캐시가 갱신되는데, 그때 다시 채우면
  // 저장 요청을 보낸 뒤에 친 글자가 서버 사본으로 덮여 사라진다.
  const loaded = note.data;
  const hydratedFor = useRef<number | null>(null);
  useEffect(() => {
    if (!loaded || hydratedFor.current === problemId) return;
    hydratedFor.current = problemId;
    setValue(loaded.content ?? "");
    if (loaded.content) setOpen(true);
    else setOpen(readOpenPreference());
  }, [loaded, problemId]);

  // 저장되지 않은 입력을 즉시 보낸다(문제 이동·언마운트).
  const saveRef = useRef(save);
  saveRef.current = save;
  useEffect(() => {
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
      if (savedTimer.current) clearTimeout(savedTimer.current);
      if (pending.current !== null) {
        saveRef.current.mutate(pending.current);
        pending.current = null;
      }
    };
  }, [problemId]);

  function handleChange(next: string) {
    setValue(next.slice(0, MAX_LENGTH));
    pending.current = next.slice(0, MAX_LENGTH);
    setSaveState("saving");
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      const content = pending.current;
      pending.current = null;
      if (content !== null) save.mutate(content);
    }, SAVE_DELAY_MS);
  }

  function toggle() {
    const next = !open;
    setOpen(next);
    try {
      window.localStorage.setItem(OPEN_KEY, next ? "1" : "0");
    } catch {
      // 저장이 막힌 브라우저(시크릿 모드 등)에서도 접기/펼치기 자체는 동작해야 한다.
    }
  }

  if (!isLoggedIn) return null;

  return (
    <div className="rounded-lg border border-border overflow-hidden">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={`memo-body-${problemId}`}
        className="flex w-full items-center gap-2 px-3 py-2 text-sm hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <ChevronDown
          className={cn("size-4 text-muted-foreground transition-transform", !open && "-rotate-90")}
        />
        <NotebookPen className="size-4 text-muted-foreground" />
        <span className="font-medium">메모</span>
        <span className="text-xs text-muted-foreground">
          {value.trim() ? "이 문제에만 남습니다" : "아직 메모가 없습니다"}
        </span>
        <span className="flex-1" />
        {saveState !== "idle" && (
          <span className="text-xs text-muted-foreground">
            {saveState === "saving" ? "저장 중…" : "저장됨"}
          </span>
        )}
      </button>
      {open && (
        <div id={`memo-body-${problemId}`} className="px-3 pb-3 space-y-1">
          <Textarea
            value={value}
            onChange={(e) => handleChange(e.target.value)}
            maxLength={MAX_LENGTH}
            placeholder="풀면서 알아낸 것들을 적어두세요 — 제한, 반례, 점화식…"
            aria-label="문제 메모"
            className="min-h-24 text-sm"
          />
          <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
            <span>정답을 맞히면 이 메모가 그 제출 기록에 함께 남습니다</span>
            <span className="tabular-nums">
              {value.length.toLocaleString("ko-KR")} / {MAX_LENGTH.toLocaleString("ko-KR")}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
