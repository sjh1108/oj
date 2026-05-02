"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";

import { submissionsApi } from "@/lib/submissions-api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CodeEditor } from "@/components/code-editor";
import { StatusBadge } from "@/components/status-badge";

export default function SubmissionDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);

  const sub = useQuery({
    queryKey: ["submission", id],
    queryFn: () => submissionsApi.detail(id),
    enabled: !Number.isNaN(id),
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

  return (
    <main className="max-w-5xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">제출 #{s.id}</h1>
          <StatusBadge status={s.status} />
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
              {s.runtimeMs !== null ? `${s.runtimeMs}ms` : "-"}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-muted-foreground">메모리</div>
            <div className="font-medium">
              {s.memoryKb !== null ? `${s.memoryKb}KB` : "-"}
            </div>
          </CardContent>
        </Card>
      </div>

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
