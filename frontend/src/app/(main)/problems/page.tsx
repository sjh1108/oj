"use client";

import { useQuery } from "@tanstack/react-query";
import { CircleCheckIcon, CircleDotIcon, SearchIcon, TagIcon } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { problemsApi } from "@/lib/problems-api";
import { usePreferences } from "@/lib/preferences";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { DifficultyBadge } from "@/components/status-badge";
import type { Difficulty, SolvedFilter } from "@/types/api";

const DIFFICULTY_OPTIONS: { value: Difficulty | ""; label: string }[] = [
  { value: "", label: "난이도 전체" },
  { value: "BRONZE", label: "브론즈" },
  { value: "SILVER", label: "실버" },
  { value: "GOLD", label: "골드" },
  { value: "PLATINUM", label: "플래티넘" },
  { value: "DIAMOND", label: "다이아" },
];

const SOLVED_OPTIONS: { value: SolvedFilter; label: string }[] = [
  { value: "ALL", label: "상태 전체" },
  { value: "SOLVED", label: "맞은 문제" },
  { value: "ATTEMPTED", label: "도전 중" },
  { value: "UNSOLVED", label: "안 푼 문제" },
];

const SELECT_CLASS =
  "h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50";

export default function ProblemsPage() {
  const [page, setPage] = useState(0);
  const size = 20;

  // The keyword only hits the API when the user submits the search form.
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [difficulty, setDifficulty] = useState<Difficulty | "">("");
  const [tag, setTag] = useState("");
  const [solved, setSolved] = useState<SolvedFilter>("ALL");

  // 태그 노출 여부는 계정 설정 — 내 계정·문제 페이지와 같은 값을 쓴다.
  const { preferences, updatePreferences } = usePreferences();

  const list = useQuery({
    queryKey: ["problems", page, keyword, difficulty, tag, solved],
    queryFn: () => problemsApi.list({ page, size, keyword, difficulty, tag, solved }),
  });

  const tags = useQuery({
    queryKey: ["problem-tags"],
    queryFn: () => problemsApi.tags(),
  });

  const resetPage = () => setPage(0);

  return (
    <main className="max-w-6xl mx-auto p-6 space-y-4">
      <h1 className="text-2xl font-semibold">문제 목록</h1>

      <div className="flex flex-wrap items-center gap-2">
        <form
          className="relative flex-1 min-w-48"
          onSubmit={(e) => {
            e.preventDefault();
            setKeyword(keywordInput);
            resetPage();
          }}
        >
          <SearchIcon className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            placeholder="제목 검색 (Enter)"
            className="pl-9"
          />
        </form>
        <select
          value={difficulty}
          onChange={(e) => {
            setDifficulty(e.target.value as Difficulty | "");
            resetPage();
          }}
          className={SELECT_CLASS}
          aria-label="난이도 필터"
        >
          {DIFFICULTY_OPTIONS.map((d) => (
            <option key={d.value} value={d.value}>
              {d.label}
            </option>
          ))}
        </select>
        <select
          value={tag}
          onChange={(e) => {
            setTag(e.target.value);
            resetPage();
          }}
          className={SELECT_CLASS}
          aria-label="태그 필터"
        >
          <option value="">태그 전체</option>
          {(tags.data ?? []).map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <select
          value={solved}
          onChange={(e) => {
            setSolved(e.target.value as SolvedFilter);
            resetPage();
          }}
          className={SELECT_CLASS}
          aria-label="풀이 상태 필터"
        >
          {SOLVED_OPTIONS.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        <Button
          type="button"
          variant={preferences.showTags ? "secondary" : "outline"}
          size="lg"
          aria-pressed={preferences.showTags}
          title="태그를 목록과 문제 페이지에 항상 표시합니다 (계정에 저장)"
          onClick={() => updatePreferences({ showTags: !preferences.showTags })}
        >
          <TagIcon className="size-4" />
          {preferences.showTags ? "태그 숨기기" : "태그 보기"}
        </Button>
      </div>

      {list.isLoading && (
        <p className="text-muted-foreground">불러오는 중...</p>
      )}
      {list.isError && (
        <p className="text-destructive">목록을 불러올 수 없습니다</p>
      )}

      {list.data && (
        <Card className="divide-y overflow-hidden">
          {list.data.empty && (
            <div className="p-6 text-muted-foreground text-center">
              조건에 맞는 문제가 없습니다
            </div>
          )}
          {list.data.content.map((p) => (
            <Link
              key={p.id}
              href={`/problems/${p.id}`}
              className="flex items-center justify-between gap-3 p-4 hover:bg-muted/50"
            >
              <div className="flex items-center gap-3 min-w-0">
                <span className="w-4 shrink-0">
                  {p.solved && (
                    <CircleCheckIcon
                      className="size-4 text-green-500"
                      aria-label="맞은 문제"
                    />
                  )}
                  {p.attempted && (
                    <CircleDotIcon
                      className="size-4 text-amber-500"
                      aria-label="도전 중"
                    />
                  )}
                </span>
                <span className="text-muted-foreground text-sm w-10 shrink-0">
                  #{p.id}
                </span>
                <span className="font-medium truncate">{p.title}</span>
                {!p.isPublic && (
                  <span className="text-xs text-muted-foreground shrink-0">
                    (비공개)
                  </span>
                )}
                {/* 태그는 풀이 방향을 알려주는 스포일러라 기본은 감춘다.
                    보기를 켠 사람에게만 노출한다(위의 "태그 보기" 토글). */}
                {preferences.showTags &&
                  p.tags.map((t) => (
                    <Badge
                      key={t}
                      variant="outline"
                      className="hidden sm:inline-flex shrink-0 text-muted-foreground"
                    >
                      {t}
                    </Badge>
                  ))}
              </div>
              <div className="flex items-center gap-3 shrink-0">
                {p.authorUsername && (
                  <span className="text-xs text-muted-foreground">
                    {p.authorUsername}
                  </span>
                )}
                <span className="text-xs text-muted-foreground">
                  {new Date(p.createdAt).toLocaleDateString("ko-KR")}
                </span>
                <DifficultyBadge difficulty={p.difficulty} />
              </div>
            </Link>
          ))}
        </Card>
      )}

      {list.data && list.data.totalPages > 1 && (
        <div className="flex justify-center items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={list.data.first}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            이전
          </Button>
          <span className="text-sm text-muted-foreground">
            {list.data.number + 1} / {list.data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={list.data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}
    </main>
  );
}
