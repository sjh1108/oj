"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import {
  Controller,
  useFieldArray,
  useForm,
  type UseFormReturn,
} from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ApiError } from "@/lib/api";
import { problemsApi } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { downloadTextFile } from "@/lib/download";
import { parseProblemFile, PROBLEM_TEMPLATE } from "@/lib/problem-file";
import { splitTags } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MarkdownEditor } from "@/components/markdown-editor";
import type { CreateProblemRequest, Difficulty } from "@/types/api";

const tcSchema = z.object({
  input: z.string().min(1, { error: "입력 필수" }),
  expectedOutput: z.string().min(1, { error: "출력 필수" }),
  isSample: z.boolean(),
});

const subtaskSchema = z.object({
  label: z.string(),
  points: z
    .number({ error: "숫자" })
    .min(0, { error: "0 이상" })
    .max(1000, { error: "1000 이하" }),
  testCases: z.array(tcSchema).min(1, { error: "TC를 최소 1개 추가하세요" }),
});

const schema = z
  .object({
    title: z
      .string()
      .min(1, { error: "제목을 입력하세요" })
      .max(200, { error: "200자 이하" }),
    description: z.string().min(1, { error: "설명을 입력하세요" }),
    inputDescription: z.string(),
    outputDescription: z.string(),
    timeLimit: z
      .number({ error: "숫자를 입력하세요" })
      .min(0.1, { error: "0.1s 이상" })
      .max(60, { error: "60s 이하" }),
    memoryLimit: z
      .number({ error: "숫자를 입력하세요" })
      .min(1, { error: "1MB 이상" })
      .max(1024, { error: "1024MB 이하" }),
    difficulty: z.enum(["BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND"]),
    tags: z.string(),
    isPublic: z.boolean(),
    useSubtasks: z.boolean(),
    testCases: z.array(tcSchema),
    subtasks: z.array(subtaskSchema),
  })
  .superRefine((v, ctx) => {
    if (v.useSubtasks) {
      if (v.subtasks.length < 1)
        ctx.addIssue({
          code: "custom",
          path: ["subtasks"],
          message: "서브태스크를 최소 1개 추가하세요",
        });
    } else if (v.testCases.length < 1) {
      ctx.addIssue({
        code: "custom",
        path: ["testCases"],
        message: "테스트케이스를 최소 1개 추가하세요",
      });
    }
  });

type FormValues = z.infer<typeof schema>;

