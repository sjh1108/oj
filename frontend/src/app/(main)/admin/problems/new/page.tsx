"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ApiError } from "@/lib/api";
import { problemsApi } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { CreateProblemRequest, Difficulty } from "@/types/api";

const tcSchema = z.object({
  input: z.string().min(1, { error: "입력 필수" }),
  expectedOutput: z.string().min(1, { error: "출력 필수" }),
  isSample: z.boolean(),
});

const schema = z.object({
  title: z
    .string()
    .min(1, { error: "제목을 입력하세요" })
    .max(200, { error: "200자 이하" }),
  description: z.string().min(1, { error: "설명을 입력하세요" }),
  inputDescription: z.string(),
  outputDescription: z.string(),
  timeLimit: z
    .number({ error: "숫자를 입력하세요" })
    .min(100, { error: "100ms 이상" })
    .max(60000, { error: "60000ms 이하" }),
  memoryLimit: z
    .number({ error: "숫자를 입력하세요" })
    .min(1024, { error: "1024KB 이상" })
    .max(1048576, { error: "1048576KB 이하" }),
  difficulty: z.enum(["BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND"]),
  isPublic: z.boolean(),
  testCases: z
    .array(tcSchema)
    .min(1, { error: "테스트케이스를 최소 1개 추가하세요" }),
});

type FormValues = z.infer<typeof schema>;

const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: "BRONZE", label: "브론즈" },
  { value: "SILVER", label: "실버" },
  { value: "GOLD", label: "골드" },
  { value: "PLATINUM", label: "플래티넘" },
  { value: "DIAMOND", label: "다이아" },
];

export default function NewProblemPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: "",
      description: "",
      inputDescription: "",
      outputDescription: "",
      timeLimit: 1000,
      memoryLimit: 262144,
      difficulty: "BRONZE",
      isPublic: true,
      testCases: [
        { input: "", expectedOutput: "", isSample: true },
      ],
    },
  });

  const tcs = useFieldArray({ control: form.control, name: "testCases" });

  const mutation = useMutation({
    mutationFn: (body: CreateProblemRequest) => problemsApi.create(body),
    onSuccess: (created) => {
      toast.success(`문제 #${created.id} 생성 완료`);
      router.push(`/problems/${created.id}`);
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        if (err.fieldErrors?.length) {
          for (const fe of err.fieldErrors) {
            toast.error(`${fe.field}: ${fe.message}`);
          }
        } else {
          toast.error(err.message);
        }
      } else {
        toast.error("문제 생성 실패");
      }
    },
  });

  const onSubmit = (values: FormValues) => {
    const payload: CreateProblemRequest = {
      ...values,
      testCases: values.testCases.map((tc, i) => ({
        ...tc,
        orderIndex: i,
      })),
    };
    mutation.mutate(payload);
  };

  if (!user || user.role !== "ADMIN") return null;

  return (
    <main className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-semibold mb-6">문제 출제</h1>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">기본 정보</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="title">제목</Label>
              <Input id="title" {...form.register("title")} />
              {form.formState.errors.title && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.title.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">문제 설명</Label>
              <Textarea
                id="description"
                rows={6}
                {...form.register("description")}
              />
              {form.formState.errors.description && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.description.message}
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="inputDescription">입력 설명 (선택)</Label>
                <Textarea
                  id="inputDescription"
                  rows={3}
                  {...form.register("inputDescription")}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="outputDescription">출력 설명 (선택)</Label>
                <Textarea
                  id="outputDescription"
                  rows={3}
                  {...form.register("outputDescription")}
                />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">제약 / 메타</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="timeLimit">시간 제한 (ms)</Label>
              <Input
                id="timeLimit"
                type="number"
                {...form.register("timeLimit", { valueAsNumber: true })}
              />
              {form.formState.errors.timeLimit && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.timeLimit.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="memoryLimit">메모리 제한 (KB)</Label>
              <Input
                id="memoryLimit"
                type="number"
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

        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-base">테스트케이스</CardTitle>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() =>
                tcs.append({ input: "", expectedOutput: "", isSample: false })
              }
            >
              + 추가
            </Button>
          </CardHeader>
          <CardContent className="space-y-4">
            {tcs.fields.map((field, idx) => (
              <div
                key={field.id}
                className="border rounded-lg p-4 space-y-3 relative"
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">TC #{idx + 1}</span>
                  <div className="flex items-center gap-3">
                    <label className="flex items-center gap-2 text-sm cursor-pointer">
                      <input
                        type="checkbox"
                        {...form.register(`testCases.${idx}.isSample`)}
                        className="size-4 rounded border-input"
                      />
                      샘플 (사용자 노출)
                    </label>
                    {tcs.fields.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => tcs.remove(idx)}
                      >
                        삭제
                      </Button>
                    )}
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-2">
                    <Label className="text-xs">입력</Label>
                    <Textarea
                      rows={4}
                      {...form.register(`testCases.${idx}.input`)}
                    />
                    {form.formState.errors.testCases?.[idx]?.input && (
                      <p className="text-sm text-destructive">
                        {form.formState.errors.testCases[idx]?.input?.message}
                      </p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label className="text-xs">예상 출력</Label>
                    <Textarea
                      rows={4}
                      {...form.register(`testCases.${idx}.expectedOutput`)}
                    />
                    {form.formState.errors.testCases?.[idx]?.expectedOutput && (
                      <p className="text-sm text-destructive">
                        {
                          form.formState.errors.testCases[idx]?.expectedOutput
                            ?.message
                        }
                      </p>
                    )}
                  </div>
                </div>
              </div>
            ))}
            {form.formState.errors.testCases?.root && (
              <p className="text-sm text-destructive">
                {form.formState.errors.testCases.root.message}
              </p>
            )}
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
            {mutation.isPending ? "생성 중..." : "문제 생성"}
          </Button>
        </div>
      </form>
    </main>
  );
}
