"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { submissionsApi } from "@/lib/submissions-api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { StatusBadge, isPending } from "@/components/status-badge";

export default function MySubmissionsPage() {
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ["my-submissions", page],
    queryFn: () => submissionsApi.me(page, 20),
    refetchInterval: (q) => {
      const data = q.state.data;
      if (!data) return false;
      return data.content.some(isPending) ? 1000 : false;
    },
  });

  return (
    <main className="max-w-6xl mx-auto p-6 space-y-4">
      <h1 className="text-2xl font-semibold">내 제출</h1>

      {list.isLoading && <p className="text-muted-foreground">불러오는 중...</p>}
      {list.isError && (
        <p className="text-destructive">목록을 불러올 수 없습니다</p>
      )}

      {list.data && (
        <Card className="divide-y overflow-hidden">
          {list.data.empty && (
            <div className="p-6 text-muted-foreground text-center">
              아직 제출 내역이 없습니다
            </div>
          )}
          {list.data.content.map((s) => (
            <Link
              key={s.id}
              href={`/submissions/${s.id}`}
              className="flex items-center justify-between p-4 hover:bg-muted/50"
            >
              <div className="flex items-center gap-4 min-w-0">
                <span className="text-muted-foreground text-sm w-12 shrink-0">
                  #{s.id}
                </span>
                <span className="font-medium truncate">{s.problemTitle}</span>
                <span className="text-xs text-muted-foreground">{s.language}</span>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                {s.runtime !== null && (
                  <span className="text-xs text-muted-foreground">
                    {s.runtime}ms / {s.memory}KB
                  </span>
                )}
                <StatusBadge
                  status={s.status}
                  passed={s.passedTestCases}
                  total={s.totalTestCases}
                />
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
