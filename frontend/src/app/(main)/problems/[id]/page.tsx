"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Copy } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { problemsApi } from "@/lib/problems-api";
import { submissionsApi } from "@/lib/submissions-api";
import { ApiError } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CodeEditor } from "@/components/code-editor";
import { Markdown } from "@/components/markdown";
import { DifficultyBadge } from "@/components/status-badge";
import type { Language } from "@/types/api";

const LANGUAGES: { value: Language; label: string }[] = [
  { value: "JAVA", label: "Java" },
  { value: "PYTHON3", label: "Python 3" },
  { value: "CPP", label: "C++" },
  { value: "C", label: "C" },
  { value: "JAVASCRIPT", label: "JavaScript" },
];

const DEFAULT_LANGUAGE: Language = "JAVA";

const STARTER: Record<Language, string> = {
  PYTHON3: "import sys\ninput = sys.stdin.readline\n\n",
  JAVA:
    "import java.util.*;\n\npublic class Main {\n  public static void main(String[] args) {\n    \n  }\n}\n",
  CPP:
    "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n  \n  return 0;\n}\n",
  C:
    "#include <stdio.h>\n\nint main() {\n  \n  return 0;\n}\n",
  JAVASCRIPT: "const input = require('fs').readFileSync(0, 'utf8').trim();\n\n",
};

