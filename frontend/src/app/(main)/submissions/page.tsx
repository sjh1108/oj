"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { authApi } from "@/lib/auth-api";
import { submissionsApi } from "@/lib/submissions-api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { StatusBadge, isPending } from "@/components/status-badge";
import type { SubmissionResponse } from "@/types/api";

function useCanView() {
  const user = useAuthStore((s) => s.user);
  const solved = useQuery({
    queryKey: ["my-solved-problems"],
    queryFn: authApi.mySolvedProblems,
    staleTime: 60_000,
  });

  return (s: SubmissionResponse) => {
    if (!user) return false;
    if (s.username === user.username) return true;
    if (user.role === "ADMIN") return true;
    if (s.status !== "ACCEPTED") return false;
    if (!s.isPublic) return false;
    return solved.data?.includes(s.problemId) ?? false;
  };
}

export default function AllSubmissionsPage() {
  const [page, setPage] = useState(0);
  const canView = useCanView();

  const list = useQuery({
    queryKey: ["all-submissions", page],
    queryFn: () => submissionsApi.list(page, 30),
    refetchInterval: (q) => {
      const data = q.state.data;
      if (!data) return 5000;
      return data.content.some(isPending) ? 1000 : 5000;
    },
  });

  return (
    <main className="max-w-6xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">채점 현황</h1>
        <span className="text-xs text-muted-foreground">
          5초마다 갱신 · 채점 중일 땐 1초마다
        </span>
      </div>

      {list.isLoading && (
        <p className="text-muted-foreground">불러오는 중...</p>
      )}
      {list.isError && (
        <p className="text-destructive">목록을 불러올 수 없습니다</p>
      )}

      {list.data && (
        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/50">
              <tr className="text-left">
                <th className="p-3 w-16">#</th>
                <th className="p-3 w-32">사용자</th>
                <th className="p-3">문제</th>
                <th className="p-3 w-28">언어</th>
                <th className="p-3 w-32">시간/메모리</th>
                <th className="p-3 w-32 text-right">상태</th>
              </tr>
            </thead>
            <tbody>
              {list.data.empty && (
                <tr>
                  <td
                    className="p-6 text-muted-foreground text-center"
                    colSpan={6}
                  >
                    아직 제출 내역이 없습니다
                  </td>
                </tr>
              )}
              {list.data.content.map((s) => {
                const viewable = canView(s);
                const idCell = viewable ? (
                  <Link
                    href={`/submissions/${s.id}`}
                    className="text-foreground hover:underline"
                  >
                    {s.id}
                  </Link>
                ) : (
                  <span className="text-muted-foreground">{s.id}</span>
                );
                return (
                  <tr
                    key={s.id}
                    className={`border-t ${viewable ? "hover:bg-muted/40" : ""}`}
                  >
                    <td className="p-3">{idCell}</td>
                    <td className="p-3">{s.username}</td>
                    <td className="p-3">
                      <Link
                        href={`/problems/${s.problemId}`}
                        className="hover:underline"
                      >
                        #{s.problemId} {s.problemTitle}
                      </Link>
                    </td>
                    <td className="p-3 text-xs text-muted-foreground">
                      {s.language}
                    </td>
                    <td className="p-3 text-xs text-muted-foreground">
                      {s.runtime !== null
                        ? `${s.runtime}ms / ${s.memory}KB`
                        : "-"}
                    </td>
                    <td className="p-3 text-right">
                      <StatusBadge
                        status={s.status}
                        passed={s.passedTestCases}
                        total={s.totalTestCases}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
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
