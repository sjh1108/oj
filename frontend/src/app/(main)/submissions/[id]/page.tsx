"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { toast } from "sonner";

import { submissionsApi } from "@/lib/submissions-api";
import { useAuthStore } from "@/lib/auth-store";
import { ApiError } from "@/lib/api";
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

  return (
    <main className="max-w-5xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">제출 #{s.id}</h1>
          <StatusBadge
            status={s.status}
            passed={s.passedTestCases}
            total={s.totalTestCases}
          />
        </div>
        <Link
          href={`/problems/${s.problemId}`}
          className="text-sm text-muted-foreground hover:text-foreground"
        >
          ← {s.problemTitle}
        </Link>
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
        <CardHeader>
          <CardTitle className="text-base">소스 코드</CardTitle>
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
