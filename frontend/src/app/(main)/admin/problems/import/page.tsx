"use client";

import {
  ArrowDownAZIcon,
  ArrowDownIcon,
  ArrowUpIcon,
  CircleCheckIcon,
  GripVerticalIcon,
  Loader2Icon,
  OctagonXIcon,
  RefreshCwIcon,
  RotateCwIcon,
  Trash2,
  TriangleAlertIcon,
  UploadIcon,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { problemsApi, type UpdateProblemRequest } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import {
  beginRun,
  endRun,
  isRunLive,
  useImportBatchStore,
  type ImportItem,
} from "@/lib/import-batch-store";
import { downloadTextFile } from "@/lib/download";
import {
  applyAssetUrls,
  assetReferenceNames,
  expectedCaseCount,
  inlineCaseCount,
  parseProblemFile,
  toCreateProblemRequest,
  PROBLEM_TEMPLATE,
  type ParsedProblem,
} from "@/lib/problem-file";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DifficultyBadge } from "@/components/status-badge";

// A multi-file drop hands over dataTransfer.files in whatever order the OS/browser
// happened to collect them — for Chrome that is the file you grabbed first, then
// the rest — so the list (and therefore the #id order) came out shuffled. Sort every
// incoming batch by file name instead, numerically so "2.md" precedes "10.md".
const nameCollator = new Intl.Collator("ko", {
  numeric: true,
  sensitivity: "base",
});
const byFileName = (a: File, b: File) => nameCollator.compare(a.name, b.name);

// Everything must clear the reverse proxy's ~1MB body limit, with headroom for
// JSON escaping. Requests under SAFE_REQUEST_BYTES go out in one shot; larger
// problems are created empty and their test cases uploaded one by one, chunked
// when a single case exceeds SINGLE_CASE_BYTES.
const SAFE_REQUEST_BYTES = 800 * 1024;
const SINGLE_CASE_BYTES = 512 * 1024;
const CHUNK_CHARS = 384 * 1024;
const MAX_ESCAPED_CHUNK_BYTES = 900 * 1024;

const byteSize = (body: unknown) => new Blob([JSON.stringify(body)]).size;

// Split into ~CHUNK_CHARS pieces, re-splitting any piece whose JSON-escaped
// byte size is still too large (e.g. newline/unicode-heavy data).
function splitChunks(s: string): string[] {
  const out: string[] = [];
  for (let i = 0; i < s.length; i += CHUNK_CHARS) {
    out.push(s.slice(i, i + CHUNK_CHARS));
  }
  const fit: string[] = [];
  const queue = out.reverse();
  while (queue.length > 0) {
    const c = queue.pop()!;
    if (c.length <= 1 || byteSize(c) <= MAX_ESCAPED_CHUNK_BYTES) {
      fit.push(c);
    } else {
      const mid = Math.ceil(c.length / 2);
      queue.push(c.slice(mid), c.slice(0, mid));
    }
  }
  return fit;
}

const uploadErrorMessage = (err: unknown): string => {
  if (err instanceof ApiError) return err.message;
  if (err instanceof TypeError) {
    return "네트워크 오류 또는 요청 크기 초과로 서버가 응답을 차단했습니다";
  }
  return "업로드 실패 (네트워크 오류)";
};

const toUpdateRequest = (p: ParsedProblem): UpdateProblemRequest => ({
  title: p.title,
  description: p.description,
  inputDescription: p.inputDescription,
  outputDescription: p.outputDescription,
  timeLimit: Math.round(p.timeLimit * 1000),
  memoryLimit: Math.round(p.memoryLimit * 1024),
  difficulty: p.difficulty,
  tags: p.tags,
  isPublic: true,
});

