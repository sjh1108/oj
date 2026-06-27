"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Copy, Download, Play, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { problemsApi } from "@/lib/problems-api";
import { runApi } from "@/lib/run-api";
import { submissionsApi } from "@/lib/submissions-api";
import { ApiError } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { downloadTextFile, sanitizeFilename } from "@/lib/download";
import { buildProblemMarkdown } from "@/lib/problem-file";
import { CodeEditor } from "@/components/code-editor";
import { Markdown } from "@/components/markdown";
import { DifficultyBadge } from "@/components/status-badge";
import type { Language, RunResponse } from "@/types/api";

type CustomCase = {
  id: string;
  stdin: string;
  expectedOutput: string;
  isRunning: boolean;
  result: RunResponse | null;
};

const RUN_STATUS_LABEL: Record<RunResponse["status"], string> = {
  OK: "정상 종료",
  TIME_LIMIT: "시간 초과",
  RUNTIME_ERROR: "런타임 에러",
  COMPILE_ERROR: "컴파일 에러",
  SYSTEM_ERROR: "시스템 오류",
};

// Judge0 채점과 동일한 정규화: CRLF→LF, 각 줄 trailing 공백 제거, 끝 공백/개행 제거
function normalizeOutput(s: string | null | undefined): string {
  if (!s) return "";
  return s
    .split(/\r?\n/)
    .map((line) => line.replace(/\s+$/, ""))
    .join("\n")
    .replace(/\s+$/, "");
}

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

  // Per-problem, per-language draft persisted in localStorage so in-progress code
  // survives session drops, refreshes, or accidental navigation.
  const draftKey = (lang: Language) => `algoj-draft:${id}:${lang}`;

  const readDraft = (lang: Language): string | null => {
    if (typeof window === "undefined" || !Number.isFinite(id)) return null;
    return window.localStorage.getItem(draftKey(lang));
  };

  const writeDraft = (lang: Language, value: string) => {
    if (typeof window === "undefined" || !Number.isFinite(id)) return;
    // Don't persist the pristine starter template — keep "no draft" meaning no draft.
    if (value === STARTER[lang]) window.localStorage.removeItem(draftKey(lang));
    else window.localStorage.setItem(draftKey(lang), value);
  };

  // Restore the saved draft for the current language when the problem changes.
  useEffect(() => {
    setCode(readDraft(language) ?? STARTER[language]);
    // Intentionally keyed on `id` only; language switches are handled inline below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleCodeChange = (value: string) => {
    setCode(value);
    writeDraft(language, value);
  };

  const handleLanguageChange = (lang: Language) => {
    setLanguage(lang);
    setCode(readDraft(lang) ?? STARTER[lang]);
  };

  const copyToClipboard = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${label} 복사됨`);
    } catch {
      toast.error("복사 실패");
    }
  };

  const [sampleResults, setSampleResults] = useState<
    Record<number, RunResponse | null>
  >({});
  const [sampleRunning, setSampleRunning] = useState<Record<number, boolean>>(
    {},
  );
  const [customCases, setCustomCases] = useState<CustomCase[]>([]);

  const runOneSample = async (tcId: number, stdin: string) => {
    setSampleRunning((m) => ({ ...m, [tcId]: true }));
    try {
      const res = await runApi.run({
        problemId: id,
        language,
        sourceCode: code,
        stdin,
      });
      setSampleResults((m) => ({ ...m, [tcId]: res }));
    } catch (err) {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("실행 실패");
    } finally {
      setSampleRunning((m) => ({ ...m, [tcId]: false }));
    }
  };

  const addCustomCase = () => {
    setCustomCases((cs) => [
      ...cs,
      {
        id:
          typeof crypto !== "undefined" && crypto.randomUUID
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random()}`,
        stdin: "",
        expectedOutput: "",
        isRunning: false,
        result: null,
      },
    ]);
  };

  const removeCustomCase = (cid: string) => {
    setCustomCases((cs) => cs.filter((c) => c.id !== cid));
  };

  const updateCustomStdin = (cid: string, value: string) => {
    setCustomCases((cs) =>
      cs.map((c) => (c.id === cid ? { ...c, stdin: value } : c)),
    );
  };

  const updateCustomExpected = (cid: string, value: string) => {
    setCustomCases((cs) =>
      cs.map((c) => (c.id === cid ? { ...c, expectedOutput: value } : c)),
    );
  };

  const runCustomCase = async (cid: string) => {
    const target = customCases.find((c) => c.id === cid);
    if (!target) return;
    setCustomCases((cs) =>
      cs.map((c) => (c.id === cid ? { ...c, isRunning: true } : c)),
    );
    try {
      const res = await runApi.run({
        problemId: id,
        language,
        sourceCode: code,
        stdin: target.stdin,
      });
      setCustomCases((cs) =>
        cs.map((c) =>
          c.id === cid ? { ...c, result: res, isRunning: false } : c,
        ),
      );
    } catch (err) {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("실행 실패");
      setCustomCases((cs) =>
        cs.map((c) => (c.id === cid ? { ...c, isRunning: false } : c)),
      );
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
            <div className="flex gap-3 items-center">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  downloadTextFile(
                    `${p.id}_${sanitizeFilename(p.title)}.md`,
                    buildProblemMarkdown(p),
                    "text/markdown;charset=utf-8",
                  )
                }
              >
                <Download className="size-4 mr-1" />
                문제 다운로드
              </Button>
              {isAdmin && (
                <>
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
                </>
              )}
            </div>
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

          {p.sampleTestCases.length > 0 && (
            <div className="flex items-center justify-between gap-3 pt-2">
              <h2 className="text-sm font-semibold">예제 테스트케이스</h2>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  Promise.all(
                    p.sampleTestCases.map((tc) =>
                      runOneSample(tc.id, tc.input),
                    ),
                  )
                }
                disabled={
                  !code.trim() ||
                  Object.values(sampleRunning).some(Boolean)
                }
              >
                <Play className="size-4 mr-1" />
                {Object.values(sampleRunning).some(Boolean)
                  ? "실행 중..."
                  : "예제 실행"}
              </Button>
            </div>
          )}

          {p.sampleTestCases.map((tc, i) => {
            const result = sampleResults[tc.id];
            const running = sampleRunning[tc.id];
            const normalizedActual = normalizeOutput(result?.stdout);
            const normalizedExpected = normalizeOutput(tc.expectedOutput);
            const passed =
              result?.status === "OK" &&
              normalizedActual === normalizedExpected;
            return (
              <div key={tc.id} className="space-y-2">
                <div className="grid grid-cols-2 gap-3 items-stretch">
                  <Card className="h-full">
                    <CardHeader>
                      <CardTitle className="text-sm">
                        입력 예시 {i + 1}
                      </CardTitle>
                      <CardAction>
                        <button
                          type="button"
                          onClick={() =>
                            copyToClipboard(tc.input, `입력 예시 ${i + 1}`)
                          }
                          className="text-muted-foreground hover:text-foreground p-1 -m-1"
                          aria-label={`입력 예시 ${i + 1} 복사`}
                        >
                          <Copy className="size-4" />
                        </button>
                      </CardAction>
                    </CardHeader>
                    <CardContent className="flex-1">
                      <pre className="text-sm bg-muted p-3 rounded whitespace-pre-wrap h-full">
                        {tc.input}
                      </pre>
                    </CardContent>
                  </Card>
                  <Card className="h-full">
                    <CardHeader>
                      <CardTitle className="text-sm">
                        출력 예시 {i + 1}
                      </CardTitle>
                      <CardAction>
                        <button
                          type="button"
                          onClick={() =>
                            copyToClipboard(
                              tc.expectedOutput,
                              `출력 예시 ${i + 1}`,
                            )
                          }
                          className="text-muted-foreground hover:text-foreground p-1 -m-1"
                          aria-label={`출력 예시 ${i + 1} 복사`}
                        >
                          <Copy className="size-4" />
                        </button>
                      </CardAction>
                    </CardHeader>
                    <CardContent className="flex-1">
                      <pre className="text-sm bg-muted p-3 rounded whitespace-pre-wrap h-full">
                        {tc.expectedOutput}
                      </pre>
                    </CardContent>
                  </Card>
                </div>
                {running && (
                  <div className="text-xs text-muted-foreground rounded border px-3 py-2">
                    실행 중...
                  </div>
                )}
                {!running && result && (
                  <div
                    className={`rounded border px-3 py-2 text-sm space-y-1 ${
                      passed
                        ? "border-emerald-500/40 bg-emerald-500/5"
                        : "border-destructive/40 bg-destructive/5"
                    }`}
                  >
                    <div className="flex items-center gap-2 text-xs">
                      <span
                        className={`font-semibold ${
                          passed ? "text-emerald-600" : "text-destructive"
                        }`}
                      >
                        {passed ? "✓ PASS" : "✗ FAIL"}
                      </span>
                      <span className="text-muted-foreground">
                        {RUN_STATUS_LABEL[result.status]}
                        {result.runtimeMs !== null &&
                          ` · ${result.runtimeMs}ms`}
                        {result.memoryKb !== null &&
                          ` · ${result.memoryKb}KB`}
                      </span>
                    </div>
                    {result.status === "OK" && (
                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <div className="text-xs text-muted-foreground">
                            실제 출력
                          </div>
                          <pre className="text-sm bg-muted p-2 rounded whitespace-pre-wrap mt-1">
                            {result.stdout ?? ""}
                          </pre>
                        </div>
                        {!passed && (
                          <div>
                            <div className="text-xs text-muted-foreground">
                              예상 출력
                            </div>
                            <pre className="text-sm bg-muted p-2 rounded whitespace-pre-wrap mt-1">
                              {tc.expectedOutput}
                            </pre>
                          </div>
                        )}
                      </div>
                    )}
                    {result.errorMessage && (
                      <div>
                        <div className="text-xs text-muted-foreground">
                          {result.status === "COMPILE_ERROR"
                            ? "컴파일 메시지"
                            : "에러 메시지"}
                        </div>
                        <pre className="text-xs bg-muted p-2 rounded whitespace-pre-wrap mt-1 text-destructive">
                          {result.errorMessage}
                        </pre>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}

          <div className="flex items-center justify-between gap-3 pt-4">
            <h2 className="text-sm font-semibold">사용자 테스트케이스</h2>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addCustomCase}
            >
              <Plus className="size-4 mr-1" />
              케이스 추가
            </Button>
          </div>
          {customCases.length === 0 && (
            <p className="text-xs text-muted-foreground">
              내가 만든 입력값으로 코드를 돌려볼 수 있습니다. DB에 저장되지 않아요.
            </p>
          )}
          {customCases.map((c, i) => {
            const hasExpected = c.expectedOutput.trim().length > 0;
            const passed =
              hasExpected &&
              c.result?.status === "OK" &&
              normalizeOutput(c.result.stdout) ===
                normalizeOutput(c.expectedOutput);
            return (
              <Card key={c.id}>
                <CardHeader>
                  <CardTitle className="text-sm">
                    사용자 케이스 {i + 1}
                  </CardTitle>
                  <CardAction>
                    <button
                      type="button"
                      onClick={() => removeCustomCase(c.id)}
                      className="text-muted-foreground hover:text-destructive p-1 -m-1"
                      aria-label={`사용자 케이스 ${i + 1} 삭제`}
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </CardAction>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">
                        입력 (stdin)
                      </div>
                      <textarea
                        value={c.stdin}
                        onChange={(e) =>
                          updateCustomStdin(c.id, e.target.value)
                        }
                        rows={5}
                        placeholder="여기에 stdin을 입력하세요"
                        className="w-full font-mono text-sm rounded-lg border border-input bg-transparent px-2.5 py-2 outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                      />
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">
                        예상 출력
                      </div>
                      <textarea
                        value={c.expectedOutput}
                        onChange={(e) =>
                          updateCustomExpected(c.id, e.target.value)
                        }
                        rows={5}
                        placeholder="입력하면 예제처럼 PASS/FAIL 비교"
                        className="w-full font-mono text-sm rounded-lg border border-input bg-transparent px-2.5 py-2 outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                      />
                    </div>
                  </div>
                  <div className="flex justify-end">
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => runCustomCase(c.id)}
                      disabled={c.isRunning || !code.trim()}
                    >
                      <Play className="size-4 mr-1" />
                      {c.isRunning ? "실행 중..." : "실행"}
                    </Button>
                  </div>
                  {c.result && (
                    <div
                      className={`rounded border px-3 py-2 text-sm space-y-1 ${
                        hasExpected && c.result.status === "OK"
                          ? passed
                            ? "border-emerald-500/40 bg-emerald-500/5"
                            : "border-destructive/40 bg-destructive/5"
                          : "bg-muted/30"
                      }`}
                    >
                      <div className="flex items-center gap-2 text-xs">
                        {hasExpected && c.result.status === "OK" && (
                          <span
                            className={`font-semibold ${
                              passed ? "text-emerald-600" : "text-destructive"
                            }`}
                          >
                            {passed ? "✓ PASS" : "✗ FAIL"}
                          </span>
                        )}
                        <span className="text-muted-foreground">
                          {RUN_STATUS_LABEL[c.result.status]}
                          {c.result.runtimeMs !== null &&
                            ` · ${c.result.runtimeMs}ms`}
                          {c.result.memoryKb !== null &&
                            ` · ${c.result.memoryKb}KB`}
                        </span>
                      </div>
                      {c.result.status === "OK" && (
                        <div className="grid grid-cols-2 gap-2">
                          <div>
                            <div className="text-xs text-muted-foreground">
                              실제 출력
                            </div>
                            <pre className="text-sm bg-muted p-2 rounded whitespace-pre-wrap mt-1">
                              {c.result.stdout ?? ""}
                            </pre>
                          </div>
                          {hasExpected && !passed && (
                            <div>
                              <div className="text-xs text-muted-foreground">
                                예상 출력
                              </div>
                              <pre className="text-sm bg-muted p-2 rounded whitespace-pre-wrap mt-1">
                                {c.expectedOutput}
                              </pre>
                            </div>
                          )}
                        </div>
                      )}
                      {c.result.errorMessage && (
                        <div>
                          <div className="text-xs text-muted-foreground">
                            {c.result.status === "COMPILE_ERROR"
                              ? "컴파일 메시지"
                              : "에러 메시지"}
                          </div>
                          <pre className="text-xs bg-muted p-2 rounded whitespace-pre-wrap mt-1 text-destructive">
                            {c.result.errorMessage}
                          </pre>
                        </div>
                      )}
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </section>

        <section className="space-y-3 lg:sticky lg:top-4 lg:self-start">
          <div className="flex items-center justify-between gap-3">
            <select
              value={language}
              onChange={(e) => handleLanguageChange(e.target.value as Language)}
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
            onChange={handleCodeChange}
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
