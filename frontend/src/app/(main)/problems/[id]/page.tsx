"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { problemsApi } from "@/lib/problems-api";
import { submissionsApi } from "@/lib/submissions-api";
import { ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CodeEditor } from "@/components/code-editor";
import { DifficultyBadge } from "@/components/status-badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Language } from "@/types/api";

const LANGUAGES: { value: Language; label: string }[] = [
  { value: "PYTHON3", label: "Python 3" },
  { value: "JAVA", label: "Java" },
  { value: "CPP", label: "C++" },
  { value: "C", label: "C" },
  { value: "JAVASCRIPT", label: "JavaScript" },
];

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

  const [language, setLanguage] = useState<Language>("PYTHON3");
  const [code, setCode] = useState<string>(STARTER.PYTHON3);

  const problem = useQuery({
    queryKey: ["problem", id],
    queryFn: () => problemsApi.detail(id),
    enabled: !Number.isNaN(id),
  });

  const submit = useMutation({
    mutationFn: submissionsApi.submit,
    onSuccess: (s) => {
      toast.success(`제출 완료 — ${s.status}`);
      router.push(`/submissions/${s.id}`);
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("제출 실패");
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

  return (
    <main className="max-w-7xl mx-auto p-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="space-y-4">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">
              #{p.id} {p.title}
            </h1>
            <DifficultyBadge difficulty={p.difficulty} />
          </div>
          <div className="text-sm text-muted-foreground">
            시간 제한 {p.timeLimitMs}ms · 메모리 {p.memoryLimitKb}KB
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">문제 설명</CardTitle>
            </CardHeader>
            <CardContent className="prose prose-sm dark:prose-invert max-w-none whitespace-pre-wrap">
              {p.description}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">입력</CardTitle>
            </CardHeader>
            <CardContent className="whitespace-pre-wrap text-sm">
              {p.inputFormat}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">출력</CardTitle>
            </CardHeader>
            <CardContent className="whitespace-pre-wrap text-sm">
              {p.outputFormat}
            </CardContent>
          </Card>

          {p.sampleTestCases.map((tc, i) => (
            <div key={tc.id} className="grid grid-cols-2 gap-3">
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">입력 예시 {i + 1}</CardTitle>
                </CardHeader>
                <CardContent>
                  <pre className="text-sm bg-muted p-3 rounded whitespace-pre-wrap">
                    {tc.input}
                  </pre>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">출력 예시 {i + 1}</CardTitle>
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
            <Select
              value={language}
              onValueChange={(v) => {
                const lang = v as Language;
                setLanguage(lang);
                setCode(STARTER[lang]);
              }}
            >
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {LANGUAGES.map((l) => (
                  <SelectItem key={l.value} value={l.value}>
                    {l.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              onClick={() =>
                submit.mutate({ problemId: p.id, language, sourceCode: code })
              }
              disabled={submit.isPending || !code.trim()}
            >
              {submit.isPending ? "채점 중..." : "제출"}
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
    </main>
  );
}