/** Pushes referenced images to S3 and rewrites ](asset:이름) to the returned URLs. */
async function uploadAssets(
  parsed: ParsedProblem,
  onProgress: (text: string) => void,
): Promise<ParsedProblem> {
  const referenced = new Set(assetReferenceNames(parsed));
  const toUpload = (parsed.assets ?? []).filter((a) => referenced.has(a.name));
  const urls: Record<string, string> = {};
  for (let k = 0; k < toUpload.length; k++) {
    onProgress(`이미지 ${k + 1}/${toUpload.length} 업로드 중`);
    try {
      const { url } = await problemsApi.uploadImage({
        contentType: toUpload[k].contentType,
        base64Data: toUpload[k].base64,
      });
      urls[toUpload[k].name] = url;
    } catch (err) {
      throw new Error(
        `이미지 '${toUpload[k].name}' 업로드 실패: ${uploadErrorMessage(err)}`,
      );
    }
  }
  return { ...parsed, ...applyAssetUrls(parsed, urls) };
}

/**
 * Upload one parsed problem, choosing the path by shape/size:
 * - @generator file  → create (private) + server-side generation per case
 * - small file       → single create request (previous behavior)
 * - big raw file     → create (private) + per-case upload, chunked when needed
 * Multi-request paths keep the problem private until every case landed, so a
 * half-uploaded problem is never visible or judged.
 *
 * Pass `existingId` to continue a problem an earlier run already created: the
 * cases the server holds are counted and only the missing ones are sent, then
 * the problem is published. That is what turns a batch cut off mid-flight
 * (tab closed, network dropped) into a resumable one instead of a private
 * half-problem nobody can tell apart from a finished one.
 */
async function uploadOne(
  parsed: ParsedProblem,
  onProgress: (text: string) => void,
  onCreated: (id: number) => void,
  existingId?: number,
): Promise<number> {
  const publish = async (id: number) => {
    if (parsed.isPublic) {
      await problemsApi.update(id, toUpdateRequest(parsed));
    }
  };

  if (parsed.generator) {
    const gen = parsed.generator;
    const inlineCount = inlineCaseCount(parsed);
    let problemId = existingId;
    let startIdx = 0;
    if (problemId == null) {
      const base = toCreateProblemRequest(parsed);
      const created = await problemsApi.create({ ...base, isPublic: false });
      problemId = created.id;
      onCreated(created.id);
    } else {
      // Everything beyond the inline cases that already landed is done.
      const landed = await problemsApi.listTestCases(problemId);
      startIdx = Math.min(
        gen.cases.length,
        Math.max(0, landed.length - inlineCount),
      );
    }
    for (let j = startIdx; j < gen.cases.length; j++) {
      onProgress(`케이스 ${j + 1}/${gen.cases.length} 생성 중`);
      try {
        await problemsApi.generateTestCase(problemId, {
          generatorLanguage: gen.generatorLanguage,
          generatorCode: gen.generatorCode,
          generatorStdin: gen.cases[j].stdin,
          solutionLanguage: gen.solutionLanguage,
          solutionCode: gen.solutionCode,
          validatorLanguage: gen.validatorLanguage,
          validatorCode: gen.validatorCode,
          orderIndex: inlineCount + j,
          isSample: gen.cases[j].isSample,
        });
      } catch (err) {
        throw new Error(
          `케이스 ${j + 1}/${gen.cases.length} 생성 실패: ${uploadErrorMessage(err)}`,
        );
      }
    }
    await publish(problemId);
    return problemId;
  }

  const fullReq = toCreateProblemRequest(parsed);
  if (byteSize(fullReq) <= SAFE_REQUEST_BYTES) {
    // One request, so there is nothing half-done to continue — a retry with an
    // existing problem only has to make sure it ended up published.
    if (existingId != null) {
      await publish(existingId);
      return existingId;
    }
    const created = await problemsApi.create(fullReq);
    onCreated(created.id);
    return created.id;
  }

  if (parsed.subtasks && parsed.subtasks.length > 0) {
    throw new Error(
      "파일이 너무 커서 서브태스크 문제는 분할 업로드할 수 없습니다",
    );
  }

  let problemId = existingId;
  let startIdx = 0;
  if (problemId == null) {
    const created = await problemsApi.create({
      ...fullReq,
      testCases: [],
      isPublic: false,
    });
    problemId = created.id;
    onCreated(created.id);
  } else {
    const landed = await problemsApi.listTestCases(problemId);
    // A case interrupted mid-chunk is still a draft holding partial data —
    // drop it and send that case again from the start.
    for (const stale of landed.filter((tc) => tc.isDraft)) {
      await problemsApi.deleteTestCase(problemId, stale.id);
    }
    startIdx = landed.filter((tc) => !tc.isDraft).length;
  }

  const cases = parsed.testCases;
  for (let i = startIdx; i < cases.length; i++) {
    const label = `테스트케이스 ${i + 1}/${cases.length} 업로드 중`;
    onProgress(label);
    const tc = cases[i];
    try {
      const oneShot = {
        input: tc.input,
        expectedOutput: tc.expectedOutput,
        orderIndex: i,
        isSample: tc.isSample,
      };
      if (byteSize(oneShot) <= SINGLE_CASE_BYTES) {
        await problemsApi.addTestCase(problemId, oneShot);
      } else {
        const draft = await problemsApi.addTestCase(problemId, {
          input: "",
          expectedOutput: "",
          orderIndex: i,
          isSample: tc.isSample,
          draft: true,
        });
        const inputChunks = splitChunks(tc.input);
        const outputChunks = splitChunks(tc.expectedOutput);
        const totalChunks = inputChunks.length + outputChunks.length;
        let sent = 0;
        for (const chunk of inputChunks) {
          onProgress(`${label} (조각 ${++sent}/${totalChunks})`);
          await problemsApi.appendTestCaseChunk(problemId, draft.id, {
            inputChunk: chunk,
          });
        }
        for (const chunk of outputChunks) {
          onProgress(`${label} (조각 ${++sent}/${totalChunks})`);
          await problemsApi.appendTestCaseChunk(problemId, draft.id, {
            expectedOutputChunk: chunk,
          });
        }
        await problemsApi.finalizeTestCase(problemId, draft.id);
      }
    } catch (err) {
      throw new Error(
        `테스트케이스 ${i + 1}/${cases.length} 업로드 실패: ${uploadErrorMessage(err)}`,
      );
    }
  }

  await publish(problemId);
  return problemId;
}

