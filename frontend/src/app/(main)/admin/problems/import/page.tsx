"use client";

import { CircleCheckIcon, Loader2Icon, OctagonXIcon, Trash2, TriangleAlertIcon, UploadIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { problemsApi, type UpdateProblemRequest } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { downloadTextFile } from "@/lib/download";
import {
  parseProblemFile,
  toCreateProblemRequest,
  PROBLEM_TEMPLATE,
  GENERATOR_TEMPLATE,
  type ParsedProblem,
} from "@/lib/problem-file";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DifficultyBadge } from "@/components/status-badge";

type ItemStatus = "parsed" | "parse_error" | "uploading" | "done" | "failed";

interface ImportItem {
  key: number;
  fileName: string;
  parsed?: ParsedProblem;
  status: ItemStatus;
  message?: string;
  createdId?: number;
  // Live progress text while uploading ("테스트케이스 3/10 업로드 중").
  progress?: string;
}

let nextKey = 1;

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

/**
 * Upload one parsed problem, choosing the path by shape/size:
 * - @generator file  → create (private) + server-side generation per case
 * - small file       → single create request (previous behavior)
 * - big raw file     → create (private) + per-case upload, chunked when needed
 * Multi-request paths keep the problem private until every case landed, so a
 * half-uploaded problem is never visible or judged.
 */
async function uploadOne(
  parsed: ParsedProblem,
  onProgress: (text: string) => void,
  onCreated: (id: number) => void,
): Promise<number> {
  if (parsed.generator) {
    const gen = parsed.generator;
    const base = toCreateProblemRequest(parsed);
    const created = await problemsApi.create({ ...base, isPublic: false });
    onCreated(created.id);
    const inlineCount = base.testCases?.length ?? 0;
    for (let j = 0; j < gen.cases.length; j++) {
      onProgress(`케이스 ${j + 1}/${gen.cases.length} 생성 중`);
      try {
        await problemsApi.generateTestCase(created.id, {
          generatorLanguage: gen.generatorLanguage,
          generatorCode: gen.generatorCode,
          generatorStdin: gen.cases[j].stdin,
          solutionLanguage: gen.solutionLanguage,
          solutionCode: gen.solutionCode,
          orderIndex: inlineCount + j,
          isSample: gen.cases[j].isSample,
        });
      } catch (err) {
        throw new Error(
          `케이스 ${j + 1}/${gen.cases.length} 생성 실패: ${uploadErrorMessage(err)}`,
        );
      }
    }
    if (parsed.isPublic) {
      await problemsApi.update(created.id, toUpdateRequest(parsed));
    }
    return created.id;
  }

  const fullReq = toCreateProblemRequest(parsed);
  if (byteSize(fullReq) <= SAFE_REQUEST_BYTES) {
    const created = await problemsApi.create(fullReq);
    onCreated(created.id);
    return created.id;
  }

  if (parsed.subtasks && parsed.subtasks.length > 0) {
    throw new Error("파일이 너무 커서 서브태스크 문제는 분할 업로드할 수 없습니다");
  }

  const created = await problemsApi.create({
    ...fullReq,
    testCases: [],
    isPublic: false,
  });
  onCreated(created.id);

  const cases = parsed.testCases;
  for (let i = 0; i < cases.length; i++) {
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
        await problemsApi.addTestCase(created.id, oneShot);
      } else {
        const draft = await problemsApi.addTestCase(created.id, {
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
          await problemsApi.appendTestCaseChunk(created.id, draft.id, {
            inputChunk: chunk,
          });
        }
        for (const chunk of outputChunks) {
          onProgress(`${label} (조각 ${++sent}/${totalChunks})`);
          await problemsApi.appendTestCaseChunk(created.id, draft.id, {
            expectedOutputChunk: chunk,
          });
        }
        await problemsApi.finalizeTestCase(created.id, draft.id);
      }
    } catch (err) {
      throw new Error(
        `테스트케이스 ${i + 1}/${cases.length} 업로드 실패: ${uploadErrorMessage(err)}`,
      );
    }
  }

  if (parsed.isPublic) {
    await problemsApi.update(created.id, toUpdateRequest(parsed));
  }
  return created.id;
}

