"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ApiError } from "@/lib/api";
import { problemsApi, type UpdateProblemRequest } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { MarkdownEditor } from "@/components/markdown-editor";
import type { Difficulty } from "@/types/api";

const schema = z.object({
  title: z.string().min(1, { error: "제목을 입력하세요" }).max(200),
  description: z.string().min(1, { error: "설명을 입력하세요" }),
  inputDescription: z.string(),
  outputDescription: z.string(),
  timeLimit: z.number().min(0.1, { error: "0.1s 이상" }).max(60, { error: "60s 이하" }),
  memoryLimit: z.number().min(1, { error: "1MB 이상" }).max(1024, { error: "1024MB 이하" }),
  difficulty: z.enum(["BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND"]),
  isPublic: z.boolean(),
});

type FormValues = z.infer<typeof schema>;

const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: "BRONZE", label: "브론즈" },
  { value: "SILVER", label: "실버" },
  { value: "GOLD", label: "골드" },
  { value: "PLATINUM", label: "플래티넘" },
  { value: "DIAMOND", label: "다이아" },
];

export default function EditProblemPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  const problem = useQuery({
    queryKey: ["problem", id],
    queryFn: () => problemsApi.detail(id),
    enabled: !Number.isNaN(id),
  });

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: problem.data
      ? {
          title: problem.data.title,
          description: problem.data.description,
          inputDescription: problem.data.inputDescription ?? "",
          outputDescription: problem.data.outputDescription ?? "",
          timeLimit: problem.data.timeLimit / 1000,
          memoryLimit: problem.data.memoryLimit / 1024,
          difficulty: problem.data.difficulty,
          isPublic: problem.data.isPublic,
        }
      : undefined,
  });

  const mutation = useMutation({
    mutationFn: (body: UpdateProblemRequest) => problemsApi.update(id, body),
    onSuccess: () => {
      toast.success("문제 수정 완료");
      qc.invalidateQueries({ queryKey: ["problem", id] });
      qc.invalidateQueries({ queryKey: ["problems"] });
      router.push(`/problems/${id}`);
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("수정 실패");
    },
  });

  if (!user || user.role !== "ADMIN") return null;
  if (problem.isLoading)
    return (
      <main className="max-w-6xl mx-auto p-6 text-muted-foreground">
        불러오는 중...
      </main>
    );
  if (problem.isError || !problem.data)
    return (
      <main className="max-w-6xl mx-auto p-6 text-destructive">
        문제를 찾을 수 없습니다
      </main>
    );

  return (
    <main className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-semibold mb-6">
        문제 수정 — #{problem.data.id}
      </h1>

      <form
        onSubmit={form.handleSubmit((v) =>
          mutation.mutate({
            ...v,
            timeLimit: Math.round(v.timeLimit * 1000),
            memoryLimit: Math.round(v.memoryLimit * 1024),
          }),
        )}
        className="space-y-6"
      >
        <Card>
          <CardHeader>
            <CardTitle className="text-base">기본 정보</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="title">제목</Label>
              <Input id="title" {...form.register("title")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">
                문제 설명{" "}
                <span className="text-xs text-muted-foreground font-normal">
                  (Markdown · GFM · KaTeX 지원)
                </span>
              </Label>
              <Controller
                control={form.control}
                name="description"
                render={({ field }) => (
                  <MarkdownEditor
                    id="description"
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    name={field.name}
                    minHeight={320}
                  />
                )}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="inputDescription">입력 설명</Label>
              <Controller
                control={form.control}
                name="inputDescription"
                render={({ field }) => (
                  <MarkdownEditor
                    id="inputDescription"
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    name={field.name}
                    minHeight={180}
                  />
                )}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="outputDescription">출력 설명</Label>
              <Controller
                control={form.control}
                name="outputDescription"
                render={({ field }) => (
                  <MarkdownEditor
                    id="outputDescription"
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    name={field.name}
                    minHeight={180}
                  />
                )}
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">제약 / 메타</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="timeLimit">시간 제한 (초)</Label>
              <Input
                id="timeLimit"
                type="number"
                step="0.1"
                min="0.1"
                max="60"
                {...form.register("timeLimit", { valueAsNumber: true })}
              />
              {form.formState.errors.timeLimit && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.timeLimit.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="memoryLimit">메모리 제한 (MB)</Label>
              <Input
                id="memoryLimit"
                type="number"
                step="1"
                min="1"
                max="1024"
                {...form.register("memoryLimit", { valueAsNumber: true })}
              />
              {form.formState.errors.memoryLimit && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.memoryLimit.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="difficulty">난이도</Label>
              <select
                id="difficulty"
                {...form.register("difficulty")}
                className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              >
                {DIFFICULTIES.map((d) => (
                  <option key={d.value} value={d.value}>
                    {d.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex items-end gap-2 pb-2">
              <input
                type="checkbox"
                id="isPublic"
                {...form.register("isPublic")}
                className="size-4 rounded border-input"
              />
              <Label htmlFor="isPublic" className="cursor-pointer">
                공개 문제
              </Label>
            </div>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.back()}
          >
            취소
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? "저장 중..." : "저장"}
          </Button>
        </div>
      </form>
    </main>
  );
}