export default function ProblemDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();
  const isAdmin = user?.role === "ADMIN";

  const [language, setLanguage] = useState<Language>(DEFAULT_LANGUAGE);
  const [code, setCode] = useState<string>(STARTER[DEFAULT_LANGUAGE]);

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${label} 복사됨`);
    } catch {
      toast.error("복사 실패");
    }
  };

  const problem = useQuery({
    queryKey: ["problem", id],
    queryFn: () => problemsApi.detail(id),
    enabled: !Number.isNaN(id),
  });

  const solutions = useQuery({
    queryKey: ["solutions", id],
    queryFn: () => submissionsApi.solutions(id),
    enabled: !Number.isNaN(id),
    retry: false,
  });

  const submit = useMutation({
    mutationFn: submissionsApi.submit,
    onSuccess: (s) => {
      toast.info("제출 완료, 채점 중...");
      router.push(`/submissions/${s.id}`);
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("제출 실패");
    },
  });

  const remove = useMutation({
    mutationFn: () => problemsApi.delete(id),
    onSuccess: () => {
      toast.success("문제 삭제 완료");
      qc.invalidateQueries({ queryKey: ["problems"] });
      router.push("/problems");
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("삭제 실패");
    },
  });

  if (problem.isLoading) {
    return (
      <main className="max-w-6xl mx-auto p-6 text-muted-foreground">
        불러오는 중...
      </main>
    );
  }
  if (problem.isError || !problem.data) {
    return (
      <main className="max-w-6xl mx-auto p-6 text-destructive">
        문제를 찾을 수 없습니다
      </main>
    );
  }

  const p = problem.data;
  const solutionsLocked =
    solutions.isError &&
    solutions.error instanceof ApiError &&
    solutions.error.code === "S003";

  return (
    <main className="max-w-7xl mx-auto p-6 space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="space-y-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-semibold">
                #{p.id} {p.title}
              </h1>
              <DifficultyBadge difficulty={p.difficulty} />
            </div>
            {isAdmin && (
              <div className="flex gap-3 items-center">
                <Link
                  href={`/admin/problems/${p.id}/edit`}
                  className="text-sm text-muted-foreground hover:text-foreground underline"
                >
                  수정
                </Link>
                <Link
                  href={`/admin/problems/${p.id}/test-cases`}
                  className="text-sm text-muted-foreground hover:text-foreground underline"
                >
                  TC 관리
                </Link>
                <button
                  className="text-sm text-destructive hover:underline"
                  onClick={() => {
                    if (confirm(`#${p.id} ${p.title} 문제를 삭제할까요?`)) {
                      remove.mutate();
                    }
                  }}
                  disabled={remove.isPending}
                >
                  삭제
                </button>
              </div>
            )}
          </div>
          <div className="text-sm text-muted-foreground">
            시간 제한 {(p.timeLimit / 1000).toLocaleString("ko-KR", { maximumFractionDigits: 3 })}초 · 메모리 {Math.round(p.memoryLimit / 1024).toLocaleString("ko-KR")}MB
            {p.authorUsername && ` · 출제자 ${p.authorUsername}`}
            {!p.isPublic && " · 비공개"}
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">문제 설명</CardTitle>
            </CardHeader>
            <CardContent>
              <Markdown>{p.description}</Markdown>
            </CardContent>
          </Card>

          {p.inputDescription && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">입력</CardTitle>
              </CardHeader>
              <CardContent>
                <Markdown>{p.inputDescription}</Markdown>
              </CardContent>
            </Card>
          )}

          {p.outputDescription && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">출력</CardTitle>
              </CardHeader>
              <CardContent>
                <Markdown>{p.outputDescription}</Markdown>
              </CardContent>
            </Card>
          )}

          {p.sampleTestCases.map((tc, i) => (
            <div key={tc.id} className="grid grid-cols-2 gap-3">
              <Card>
                <CardHeader className="flex-row items-center justify-between space-y-0">
                  <CardTitle className="text-sm">입력 예시 {i + 1}</CardTitle>
                  <button
                    type="button"
                    onClick={() => copyToClipboard(tc.input, `입력 예시 ${i + 1}`)}
                    className="text-muted-foreground hover:text-foreground p-1 -m-1"
                    aria-label={`입력 예시 ${i + 1} 복사`}
                  >
                    <Copy className="size-4" />
                  </button>
                </CardHeader>
                <CardContent>
                  <pre className="text-sm bg-muted p-3 rounded whitespace-pre-wrap">
                    {tc.input}
                  </pre>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex-row items-center justify-between space-y-0">
                  <CardTitle className="text-sm">출력 예시 {i + 1}</CardTitle>
                  <button
                    type="button"
                    onClick={() =>
                      copyToClipboard(tc.expectedOutput, `출력 예시 ${i + 1}`)
                    }
                    className="text-muted-foreground hover:text-foreground p-1 -m-1"
                    aria-label={`출력 예시 ${i + 1} 복사`}
                  >
                    <Copy className="size-4" />
                  </button>
                </CardHeader>
                <CardContent>
                  <pre className="text-sm bg-muted p-3 rounded whitespace-pre-wrap">
                    {tc.expectedOutput}
                  </pre>
                </CardContent>
              </Card>
            </div>
          ))}
        </section>

        <section className="space-y-3 lg:sticky lg:top-4 lg:self-start">
          <div className="flex items-center justify-between gap-3">
            <select
              value={language}
              onChange={(e) => {
                const lang = e.target.value as Language;
                setLanguage(lang);
                setCode(STARTER[lang]);
              }}
              className="h-9 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              {LANGUAGES.map((l) => (
                <option key={l.value} value={l.value}>
                  {l.label}
                </option>
              ))}
            </select>
            <Button
              onClick={() =>
                submit.mutate({ problemId: p.id, language, sourceCode: code })
              }
              disabled={submit.isPending || !code.trim()}
            >
              {submit.isPending ? "제출 중..." : "제출"}
            </Button>
          </div>
          <CodeEditor
            language={language}
            value={code}
            onChange={setCode}
            height="600px"
          />
        </section>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">다른 사람 풀이</h2>
        {solutionsLocked && (
          <Card>
            <CardContent className="p-6 text-center text-muted-foreground text-sm">
              이 문제를 정답 처리한 후에 다른 사람의 풀이를 볼 수 있습니다.
            </CardContent>
          </Card>
        )}
        {!solutionsLocked && solutions.isLoading && (
          <p className="text-muted-foreground text-sm">불러오는 중...</p>
        )}
        {!solutionsLocked && solutions.data && solutions.data.empty && (
          <Card>
            <CardContent className="p-6 text-center text-muted-foreground text-sm">
              아직 공개된 풀이가 없습니다
            </CardContent>
          </Card>
        )}
        {!solutionsLocked && solutions.data && !solutions.data.empty && (
          <Card className="overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr className="text-left">
                  <th className="p-3 w-32">사용자</th>
                  <th className="p-3 w-28">언어</th>
                  <th className="p-3 w-32">시간/메모리</th>
                  <th className="p-3 w-32 text-right">상세</th>
                </tr>
              </thead>
              <tbody>
                {solutions.data.content.map((s) => (
                  <tr key={s.id} className="border-t hover:bg-muted/40">
                    <td className="p-3">{s.username}</td>
                    <td className="p-3 text-xs text-muted-foreground">
                      {s.language}
                    </td>
                    <td className="p-3 text-xs text-muted-foreground">
                      {s.runtime !== null
                        ? `${s.runtime}ms / ${s.memory}KB`
                        : "-"}
                    </td>
                    <td className="p-3 text-right">
                      <Link
                        href={`/submissions/${s.id}`}
                        className="text-sm hover:underline"
                      >
                        코드 보기
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )}
      </section>
    </main>
  );
}