const EMPTY_TC = { input: "", expectedOutput: "", isSample: false };
const FIRST_TC = { input: "", expectedOutput: "", isSample: true };

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
      timeLimit: 1,
      memoryLimit: 256,
      difficulty: "BRONZE",
      tags: "",
      isPublic: true,
      useSubtasks: false,
      testCases: [{ ...FIRST_TC }],
      subtasks: [],
    },
  });

  const tcs = useFieldArray({ control: form.control, name: "testCases" });
  const stcs = useFieldArray({ control: form.control, name: "subtasks" });
  const useSubtasks = form.watch("useSubtasks");

  const setUseSubtasks = (on: boolean) => {
    form.setValue("useSubtasks", on);
    if (on) {
      tcs.replace([]);
      if (form.getValues("subtasks").length === 0) {
        stcs.append({ label: "", points: 100, testCases: [{ ...FIRST_TC }] });
      }
    } else {
      stcs.replace([]);
      if (form.getValues("testCases").length === 0) {
        tcs.append({ ...FIRST_TC });
      }
    }
  };

  const mutation = useMutation({
    mutationFn: (body: CreateProblemRequest) => problemsApi.create(body),
    onSuccess: (created) => {
      toast.success(`문제 #${created.id} 생성 완료`);
      router.push(`/problems/${created.id}`);
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        if (err.fieldErrors?.length) {
          for (const fe of err.fieldErrors) toast.error(`${fe.field}: ${fe.message}`);
        } else {
          toast.error(err.message);
        }
      } else {
        toast.error("문제 생성 실패");
      }
    },
  });

  const loadFromFile = async (file: File) => {
    try {
      const parsed = parseProblemFile(await file.text());
      const common = {
        title: parsed.title,
        description: parsed.description,
        inputDescription: parsed.inputDescription,
        outputDescription: parsed.outputDescription,
        timeLimit: parsed.timeLimit,
        memoryLimit: parsed.memoryLimit,
        difficulty: parsed.difficulty,
        tags: parsed.tags.join(", "),
        isPublic: parsed.isPublic,
      };
      if (parsed.subtasks && parsed.subtasks.length > 0) {
        form.reset({
          ...common,
          useSubtasks: true,
          testCases: [],
          subtasks: parsed.subtasks.map((st) => ({
            label: st.label,
            points: st.points,
            testCases: st.testCases.map((tc) => ({
              input: tc.input,
              expectedOutput: tc.expectedOutput,
              isSample: tc.isSample,
            })),
          })),
        });
        toast.success(`파일을 불러왔습니다 · 서브태스크 ${parsed.subtasks.length}개`);
      } else {
        form.reset({
          ...common,
          useSubtasks: false,
          subtasks: [],
          testCases: parsed.testCases.length
            ? parsed.testCases.map((tc) => ({
                input: tc.input,
                expectedOutput: tc.expectedOutput,
                isSample: tc.isSample,
              }))
            : [{ ...FIRST_TC }],
        });
        toast.success(`파일을 불러왔습니다 · 테스트케이스 ${parsed.testCases.length}개`);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "파일 파싱에 실패했습니다");
    }
  };

  const onSubmit = (values: FormValues) => {
    const base = {
      title: values.title,
      description: values.description,
      inputDescription: values.inputDescription,
      outputDescription: values.outputDescription,
      timeLimit: Math.round(values.timeLimit * 1000),
      memoryLimit: Math.round(values.memoryLimit * 1024),
      difficulty: values.difficulty,
      tags: splitTags(values.tags),
      isPublic: values.isPublic,
    };
    let payload: CreateProblemRequest;
    if (values.useSubtasks) {
      let order = 0;
      payload = {
        ...base,
        subtasks: values.subtasks.map((st, i) => ({
          label: st.label?.trim() || `서브태스크 ${i + 1}`,
          points: st.points,
          testCases: st.testCases.map((tc) => ({ ...tc, orderIndex: order++ })),
        })),
      };
    } else {
      payload = {
        ...base,
        testCases: values.testCases.map((tc, i) => ({ ...tc, orderIndex: i })),
      };
    }
    mutation.mutate(payload);
  };

  if (!user || user.role !== "ADMIN") return null;

  const subtaskErr = form.formState.errors.subtasks;
  const subtaskMsg =
    typeof subtaskErr?.message === "string"
      ? subtaskErr.message
      : typeof subtaskErr?.root?.message === "string"
        ? subtaskErr.root.message
        : null;

  return (
    <main className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-semibold mb-6">문제 출제</h1>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-base">파일로 채우기 (선택)</CardTitle>
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
                render={<Link href="/admin/problems/import" />}
              >
                여러 파일 한번에 업로드
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-sm text-muted-foreground">
              <code className="text-xs">.md</code> 문제 파일을 올리면 제목·설명·제약·테스트케이스가
              아래 폼에 자동으로 채워집니다. <code className="text-xs">{"<!-- @subtasks -->"}</code> 섹션이
              있으면 서브태스크로 불러옵니다. (업로드 후 폼에서 수정 가능)
            </p>
            <Input
              type="file"
              accept=".md,.markdown,.txt,text/markdown,text/plain"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) loadFromFile(file);
                e.target.value = "";
              }}
            />
          </CardContent>
        </Card>

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
                    value={field.value}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    name={field.name}
                    minHeight={320}
                    placeholder={"# 제목\n\n문제 본문...\n\n인라인 수식: $O(n \\log n)$"}
                  />
                )}
              />
              {form.formState.errors.description && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.description.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="inputDescription">입력 설명 (선택)</Label>
              <Controller
                control={form.control}
                name="inputDescription"
                render={({ field }) => (
                  <MarkdownEditor
                    id="inputDescription"
                    value={field.value}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    name={field.name}
                    minHeight={180}
                  />
                )}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="outputDescription">출력 설명 (선택)</Label>
              <Controller
                control={form.control}
                name="outputDescription"
                render={({ field }) => (
                  <MarkdownEditor
                    id="outputDescription"
                    value={field.value}
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
            <div className="space-y-2 col-span-2">
              <Label htmlFor="tags">태그 (선택, 쉼표로 구분)</Label>
              <Input
                id="tags"
                placeholder="예: DP, 그래프, 구현"
                {...form.register("tags")}
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div className="flex items-center gap-3 flex-wrap">
              <CardTitle className="text-base">테스트케이스</CardTitle>
              <label className="flex items-center gap-2 text-sm cursor-pointer text-muted-foreground">
                <input
                  type="checkbox"
                  checked={useSubtasks}
                  onChange={(e) => setUseSubtasks(e.target.checked)}
                  className="size-4 rounded border-input"
                />
                서브태스크(부분 점수) 사용
              </label>
            </div>
            {useSubtasks ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  stcs.append({
                    label: "",
                    points: 0,
                    testCases: [{ ...EMPTY_TC }],
                  })
                }
              >
                + 서브태스크
              </Button>
            ) : (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => tcs.append({ ...EMPTY_TC })}
              >
                + 추가
              </Button>
            )}
          </CardHeader>
          <CardContent className="space-y-4">
            {useSubtasks ? (
              <>
                <p className="text-xs text-muted-foreground">
                  각 서브태스크는 <b>전부 통과해야</b> 배점을 얻습니다(부분 점수). 배점 합은 보통 100.
                </p>
                {stcs.fields.map((field, sIdx) => (
                  <SubtaskGroup
                    key={field.id}
                    form={form}
                    sIdx={sIdx}
                    removable={stcs.fields.length > 1}
                    onRemove={() => stcs.remove(sIdx)}
                  />
                ))}
                {subtaskMsg && (
                  <p className="text-sm text-destructive">{subtaskMsg}</p>
                )}
              </>
            ) : (
              <>
                {tcs.fields.map((field, idx) => (
                  <div key={field.id} className="border rounded-lg p-4 space-y-3">
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
                        <Textarea rows={4} {...form.register(`testCases.${idx}.input`)} />
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
                            {form.formState.errors.testCases[idx]?.expectedOutput?.message}
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
                <div className="flex justify-center pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => tcs.append({ ...EMPTY_TC })}
                  >
                    + TC 추가
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => router.back()}>
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

function SubtaskGroup({
  form,
  sIdx,
  removable,
  onRemove,
}: {
  form: UseFormReturn<FormValues>;
  sIdx: number;
  removable: boolean;
  onRemove: () => void;
}) {
  const tcs = useFieldArray({
    control: form.control,
    name: `subtasks.${sIdx}.testCases`,
  });
  const errors = form.formState.errors;
  const pointsErr = errors.subtasks?.[sIdx]?.points?.message;

  return (
    <div className="border rounded-lg p-4 space-y-3 bg-muted/30">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-semibold">서브태스크 {sIdx + 1}</span>
          <Input
            className="h-8 w-44"
            placeholder="라벨 (선택)"
            {...form.register(`subtasks.${sIdx}.label`)}
          />
          <div className="flex items-center gap-1">
            <Input
              className="h-8 w-20"
              type="number"
              min="0"
              {...form.register(`subtasks.${sIdx}.points`, { valueAsNumber: true })}
            />
            <span className="text-sm text-muted-foreground">점</span>
          </div>
        </div>
        {removable && (
          <Button type="button" variant="ghost" size="sm" onClick={onRemove}>
            그룹 삭제
          </Button>
        )}
      </div>
      {pointsErr && <p className="text-sm text-destructive">{pointsErr}</p>}

      {tcs.fields.map((field, idx) => (
        <div key={field.id} className="border rounded-md p-3 space-y-2 bg-background">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium">TC #{idx + 1}</span>
            <div className="flex items-center gap-3">
              <label className="flex items-center gap-2 text-xs cursor-pointer">
                <input
                  type="checkbox"
                  {...form.register(`subtasks.${sIdx}.testCases.${idx}.isSample`)}
                  className="size-4 rounded border-input"
                />
                샘플
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
            <div className="space-y-1">
              <Textarea
                rows={3}
                placeholder="입력"
                {...form.register(`subtasks.${sIdx}.testCases.${idx}.input`)}
              />
              {errors.subtasks?.[sIdx]?.testCases?.[idx]?.input && (
                <p className="text-sm text-destructive">
                  {errors.subtasks[sIdx]?.testCases?.[idx]?.input?.message}
                </p>
              )}
            </div>
            <div className="space-y-1">
              <Textarea
                rows={3}
                placeholder="예상 출력"
                {...form.register(`subtasks.${sIdx}.testCases.${idx}.expectedOutput`)}
              />
              {errors.subtasks?.[sIdx]?.testCases?.[idx]?.expectedOutput && (
                <p className="text-sm text-destructive">
                  {errors.subtasks[sIdx]?.testCases?.[idx]?.expectedOutput?.message}
                </p>
              )}
            </div>
          </div>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => tcs.append({ input: "", expectedOutput: "", isSample: false })}
      >
        + TC 추가
      </Button>
    </div>
  );
}
