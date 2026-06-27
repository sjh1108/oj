"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { toast } from "sonner";

import { submissionsApi } from "@/lib/submissions-api";
import { useAuthStore } from "@/lib/auth-store";
import { ApiError } from "@/lib/api";
import {
  downloadTextFile,
  LANGUAGE_EXTENSION,
  sanitizeFilename,
} from "@/lib/download";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CodeEditor } from "@/components/code-editor";
import { StatusBadge, isPending } from "@/components/status-badge";

export default function SubmissionDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();

  const sub = useQuery({
    queryKey: ["submission", id],
    queryFn: () => submissionsApi.detail(id),
    enabled: !Number.isNaN(id),
    refetchInterval: (q) => {
      const data = q.state.data;
      if (!data) return false;
      return isPending(data) ? 1000 : false;
    },
  });

  const visibility = useMutation({
    mutationFn: (isPublic: boolean) =>
      submissionsApi.updateVisibility(id, { isPublic }),
    onSuccess: (updated) => {
      toast.success(updated.isPublic ? "공개로 변경" : "비공개로 변경");
      qc.invalidateQueries({ queryKey: ["submission", id] });
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("변경 실패");
    },
  });

  const rejudge = useMutation({
    mutationFn: () => submissionsApi.rejudge(id),
    onSuccess: () => {
      toast.info("재채점 큐에 등록 — 채점 중...");
      qc.invalidateQueries({ queryKey: ["submission", id] });
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("재채점 실패");
    },
  });

  if (sub.isLoading) {
    return (
      <main className="max-w-5xl mx-auto p-6 text-muted-foreground">
        불러오는 중...
      </main>
    );
  }
  if (sub.isError || !sub.data) {
    return (
      <main className="max-w-5xl mx-auto p-6 text-destructive">
        제출을 찾을 수 없습니다
      </main>
    );
  }

  const s = sub.data;
  const isOwner = user?.username === s.username;
  const isAdmin = user?.role === "ADMIN";

  return (
    <main className="max-w-5xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">제출 #{s.id}</h1>
          <StatusBadge
            status={s.status}
            passed={s.passedTestCases}
            total={s.totalTestCases}
            score={s.score}
            maxScore={s.maxScore}
          />
        </div>
        <div className="flex items-center gap-3">
          {isAdmin && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                if (confirm(`제출 #${s.id} 재채점할까요?`)) {
                  rejudge.mutate();
                }
              }}
              disabled={rejudge.isPending || isPending(s)}
            >
              {rejudge.isPending ? "요청 중..." : "재채점"}
            </Button>
          )}
          <Link
            href={`/problems/${s.problemId}`}
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            ← {s.problemTitle}
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 text-sm">
        <Card>
          <CardContent className="p-4">
            <div className="text-muted-foreground">언어</div>
            <div className="font-medium">{s.language}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-muted-foreground">제출자</div>
            <div className="font-medium">{s.username}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-muted-foreground">실행 시간</div>
            <div className="font-medium">
              {s.runtime !== null ? `${s.runtime}ms` : "-"}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-muted-foreground">메모리</div>
            <div className="font-medium">
              {s.memory !== null ? `${s.memory}KB` : "-"}
            </div>
          </CardContent>
        </Card>
      </div>

      {s.maxScore != null && (
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-base">점수</CardTitle>
            <span className="text-lg font-semibold tabular-nums">
              {s.score ?? 0} / {s.maxScore}
            </span>
          </CardHeader>
          {s.subtaskResults.length > 0 && (
            <CardContent>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-muted-foreground">
                    <th className="py-1 font-medium">서브태스크</th>
                    <th className="py-1 font-medium">결과</th>
                    <th className="py-1 text-right font-medium">점수</th>
                  </tr>
                </thead>
                <tbody>
                  {s.subtaskResults.map((st, i) => (
                    <tr key={i} className="border-t">
                      <td className="py-1.5">{st.label}</td>
                      <td className="py-1.5">
                        <span
                          className={
                            st.passed ? "text-green-500" : "text-red-500"
                          }
                        >
                          {st.passed ? "통과" : "실패"}
                        </span>
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {st.earned} / {st.points}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          )}
        </Card>
      )}

      {isOwner && (
        <Card>
          <CardContent className="p-4 flex items-center justify-between">
            <div>
              <div className="font-medium">풀이 공개</div>
              <div className="text-sm text-muted-foreground">
                공개 시 같은 문제를 푼 다른 사용자가 이 제출의 코드를 볼 수
                있습니다
              </div>
            </div>
            <Button
              variant={s.isPublic ? "outline" : "default"}
              size="sm"
              onClick={() => visibility.mutate(!s.isPublic)}
              disabled={visibility.isPending}
            >
              {s.isPublic ? "비공개로 전환" : "공개로 전환"}
            </Button>
          </CardContent>
        </Card>
      )}

      {s.errorMessage && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base text-destructive">
              에러 메시지
            </CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="text-sm bg-muted p-3 rounded whitespace-pre-wrap overflow-auto max-h-64">
              {s.errorMessage}
            </pre>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle className="text-base">소스 코드</CardTitle>
          <Button
            variant="outline"
            size="sm"
            onClick={() =>
              downloadTextFile(
                `${s.problemId}_${sanitizeFilename(s.problemTitle)}.${LANGUAGE_EXTENSION[s.language]}`,
                s.sourceCode,
              )
            }
          >
            코드 다운로드
          </Button>
        </CardHeader>
        <CardContent>
          <CodeEditor
            language={s.language}
            value={s.sourceCode}
            readOnly
            height="500px"
          />
        </CardContent>
      </Card>
    </main>
  );
}
