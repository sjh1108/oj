"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { problemsApi } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { downloadTextFile } from "@/lib/download";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CodeEditor } from "@/components/code-editor";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type {
  GenerateTestCaseRequest,
  GenerateTestCaseResponse,
  Language,
  TestCaseMetaRequest,
  TestCaseRequest,
  TestCaseSummaryResponse,
} from "@/types/api";

const LANGUAGES: { value: Language; label: string }[] = [
  { value: "PYTHON3", label: "Python 3" },
  { value: "PYPY3", label: "PyPy 3" },
  { value: "CPP", label: "C++" },
  { value: "JAVA", label: "Java" },
  { value: "C", label: "C" },
  { value: "JAVASCRIPT", label: "JavaScript" },
];

const numberFormat = new Intl.NumberFormat("ko");
const chars = (n: number) => `${numberFormat.format(n)}자`;

// Opening a case this big in a textarea is what drags the browser down, so it
// only happens on an explicit click (with a confirm past this size).
const HEAVY_CHARS = 200_000;
// A save carries the whole case in one request; nginx caps bodies at 1MB.
const MAX_SAVE_CHARS = 700_000;

interface DraftTC {
  id?: number;
  orderIndex: number;
  isSample: boolean;
  isDraft: boolean;
  // Real sizes on the server — the list endpoint never sends the data itself.
  inputLength: number;
  expectedOutputLength: number;
  inputPreview: string;
  expectedOutputPreview: string;
  // Full data, filled in once the case is opened (or straight away when the
  // preview already covers the whole thing).
  input: string;
  expectedOutput: string;
  loaded: boolean;
  // Data edited — needs a full save. Only ever set on an opened case.
  dirty: boolean;
  // Order/sample edited — saved through the flags-only endpoint, so a case that
  // was never opened can be reordered without its data being touched.
  metaDirty: boolean;
}

const isTruncated = (d: DraftTC) =>
  d.inputLength > d.inputPreview.length ||
  d.expectedOutputLength > d.expectedOutputPreview.length;

const fromServer = (s: TestCaseSummaryResponse): DraftTC => {
  const whole =
    s.inputLength <= s.inputPreview.length &&
    s.expectedOutputLength <= s.expectedOutputPreview.length;
  return {
    id: s.id,
    orderIndex: s.orderIndex,
    isSample: s.isSample,
    isDraft: s.isDraft,
    inputLength: s.inputLength,
    expectedOutputLength: s.expectedOutputLength,
    inputPreview: s.inputPreview,
    expectedOutputPreview: s.expectedOutputPreview,
    input: whole ? s.inputPreview : "",
    expectedOutput: whole ? s.expectedOutputPreview : "",
    loaded: whole,
    dirty: false,
    metaDirty: false,
  };
};

const emptyDraft = (orderIndex: number): DraftTC => ({
  orderIndex,
  isSample: false,
  isDraft: false,
  inputLength: 0,
  expectedOutputLength: 0,
  inputPreview: "",
  expectedOutputPreview: "",
  input: "",
  expectedOutput: "",
  loaded: true,
  dirty: true,
  metaDirty: false,
});