export default function ImportProblemsPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [items, setItems] = useState<ImportItem[]>([]);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  const addFiles = useCallback(async (files: FileList | File[]) => {
    const next: ImportItem[] = [];
    for (const file of Array.from(files)) {
      try {
        const parsed = parseProblemFile(await file.text());
        next.push({ key: nextKey++, fileName: file.name, parsed, status: "parsed" });
      } catch (err) {
        next.push({
          key: nextKey++,
          fileName: file.name,
          status: "parse_error",
          message: err instanceof Error ? err.message : "파싱 실패",
        });
      }
    }
    setItems((prev) => [...prev, ...next]);
  }, []);

  const removeItem = (key: number) =>
    setItems((prev) => prev.filter((it) => it.key !== key));

  const uploadable = items.filter((it) => it.status === "parsed");

  const uploadAll = async () => {
    setUploading(true);
    let ok = 0;
    let failed = 0;
    const patch = (key: number, p: Partial<ImportItem>) =>
      setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...p } : it)));
    // Sequential on purpose: keeps server load low and #id order == list order.
    for (const item of items) {
      if (item.status !== "parsed" || !item.parsed) continue;
      patch(item.key, { status: "uploading", progress: undefined });
      try {
        const id = await uploadOne(
          item.parsed,
          (text) => patch(item.key, { progress: text }),
          // Record the id as soon as the problem exists so a mid-way failure
          // still links to the (private) partial problem for inspection.
          (createdId) => patch(item.key, { createdId }),
        );
        ok++;
        patch(item.key, { status: "done", createdId: id, progress: undefined });
      } catch (err) {
        failed++;
        const message =
          err instanceof ApiError || err instanceof TypeError || !(err instanceof Error)
            ? uploadErrorMessage(err)
            : err.message;
        patch(item.key, { status: "failed", message, progress: undefined });
      }
    }
    setUploading(false);
    if (failed === 0) toast.success(`${ok}개 문제 등록 완료`);
    else toast.error(`${ok}개 성공, ${failed}개 실패 — 실패 항목을 확인하세요`);
  };

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
            onClick={() => downloadTextFile("problem-template.md", PROBLEM_TEMPLATE)}
          >
            템플릿 다운로드
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() =>
              downloadTextFile("problem-generator-template.md", GENERATOR_TEMPLATE)
            }
          >
            생성기 템플릿
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
              dragOver ? "border-primary bg-primary/5" : "border-input hover:bg-muted/40"
            }`}
          >
            <UploadIcon className="size-8 text-muted-foreground" />
            <p className="text-sm font-medium">
              문제 파일(.md)을 여기에 끌어놓거나 클릭해서 선택
            </p>
            <p className="text-xs text-muted-foreground">
              여러 파일을 한 번에 선택할 수 있습니다 · 형식은 템플릿 참고
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

      {items.length > 0 && (
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-base">
              불러온 파일 {items.length}개
              {uploadable.length > 0 && ` · 업로드 가능 ${uploadable.length}개`}
            </CardTitle>
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
          </CardHeader>
          <CardContent className="divide-y p-0">
            {items.map((it) => (
              <div key={it.key} className="flex items-center gap-3 px-4 py-3">
                <span className="shrink-0">
                  {it.status === "parsed" && (
                    <Badge variant="outline" className="text-muted-foreground">대기</Badge>
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
                    {it.parsed && <DifficultyBadge difficulty={it.parsed.difficulty} />}
                    {it.parsed?.tags.map((t) => (
                      <Badge key={t} variant="outline" className="text-muted-foreground hidden sm:inline-flex">
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
                    {it.status === "uploading" && it.progress && ` · ${it.progress}`}
                    {it.status === "parse_error" && ` · ⚠️ ${it.message}`}
                    {it.status === "failed" && ` · ❌ ${it.message}`}
                  </p>
                </div>
                {it.createdId != null ? (
                  <Button
                    variant="outline"
                    size="sm"
                    render={<Link href={`/problems/${it.createdId}`} />}
                  >
                    #{it.createdId} 보기
                  </Button>
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
            ))}
          </CardContent>
        </Card>
      )}
    </main>
  );
}
