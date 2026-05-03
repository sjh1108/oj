"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { problemsApi } from "@/lib/problems-api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { TestCaseRequest, TestCaseResponse } from "@/types/api";

interface DraftTC {
  id?: number;
  input: string;
  expectedOutput: string;
  orderIndex: number;
  isSample: boolean;
  dirty: boolean;
}

const fromServer = (tc: TestCaseResponse): DraftTC => ({
  id: tc.id,
  input: tc.input,
  expectedOutput: tc.expectedOutput,
  orderIndex: tc.orderIndex,
  isSample: tc.isSample,
  dirty: false,
});

export default function TestCaseManagementPage() {
  const params = useParams<{ id: string }>();
  const problemId = Number(params.id);
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();

  const [drafts, setDrafts] = useState<DraftTC[]>([]);

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

  useEffect(() => {
    if (tcs.data) {
      setDrafts(tcs.data.map(fromServer));
    }
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

  const setField = <K extends keyof DraftTC>(
    idx: number,
    key: K,
    value: DraftTC[K],
  ) => {
    setDrafts((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], [key]: value, dirty: true };
      return next;
    });
  };

  const addLocal = () => {
    setDrafts((prev) => [
      ...prev,
      {
        input: "",
        expectedOutput: "",
        orderIndex: prev.length,
        isSample: false,
        dirty: true,
      },
    ]);
  };

  const removeLocal = (idx: number) => {
    setDrafts((prev) => prev.filter((_, i) => i !== idx));
  };

  const saveOne = async (draft: DraftTC) => {
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
    const dirty = drafts.filter((d) => d.dirty);
    if (dirty.length === 0) {
      toast.info("변경사항이 없습니다");
      return;
    }
    try {
      for (const d of dirty) {
        await saveOne(d);
      }
      toast.success(`저장 완료 — ${dirty.length}건`);
    } catch {
      // mutation onError already shows toast
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

  const dirtyCount = drafts.filter((d) => d.dirty).length;
  const busy =
    addMutation.isPending ||
    updateMutation.isPending ||
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

      <div className="space-y-4">
        {drafts.map((d, idx) => (
          <Card
            key={d.id ?? `new-${idx}`}
            className={d.dirty ? "border-amber-500" : ""}
          >
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2">
                TC #{idx + 1}
                {d.id === undefined && (
                  <span className="text-xs text-muted-foreground">(신규)</span>
                )}
                {d.dirty && (
                  <span className="text-xs text-amber-600">● 미저장</span>
                )}
              </CardTitle>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={d.isSample}
                    onChange={(e) => setField(idx, "isSample", e.target.checked)}
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
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label className="text-xs">입력</Label>
                  <Textarea
                    rows={5}
                    value={d.input}
                    onChange={(e) => setField(idx, "input", e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label className="text-xs">예상 출력</Label>
                  <Textarea
                    rows={5}
                    value={d.expectedOutput}
                    onChange={(e) =>
                      setField(idx, "expectedOutput", e.target.value)
                    }
                  />
                </div>
              </div>
              <div className="space-y-2 max-w-32">
                <Label className="text-xs">순서</Label>
                <Input
                  type="number"
                  value={d.orderIndex}
                  onChange={(e) =>
                    setField(idx, "orderIndex", Number(e.target.value))
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