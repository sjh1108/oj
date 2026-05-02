"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { problemsApi } from "@/lib/problems-api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DifficultyBadge } from "@/components/status-badge";

export default function ProblemsPage() {
  const [page, setPage] = useState(0);
  const size = 20;

  const list = useQuery({
    queryKey: ["problems", page],
    queryFn: () => problemsApi.list(page, size),
  });

  return (
    <main className="max-w-6xl mx-auto p-6 space-y-4">
      <h1 className="text-2xl font-semibold">문제 목록</h1>

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
              등록된 문제가 없습니다
            </div>
          )}
          {list.data.content.map((p) => (
            <Link
              key={p.id}
              href={`/problems/${p.id}`}
              className="flex items-center justify-between p-4 hover:bg-muted/50"
            >
              <div className="flex items-center gap-3">
                <span className="text-muted-foreground text-sm w-10">
                  #{p.id}
                </span>
                <span className="font-medium">{p.title}</span>
                {!p.isPublic && (
                  <span className="text-xs text-muted-foreground">(비공개)</span>
                )}
              </div>
              <DifficultyBadge difficulty={p.difficulty} />
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