export default function ImportProblemsPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  // The batch lives outside the component: an upload started here keeps running
  // (and keeps recording progress) after the admin navigates away, and is still
  // here — with what landed and what didn't — when they come back.
  const items = useImportBatchStore((s) => s.items);
  const uploading = useImportBatchStore((s) => s.running);
  const addItems = useImportBatchStore((s) => s.addItems);
  const patchItem = useImportBatchStore((s) => s.patchItem);
  const setItems = useImportBatchStore((s) => s.setItems);
  const removeItem = useImportBatchStore((s) => s.removeItem);
  const clearBatch = useImportBatchStore((s) => s.clear);
  const [dragOver, setDragOver] = useState(false);
  const [checking, setChecking] = useState(false);
  // Row being dragged, and the row it is currently hovering over.
  const [dragKey, setDragKey] = useState<number | null>(null);
  const [dropKey, setDropKey] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  /** Asks the server what each created problem actually holds right now. */
  const refreshServerState = useCallback(async () => {
    const targets = useImportBatchStore
      .getState()
      .items.filter((it) => it.createdId != null && it.status !== "uploading");
    if (targets.length === 0) return;
    setChecking(true);
    for (const it of targets) {
      const id = it.createdId!;
      try {
        const [detail, cases] = await Promise.all([
          problemsApi.detail(id),
          problemsApi.listTestCases(id),
        ]);
        const caseCount = cases.filter((c) => !c.isDraft).length;
        const server = {
          isPublic: detail.isPublic,
          caseCount,
          draftCaseCount: cases.length - caseCount,
          checkedAt: Date.now(),
        };
        const expected = it.parsed ? expectedCaseCount(it.parsed) : caseCount;
        const wantsPublic = it.parsed ? it.parsed.isPublic : detail.isPublic;
        const complete =
          caseCount >= expected &&
          server.draftCaseCount === 0 &&
          (!wantsPublic || detail.isPublic);
        patchItem(it.key, {
          server,
          ...(complete
            ? { status: "done" as const, message: undefined }
            : it.status === "done"
              ? {
                  status: "failed" as const,
                  message: "서버에 일부만 등록돼 있습니다",
                }
              : {}),
        });
      } catch (err) {
        patchItem(it.key, {
          server: undefined,
          message:
            err instanceof ApiError && err.status === 404
              ? "문제를 찾을 수 없습니다 (삭제됨)"
              : it.message,
        });
      }
    }
    setChecking(false);
  }, [patchItem]);

  // On arrival: a run marked running that isn't alive in this JS context died
  // with the previous page load, so its rows are stranded — say so instead of
  // leaving a spinner that never resolves.
  const reconciled = useRef(false);
  useEffect(() => {
    if (user?.role !== "ADMIN" || reconciled.current) return;
    reconciled.current = true;
    const state = useImportBatchStore.getState();
    if (state.running && !isRunLive()) {
      endRun();
      for (const it of state.items) {
        if (it.status === "uploading") {
          patchItem(it.key, {
            status: "failed",
            progress: undefined,
            message: "페이지를 벗어나 업로드가 중단됐습니다",
          });
        }
      }
    }
    void refreshServerState();
  }, [user, patchItem, refreshServerState]);

  // Hard navigations (reload, tab close) do kill the upload — warn first.
  useEffect(() => {
    if (!uploading) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [uploading]);

  // Reading files is async, so two quick drops could otherwise append in
  // completion order rather than drop order. Chain the batches to keep them ordered.
  const appendQueue = useRef<Promise<void>>(Promise.resolve());

  const addFiles = useCallback(
    (files: FileList | File[]) => {
      const batch = Array.from(files).sort(byFileName);
      appendQueue.current = appendQueue.current.then(async () => {
        const next: Omit<ImportItem, "key">[] = [];
        for (const file of batch) {
          try {
            const parsed = parseProblemFile(await file.text());
            next.push({ fileName: file.name, parsed, status: "parsed" });
          } catch (err) {
            next.push({
              fileName: file.name,
              status: "parse_error",
              message: err instanceof Error ? err.message : "파싱 실패",
            });
          }
        }
        addItems(next);
      });
    },
    [addItems],
  );

  // Manual override for cases where file names don't encode the intended order.
  const moveItem = (index: number, delta: number) => {
    const to = index + delta;
    if (to < 0 || to >= items.length) return;
    const next = [...items];
    [next[index], next[to]] = [next[to], next[index]];
    setItems(next);
  };

  // Pull the dragged row out and drop it into the target row's slot, pushing the
  // rest along — the same result the insertion line drawn during the drag shows.
  const reorder = (fromKey: number, toKey: number) => {
    const from = items.findIndex((it) => it.key === fromKey);
    const to = items.findIndex((it) => it.key === toKey);
    if (from < 0 || to < 0 || from === to) return;
    const next = [...items];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    setItems(next);
  };

  const endDrag = () => {
    setDragKey(null);
    setDropKey(null);
  };

  const sortByFileName = () =>
    setItems(
      [...items].sort((a, b) => nameCollator.compare(a.fileName, b.fileName)),
    );

  const uploadable = items.filter((it) => it.status === "parsed");
  // Cut off or failed, and still holding the parsed file needed to continue.
  const resumable = items.filter(
    (it) => it.status === "failed" && it.parsed && !it.parsedDropped,
  );

  const runItems = async (targets: ImportItem[]) => {
    if (targets.length === 0 || useImportBatchStore.getState().running) return;
    beginRun();
    let ok = 0;
    let failed = 0;
    try {
      for (const target of targets) {
        // Read the row fresh: an earlier pass may have recorded its problem id.
        const item = useImportBatchStore
          .getState()
          .items.find((it) => it.key === target.key);
        if (!item?.parsed) continue;
        patchItem(item.key, {
          status: "uploading",
          progress: undefined,
          message: undefined,
        });
        const onProgress = (text: string) =>
          patchItem(item.key, { progress: text });
        try {
          let parsed = item.parsed;
          if (item.createdId == null && parsed.assets?.length) {
            // Rewrite asset links before creating, and keep the rewritten copy so
            // a later resume publishes the same description the problem holds.
            parsed = await uploadAssets(parsed, onProgress);
            patchItem(item.key, { parsed });
          }
          const id = await uploadOne(
            parsed,
            onProgress,
            // Record the id as soon as the problem exists so a mid-way failure
            // still links to the (private) partial problem for inspection.
            (createdId) => patchItem(item.key, { createdId }),
            item.createdId,
          );
          ok++;
          patchItem(item.key, {
            status: "done",
            createdId: id,
            progress: undefined,
            message: undefined,
          });
        } catch (err) {
          failed++;
          const message =
            err instanceof ApiError ||
            err instanceof TypeError ||
            !(err instanceof Error)
              ? uploadErrorMessage(err)
              : err.message;
          patchItem(item.key, {
            status: "failed",
            message,
            progress: undefined,
          });
        }
      }
    } finally {
      // Never leave the batch marked running — that would block every retry.
      endRun();
    }
    await refreshServerState();
    if (failed === 0) toast.success(`${ok}개 문제 등록 완료`);
    else toast.error(`${ok}개 성공, ${failed}개 실패 — 실패 항목을 확인하세요`);
  };

  const uploadAll = () =>
    runItems(items.filter((it) => it.status === "parsed"));
  const resumeAll = () => runItems(resumable);

  if (!user || user.role !== "ADMIN") return null;

  return (
    <main className="max-w-4xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-semibold">문제 일괄 업로드</h1>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() =>
              downloadTextFile("problem-template.md", PROBLEM_TEMPLATE)
            }
          >
            템플릿 다운로드
          </Button>
          <Button
            variant="outline"
            size="sm"
            render={<Link href="/admin/problems/new" />}
          >
            한 개씩 출제
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          <div
            role="button"
            tabIndex={0}
            onClick={() => inputRef.current?.click()}
            onKeyDown={(e) => e.key === "Enter" && inputRef.current?.click()}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
            }}
            className={`flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-10 cursor-pointer transition-colors ${
              dragOver
                ? "border-primary bg-primary/5"
                : "border-input hover:bg-muted/40"
            }`}
          >
            <UploadIcon className="size-8 text-muted-foreground" />
            <p className="text-sm font-medium">
              문제 파일(.md)을 여기에 끌어놓거나 클릭해서 선택
            </p>
            <p className="text-xs text-muted-foreground">
              여러 파일을 한 번에 선택할 수 있습니다 · 형식은 템플릿 참고
            </p>
            <p className="text-xs text-muted-foreground">
              불러온 파일은 파일명 순(숫자 인식)으로 정렬되며, 목록 순서대로
              등록됩니다
            </p>
            <input
              ref={inputRef}
              type="file"
              multiple
              accept=".md,.markdown,.txt,text/markdown,text/plain"
              className="hidden"
              onChange={(e) => {
                if (e.target.files?.length) addFiles(e.target.files);
                e.target.value = "";
              }}
            />
          </div>
        </CardContent>
      </Card>

      {uploading && (
        <Card className="border-blue-500">
          <CardContent className="p-4 flex items-start gap-3">
            <Loader2Icon className="size-4 mt-0.5 animate-spin text-blue-500" />
            <div className="text-sm">
              <div className="font-medium">업로드 진행 중</div>
              <p className="text-muted-foreground">
                다른 페이지로 이동해도 업로드는 계속되고, 이 화면으로 돌아오면
                진행 상황이 그대로 보입니다. 다만 새로고침하거나 탭을 닫으면
                중단되며, 중단된 문제는 비공개로 남습니다.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {items.length > 0 && (
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-base">
              불러온 파일 {items.length}개
              {uploadable.length > 0 && ` · 업로드 가능 ${uploadable.length}개`}
              {resumable.length > 0 && ` · 중단됨 ${resumable.length}개`}
              <span className="block text-xs font-normal text-muted-foreground mt-0.5">
                위에서부터 순서대로 등록됩니다 · 끌어서 순서 변경
              </span>
            </CardTitle>
            <div className="flex gap-2 flex-wrap justify-end">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={uploading || checking}
                onClick={() => void refreshServerState()}
              >
                <RefreshCwIcon
                  className={`size-4 mr-1 ${checking ? "animate-spin" : ""}`}
                />
                상태 확인
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={uploading}
                onClick={() => {
                  if (
                    confirm(
                      "목록을 비울까요? 이미 등록된 문제는 지워지지 않습니다.",
                    )
                  ) {
                    clearBatch();
                  }
                }}
              >
                목록 비우기
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={uploading || items.length < 2}
                onClick={sortByFileName}
              >
                <ArrowDownAZIcon className="size-4 mr-1" />
                이름순 정렬
              </Button>
              {resumable.length > 0 && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={uploading}
                  onClick={resumeAll}
                >
                  <RotateCwIcon className="size-4 mr-1" />
                  중단된 {resumable.length}개 이어서 등록
                </Button>
              )}
              <Button
                type="button"
                size="sm"
                disabled={uploading || uploadable.length === 0}
                onClick={uploadAll}
              >
                {uploading ? (
                  <>
                    <Loader2Icon className="size-4 mr-1 animate-spin" />
                    업로드 중...
                  </>
                ) : (
                  `${uploadable.length}개 문제 등록`
                )}
              </Button>
            </div>
          </CardHeader>
          <CardContent className="divide-y p-0">
            {items.map((it, index) => {
              const draggable = !uploading && it.createdId == null;
              const dragIndex =
                dragKey == null
                  ? -1
                  : items.findIndex((o) => o.key === dragKey);
              // Insertion line on the edge the dragged row will land against.
              // Inline so it can't lose to the divide-y borders on these rows.
              const showMarker =
                dropKey === it.key && dragIndex >= 0 && dragIndex !== index;
              const markerStyle = showMarker
                ? {
                    boxShadow: `inset 0 ${dragIndex < index ? "-2px" : "2px"} 0 0 var(--primary)`,
                  }
                : undefined;
              return (
                <div
                  key={it.key}
                  draggable={draggable}
                  onDragStart={(e) => {
                    setDragKey(it.key);
                    e.dataTransfer.effectAllowed = "move";
                    // Firefox only starts a drag once data is set.
                    e.dataTransfer.setData("text/plain", String(it.key));
                  }}
                  onDragEnd={endDrag}
                  onDragOver={(e) => {
                    if (dragKey == null || dragKey === it.key) return;
                    e.preventDefault();
                    e.dataTransfer.dropEffect = "move";
                    setDropKey(it.key);
                  }}
                  onDragLeave={() =>
                    setDropKey((k) => (k === it.key ? null : k))
                  }
                  onDrop={(e) => {
                    if (dragKey == null) return;
                    e.preventDefault();
                    e.stopPropagation();
                    reorder(dragKey, it.key);
                    endDrag();
                  }}
                  style={markerStyle}
                  className={`flex items-center gap-3 px-4 py-3 ${
                    dragKey === it.key ? "opacity-40" : ""
                  } ${draggable ? "cursor-grab active:cursor-grabbing" : ""}`}
                >
                  <span className="shrink-0 flex items-center gap-1">
                    <GripVerticalIcon
                      className={`size-4 ${
                        draggable ? "text-muted-foreground" : "text-transparent"
                      }`}
                      aria-hidden="true"
                    />
                    <span className="w-6 text-xs tabular-nums text-muted-foreground text-right">
                      {index + 1}
                    </span>
                  </span>
                  <span className="shrink-0">
                    {it.status === "parsed" && (
                      <Badge
                        variant="outline"
                        className="text-muted-foreground"
                      >
                        대기
                      </Badge>
                    )}
                    {it.status === "parse_error" && (
                      <TriangleAlertIcon className="size-4 text-amber-500" />
                    )}
                    {it.status === "uploading" && (
                      <Loader2Icon className="size-4 animate-spin text-blue-500" />
                    )}
                    {it.status === "done" && (
                      <CircleCheckIcon className="size-4 text-green-500" />
                    )}
                    {it.status === "failed" && (
                      <OctagonXIcon className="size-4 text-red-500" />
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 min-w-0">
                      <span className="font-medium truncate">
                        {it.parsed ? it.parsed.title : it.fileName}
                      </span>
                      {it.parsed && (
                        <DifficultyBadge difficulty={it.parsed.difficulty} />
                      )}
                      {it.parsed?.tags.map((t) => (
                        <Badge
                          key={t}
                          variant="outline"
                          className="text-muted-foreground hidden sm:inline-flex"
                        >
                          {t}
                        </Badge>
                      ))}
                    </div>
                    <p className="text-xs text-muted-foreground truncate">
                      {it.fileName}
                      {it.parsed &&
                        (it.parsed.subtasks
                          ? ` · 서브태스크 ${it.parsed.subtasks.length}개`
                          : ` · 테스트케이스 ${it.parsed.testCases.length}개`)}
                      {it.parsed?.generator &&
                        ` · 생성기 케이스 ${it.parsed.generator.cases.length}개`}
                      {it.status === "uploading" &&
                        it.progress &&
                        ` · ${it.progress}`}
                      {it.status === "parse_error" && ` · ⚠️ ${it.message}`}
                      {it.status === "failed" && ` · ❌ ${it.message}`}
                      {it.parsedDropped &&
                        it.status === "failed" &&
                        " · 파일 내용이 남아있지 않습니다 — 같은 .md를 다시 올려 주세요"}
                    </p>
                    {it.server && (
                      // Server truth, so a row that finished while the admin was
                      // on another page (or died half-way) reads correctly.
                      <p className="text-xs truncate">
                        <span className="text-muted-foreground">
                          서버 상태 — 케이스{" "}
                          {it.parsed
                            ? `${it.server.caseCount}/${expectedCaseCount(it.parsed)}`
                            : it.server.caseCount}
                          개
                          {it.server.draftCaseCount > 0 &&
                            ` (미완료 ${it.server.draftCaseCount}개)`}
                          {" · "}
                        </span>
                        <span
                          className={
                            it.server.isPublic
                              ? "text-green-600"
                              : "text-amber-600"
                          }
                        >
                          {it.server.isPublic ? "공개" : "비공개"}
                        </span>
                      </p>
                    )}
                  </div>
                  {it.createdId == null && (
                    <div className="flex shrink-0">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={uploading || index === 0}
                        onClick={() => moveItem(index, -1)}
                        aria-label="위로 이동"
                      >
                        <ArrowUpIcon className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={uploading || index === items.length - 1}
                        onClick={() => moveItem(index, 1)}
                        aria-label="아래로 이동"
                      >
                        <ArrowDownIcon className="size-4" />
                      </Button>
                    </div>
                  )}
                  {it.status === "failed" && it.parsed && !it.parsedDropped && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={uploading}
                      onClick={() => void runItems([it])}
                    >
                      <RotateCwIcon className="size-4 mr-1" />
                      {it.createdId != null ? "이어서" : "재시도"}
                    </Button>
                  )}
                  {it.createdId != null ? (
                    <div className="flex shrink-0 gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        render={
                          <Link
                            href={`/admin/problems/${it.createdId}/test-cases`}
                          />
                        }
                      >
                        TC
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        render={<Link href={`/problems/${it.createdId}`} />}
                      >
                        #{it.createdId} 보기
                      </Button>
                    </div>
                  ) : (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={uploading}
                      onClick={() => removeItem(it.key)}
                      aria-label="목록에서 제거"
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  )}
                </div>
              );
            })}
          </CardContent>
        </Card>
      )}
    </main>
  );
}
