"use client";

import { CircleCheckIcon, Loader2Icon, OctagonXIcon, Trash2, TriangleAlertIcon, UploadIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { problemsApi } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { downloadTextFile } from "@/lib/download";
import {
  parseProblemFile,
  toCreateProblemRequest,
  PROBLEM_TEMPLATE,
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
}

let nextKey = 1;

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
    // Sequential on purpose: keeps server load low and #id order == list order.
    for (const item of items) {
      if (item.status !== "parsed" || !item.parsed) continue;
      setItems((prev) =>
        prev.map((it) => (it.key === item.key ? { ...it, status: "uploading" } : it)),
      );
      try {
        const created = await problemsApi.create(toCreateProblemRequest(item.parsed));
        ok++;
        setItems((prev) =>
          prev.map((it) =>
            it.key === item.key ? { ...it, status: "done", createdId: created.id } : it,
          ),
        );
      } catch (err) {
        failed++;
        const message =
          err instanceof ApiError ? err.message : "업로드 실패 (네트워크 오류)";
        setItems((prev) =>
          prev.map((it) =>
            it.key === item.key ? { ...it, status: "failed", message } : it,
          ),
        );
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
                    {it.status === "parse_error" && ` · ⚠️ ${it.message}`}
                    {it.status === "failed" && ` · ❌ ${it.message}`}
                  </p>
                </div>
                {it.status === "done" && it.createdId != null ? (
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