export default function TestCaseManagementPage() {
  const params = useParams<{ id: string }>();
  const problemId = Number(params.id);
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();

  const [drafts, setDrafts] = useState<DraftTC[]>([]);
  // Case currently being pulled in full (id), for the button's spinner state.
  const [openingId, setOpeningId] = useState<number | null>(null);

  // ── Generator panel state ──────────────────────────────────
  const [genLang, setGenLang] = useState<Language>("PYTHON3");
  const [genCode, setGenCode] = useState("");
  const [genStdin, setGenStdin] = useState("");
  const [solLang, setSolLang] = useState<Language>("PYTHON3");
  const [solCode, setSolCode] = useState("");
  const [valLang, setValLang] = useState<Language>("PYTHON3");
  const [valCode, setValCode] = useState("");
  const [genOrderIndex, setGenOrderIndex] = useState(0);
  const [genIsSample, setGenIsSample] = useState(false);
  const [genResult, setGenResult] = useState<GenerateTestCaseResponse | null>(
    null,
  );

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  const problem = useQuery({
    queryKey: ["problem", problemId],
    queryFn: () => problemsApi.detail(problemId),
    enabled: !Number.isNaN(problemId),
  });

  const tcs = useQuery({
    queryKey: ["test-cases", problemId],
    queryFn: () => problemsApi.listTestCases(problemId),
    enabled: !Number.isNaN(problemId) && user?.role === "ADMIN",
  });

  // A refetch (after save/generate/delete) must not throw away data the admin
  // already opened, nor unsaved edits — merge the new sizes onto what's here.
  useEffect(() => {
    if (!tcs.data) return;
    const fresh = tcs.data;
    setDrafts((prev) => {
      const byId = new Map(
        prev.filter((d) => d.id !== undefined).map((d) => [d.id!, d]),
      );
      const merged = fresh.map((summary) => {
        const next = fromServer(summary);
        const old = byId.get(summary.id);
        if (!old) return next;
        if (old.dirty || old.metaDirty) return old;
        const sameSize =
          old.inputLength === next.inputLength &&
          old.expectedOutputLength === next.expectedOutputLength;
        if (old.loaded && !next.loaded && sameSize) {
          return {
            ...next,
            input: old.input,
            expectedOutput: old.expectedOutput,
            loaded: true,
          };
        }
        return next;
      });
      // Locally added rows that were never saved stay at the end.
      return [...merged, ...prev.filter((d) => d.id === undefined)];
    });
  }, [tcs.data]);

  const handleApiError = (err: unknown, fallback: string) => {
    if (err instanceof ApiError) toast.error(err.message);
    else toast.error(fallback);
  };

  const addMutation = useMutation({
    mutationFn: (body: TestCaseRequest) =>
      problemsApi.addTestCase(problemId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["test-cases", problemId] });
      qc.invalidateQueries({ queryKey: ["problem", problemId] });
    },
    onError: (err) => handleApiError(err, "추가 실패"),
  });

  const updateMutation = useMutation({
    mutationFn: ({ tcId, body }: { tcId: number; body: TestCaseRequest }) =>
      problemsApi.updateTestCase(problemId, tcId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["test-cases", problemId] });
      qc.invalidateQueries({ queryKey: ["problem", problemId] });
    },
    onError: (err) => handleApiError(err, "수정 실패"),
  });

  const metaMutation = useMutation({
    mutationFn: ({ tcId, body }: { tcId: number; body: TestCaseMetaRequest }) =>
      problemsApi.updateTestCaseMeta(problemId, tcId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["test-cases", problemId] });
      qc.invalidateQueries({ queryKey: ["problem", problemId] });
    },
    onError: (err) => handleApiError(err, "순서/샘플 저장 실패"),
  });

  const deleteMutation = useMutation({
    mutationFn: (tcId: number) => problemsApi.deleteTestCase(problemId, tcId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["test-cases", problemId] });
      qc.invalidateQueries({ queryKey: ["problem", problemId] });
    },
    onError: (err) => handleApiError(err, "삭제 실패"),
  });

  const rejudgeMutation = useMutation({
    mutationFn: () => problemsApi.rejudge(problemId),
    onSuccess: (res) => {
      toast.success(`재채점 큐 등록 — ${res.queued}건`);
    },
    onError: (err) => handleApiError(err, "재채점 실패"),
  });

  const generateMutation = useMutation({
    mutationFn: (body: GenerateTestCaseRequest) =>
      problemsApi.generateTestCase(problemId, body),
    onSuccess: (res) => {
      setGenResult(res);
      setGenOrderIndex((n) => n + 1);
      qc.invalidateQueries({ queryKey: ["test-cases", problemId] });
      qc.invalidateQueries({ queryKey: ["problem", problemId] });
      toast.success(
        `생성 완료 — 입력 ${res.inputSize}자 / 출력 ${res.outputSize}자`,
      );
    },
    onError: (err) => handleApiError(err, "생성 실패"),
  });

  const handleGenerate = () => {
    if (!genCode.trim() || !solCode.trim()) {
      toast.error("생성기 코드와 모범답안 코드를 모두 입력하세요");
      return;
    }
    generateMutation.mutate({
      generatorLanguage: genLang,
      generatorCode: genCode,
      generatorStdin: genStdin || undefined,
      solutionLanguage: solLang,
      solutionCode: solCode,
      validatorLanguage: valCode.trim() ? valLang : undefined,
      validatorCode: valCode.trim() ? valCode : undefined,
      orderIndex: genOrderIndex,
      isSample: genIsSample,
    });
  };

  // Data edits — only reachable on an opened case.
  const setData = (idx: number, key: "input" | "expectedOutput", value: string) => {
    setDrafts((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], [key]: value, dirty: true };
      return next;
    });
  };

  // Flag edits — allowed on unopened cases too; saved without their data.
  const setMeta = <K extends "orderIndex" | "isSample">(
    idx: number,
    key: K,
    value: DraftTC[K],
  ) => {
    setDrafts((prev) => {
      const next = [...prev];
      const d = next[idx];
      // A brand-new row is saved whole anyway, so keep it on the data path.
      next[idx] = { ...d, [key]: value, metaDirty: d.id !== undefined };
      if (d.id === undefined) next[idx].dirty = true;
      return next;
    });
  };

  const addLocal = () => {
    setDrafts((prev) => [...prev, emptyDraft(prev.length)]);
  };

  /** Pulls one case's full data down — the only thing that renders a big TC. */
  const openFull = async (idx: number) => {
    const d = drafts[idx];
    if (d.id === undefined || d.loaded) return;
    const total = d.inputLength + d.expectedOutputLength;
    if (
      total > HEAVY_CHARS &&
      !confirm(
        `이 케이스는 ${chars(total)}입니다. 편집기에 펼치면 브라우저가 느려질 수 있습니다.\n` +
          `내용만 확인하려면 '.txt로 저장'을 쓰세요. 계속 펼칠까요?`,
      )
    ) {
      return;
    }
    setOpeningId(d.id);
    try {
      const full = await problemsApi.getTestCase(problemId, d.id);
      setDrafts((prev) =>
        prev.map((x) =>
          x.id === full.id
            ? {
                ...x,
                input: full.input,
                expectedOutput: full.expectedOutput,
                loaded: true,
              }
            : x,
        ),
      );
    } catch (err) {
      handleApiError(err, "불러오기 실패");
    } finally {
      setOpeningId(null);
    }
  };

  /** Drops the opened data again so the page stops carrying it. */
  const collapse = (idx: number) => {
    setDrafts((prev) =>
      prev.map((x, i) =>
        i === idx && !x.dirty
          ? { ...x, input: "", expectedOutput: "", loaded: false }
          : x,
      ),
    );
  };

  // Saves the data to a file without ever putting it in the DOM.
  const downloadPart = async (idx: number, part: "input" | "output") => {
    const d = drafts[idx];
    if (d.id === undefined) return;
    setOpeningId(d.id);
    try {
      const full = d.loaded ? d : await problemsApi.getTestCase(problemId, d.id);
      downloadTextFile(
        `problem-${problemId}-tc${idx + 1}-${part === "input" ? "in" : "out"}.txt`,
        part === "input" ? full.input : full.expectedOutput,
      );
    } catch (err) {
      handleApiError(err, "다운로드 실패");
    } finally {
      setOpeningId(null);
    }
  };

  const removeLocal = (idx: number) => {
    setDrafts((prev) => prev.filter((_, i) => i !== idx));
  };

  const saveOne = async (draft: DraftTC) => {
    // Flags-only change on an existing case: never send the data back.
    if (draft.id !== undefined && !draft.dirty) {
      await metaMutation.mutateAsync({
        tcId: draft.id,
        body: { orderIndex: draft.orderIndex, isSample: draft.isSample },
      });
      return;
    }
    if (draft.input.length + draft.expectedOutput.length > MAX_SAVE_CHARS) {
      throw new Error(
        `케이스가 ${chars(MAX_SAVE_CHARS)}를 넘어 한 번에 저장할 수 없습니다 — 생성기로 다시 만들어 주세요`,
      );
    }
    const body: TestCaseRequest = {
      input: draft.input,
      expectedOutput: draft.expectedOutput,
      orderIndex: draft.orderIndex,
      isSample: draft.isSample,
    };
    if (draft.id !== undefined) {
      await updateMutation.mutateAsync({ tcId: draft.id, body });
    } else {
      await addMutation.mutateAsync(body);
    }
  };

  const handleDelete = async (idx: number) => {
    const draft = drafts[idx];
    if (draft.id === undefined) {
      removeLocal(idx);
      return;
    }
    if (!confirm(`TC #${idx + 1} 삭제할까요? 운영 중인 제출의 채점 결과가 stale 됩니다.`)) {
      return;
    }
    await deleteMutation.mutateAsync(draft.id);
  };

  const handleSaveAll = async () => {
    const dirty = drafts.filter((d) => d.dirty || d.metaDirty);
    if (dirty.length === 0) {
      toast.info("변경사항이 없습니다");
      return;
    }
    try {
      for (const d of dirty) {
        await saveOne(d);
      }
      toast.success(`저장 완료 — ${dirty.length}건`);
    } catch (err) {
      // Mutation failures already showed a toast; size rejections did not.
      if (!(err instanceof ApiError) && err instanceof Error) {
        toast.error(err.message);
      }
    }
  };

  if (!user || user.role !== "ADMIN") return null;

  if (problem.isLoading || tcs.isLoading) {
    return (
      <main className="max-w-5xl mx-auto p-6 text-muted-foreground">
        불러오는 중...
      </main>
    );
  }

  if (problem.isError || !problem.data) {
    return (
      <main className="max-w-5xl mx-auto p-6 text-destructive">
        문제를 찾을 수 없습니다
      </main>
    );
  }

  const dirtyCount = drafts.filter((d) => d.dirty || d.metaDirty).length;
  const busy =
    addMutation.isPending ||
    updateMutation.isPending ||
    metaMutation.isPending ||
    deleteMutation.isPending;

  return (
    <main className="max-w-5xl mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-semibold">
            TC 관리 — #{problem.data.id} {problem.data.title}
          </h1>
          <p className="text-sm text-muted-foreground">
            테스트케이스 추가 / 수정 / 삭제. TC 변경 후 기존 제출은 재채점이 필요합니다.
          </p>
        </div>
        <Link
          href={`/problems/${problemId}`}
          className="text-sm text-muted-foreground hover:text-foreground"
        >
          ← 문제로 돌아가기
        </Link>
      </div>

      <Card>
        <CardContent className="p-4 flex items-center justify-between flex-wrap gap-3">
          <div>
            <div className="font-medium">전체 재채점</div>
            <div className="text-sm text-muted-foreground">
              이 문제의 모든 제출을 PENDING으로 초기화하고 다시 채점합니다.
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (confirm("이 문제의 전체 제출을 재채점할까요?")) {
                rejudgeMutation.mutate();
              }
            }}
            disabled={rejudgeMutation.isPending}
          >
            {rejudgeMutation.isPending ? "재채점 중..." : "전체 재채점"}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">생성기로 추가</CardTitle>
          <p className="text-sm text-muted-foreground">
            큰 입력은 직접 붙여넣지 말고, 입력을 stdout으로 출력하는 생성기 코드와
            모범답안 코드를 올리면 서버가 Judge0로 실행해 입력·정답을 만들어 저장합니다.
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label className="text-xs">생성기 (입력을 stdout으로 출력)</Label>
                <select
                  value={genLang}
                  onChange={(e) => setGenLang(e.target.value as Language)}
                  className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                >
                  {LANGUAGES.map((l) => (
                    <option key={l.value} value={l.value}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </div>
              <CodeEditor
                language={genLang}
                value={genCode}
                onChange={setGenCode}
                height="260px"
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label className="text-xs">모범답안 (입력 → 정답을 stdout으로)</Label>
                <select
                  value={solLang}
                  onChange={(e) => setSolLang(e.target.value as Language)}
                  className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                >
                  {LANGUAGES.map((l) => (
                    <option key={l.value} value={l.value}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </div>
              <CodeEditor
                language={solLang}
                value={solCode}
                onChange={setSolCode}
                height="260px"
              />
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">
                검증용 정답 코드 (선택 — 다른 방식의 두 번째 정답. 생성된 케이스가
                이 코드로도 풀려야 저장됩니다)
              </Label>
              <select
                value={valLang}
                onChange={(e) => setValLang(e.target.value as Language)}
                className="h-8 rounded-md border border-input bg-background px-2 text-sm"
              >
                {LANGUAGES.map((l) => (
                  <option key={l.value} value={l.value}>
                    {l.label}
                  </option>
                ))}
              </select>
            </div>
            <CodeEditor
              language={valLang}
              value={valCode}
              onChange={setValCode}
              height="180px"
            />
          </div>

          <div className="space-y-2">
            <Label className="text-xs">
              생성기 stdin (선택 — 시드/파라미터)
            </Label>
            <Textarea
              rows={2}
              value={genStdin}
              onChange={(e) => setGenStdin(e.target.value)}
              placeholder="생성기가 stdin으로 시드를 받는다면 여기에 입력"
            />
          </div>

          <div className="flex items-end gap-4 flex-wrap">
            <div className="space-y-2 max-w-28">
              <Label className="text-xs">순서</Label>
              <Input
                type="number"
                value={genOrderIndex}
                onChange={(e) => setGenOrderIndex(Number(e.target.value))}
              />
            </div>
            <label className="flex items-center gap-2 text-sm cursor-pointer h-9">
              <input
                type="checkbox"
                checked={genIsSample}
                onChange={(e) => setGenIsSample(e.target.checked)}
                className="size-4 rounded border-input"
              />
              샘플
            </label>
            <div className="ml-auto">
              <Button
                onClick={handleGenerate}
                disabled={generateMutation.isPending}
              >
                {generateMutation.isPending ? "생성 중..." : "생성"}
              </Button>
            </div>
          </div>

          {genResult && (
            <div className="rounded-md border p-3 space-y-2 text-sm">
              <div className="font-medium text-emerald-600">
                TC #{genResult.id} 생성됨 — 입력 {genResult.inputSize}자 / 출력{" "}
                {genResult.outputSize}자
                {genResult.generatorRuntimeMs != null &&
                  ` · 생성기 ${genResult.generatorRuntimeMs}ms`}
                {genResult.solutionRuntimeMs != null &&
                  ` · 모범답안 ${genResult.solutionRuntimeMs}ms`}
                {genResult.validatorRuntimeMs != null &&
                  ` · 검증 통과 ${genResult.validatorRuntimeMs}ms`}
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <div className="text-xs text-muted-foreground mb-1">
                    입력 미리보기
                  </div>
                  <pre className="bg-muted rounded p-2 overflow-x-auto text-xs whitespace-pre-wrap break-all max-h-40">
                    {genResult.inputPreview}
                  </pre>
                </div>
                <div>
                  <div className="text-xs text-muted-foreground mb-1">
                    출력 미리보기
                  </div>
                  <pre className="bg-muted rounded p-2 overflow-x-auto text-xs whitespace-pre-wrap break-all max-h-40">
                    {genResult.outputPreview}
                  </pre>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="space-y-4">
        {drafts.map((d, idx) => (
          <Card
            key={d.id ?? `new-${idx}`}
            className={d.dirty || d.metaDirty ? "border-amber-500" : ""}
          >
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2 flex-wrap">
                TC #{idx + 1}
                {d.id === undefined && (
                  <span className="text-xs text-muted-foreground">(신규)</span>
                )}
                {d.isDraft && (
                  <span className="text-xs text-amber-600">
                    ● 업로드 미완료 (채점 제외)
                  </span>
                )}
                {(d.dirty || d.metaDirty) && (
                  <span className="text-xs text-amber-600">● 미저장</span>
                )}
                {d.id !== undefined && (
                  <span className="text-xs font-normal text-muted-foreground">
                    입력 {chars(d.inputLength)} · 출력{" "}
                    {chars(d.expectedOutputLength)}
                  </span>
                )}
              </CardTitle>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={d.isSample}
                    onChange={(e) => setMeta(idx, "isSample", e.target.checked)}
                    className="size-4 rounded border-input"
                  />
                  샘플
                </label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => handleDelete(idx)}
                  disabled={busy}
                >
                  삭제
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              {d.loaded ? (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                      <Label className="text-xs">입력</Label>
                      <Textarea
                        rows={5}
                        value={d.input}
                        onChange={(e) => setData(idx, "input", e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label className="text-xs">예상 출력</Label>
                      <Textarea
                        rows={5}
                        value={d.expectedOutput}
                        onChange={(e) =>
                          setData(idx, "expectedOutput", e.target.value)
                        }
                      />
                    </div>
                  </div>
                  {isTruncated(d) && !d.dirty && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => collapse(idx)}
                    >
                      접기 (페이지에서 내려놓기)
                    </Button>
                  )}
                </>
              ) : (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                      <Label className="text-xs">
                        입력 — 앞 {chars(d.inputPreview.length)}만 표시
                      </Label>
                      <pre className="bg-muted rounded p-2 text-xs h-32 overflow-auto whitespace-pre-wrap break-all">
                        {d.inputPreview}
                      </pre>
                    </div>
                    <div className="space-y-2">
                      <Label className="text-xs">
                        예상 출력 — 앞{" "}
                        {chars(d.expectedOutputPreview.length)}만 표시
                      </Label>
                      <pre className="bg-muted rounded p-2 text-xs h-32 overflow-auto whitespace-pre-wrap break-all">
                        {d.expectedOutputPreview}
                      </pre>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={openingId !== null}
                      onClick={() => openFull(idx)}
                    >
                      {openingId === d.id ? "불러오는 중..." : "전체 펼쳐서 편집"}
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={openingId !== null}
                      onClick={() => downloadPart(idx, "input")}
                    >
                      입력 .txt로 저장
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={openingId !== null}
                      onClick={() => downloadPart(idx, "output")}
                    >
                      출력 .txt로 저장
                    </Button>
                    <span className="text-xs text-muted-foreground">
                      큰 케이스는 펼치는 순간 브라우저가 느려집니다 — 확인만
                      하려면 .txt로 저장하세요
                    </span>
                  </div>
                </>
              )}
              <div className="space-y-2 max-w-32">
                <Label className="text-xs">순서</Label>
                <Input
                  type="number"
                  value={d.orderIndex}
                  onChange={(e) =>
                    setMeta(idx, "orderIndex", Number(e.target.value))
                  }
                />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="flex items-center justify-between">
        <Button type="button" variant="outline" onClick={addLocal}>
          + TC 추가
        </Button>
        <div className="flex items-center gap-3">
          {dirtyCount > 0 && (
            <span className="text-sm text-amber-600">
              미저장 {dirtyCount}건
            </span>
          )}
          <Button onClick={handleSaveAll} disabled={busy || dirtyCount === 0}>
            {busy ? "저장 중..." : "변경사항 저장"}
          </Button>
        </div>
      </div>
    </main>
  );
}