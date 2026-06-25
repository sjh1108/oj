"use client";

import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { adminApi } from "@/lib/admin-api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AdminResetPasswordResponse } from "@/types/api";

export default function AdminUsersPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [result, setResult] = useState<AdminResetPasswordResponse | null>(null);

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  const mutation = useMutation({
    mutationFn: adminApi.resetPassword,
    onSuccess: (res) => {
      setResult(res);
      toast.success(`${res.username} 비밀번호를 재설정했습니다`);
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("재설정 실패");
    },
  });

  const handleSubmit = () => {
    const value = usernameOrEmail.trim();
    if (!value) {
      toast.error("아이디 또는 이메일을 입력하세요");
      return;
    }
    if (!confirm(`'${value}' 회원의 비밀번호를 임시값으로 재설정할까요?`)) return;
    setResult(null);
    mutation.mutate({ usernameOrEmail: value });
  };

  const copyTemp = async () => {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.temporaryPassword);
      toast.success("임시 비밀번호를 복사했습니다");
    } catch {
      toast.error("복사 실패 — 직접 선택해 복사하세요");
    }
  };

  if (!user || user.role !== "ADMIN") return null;

  return (
    <main className="max-w-2xl mx-auto p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">회원 비밀번호 재설정</h1>
        <p className="text-sm text-muted-foreground">
          비밀번호를 잊은 회원의 아이디 또는 이메일을 입력하면 임시 비밀번호를 생성합니다.
          생성된 값을 회원에게 전달하면 그 값으로 로그인할 수 있습니다.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">대상 회원</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="target">아이디 또는 이메일</Label>
            <Input
              id="target"
              value={usernameOrEmail}
              onChange={(e) => setUsernameOrEmail(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleSubmit();
              }}
              placeholder="예: alice 또는 alice@study.dev"
            />
          </div>
          <Button onClick={handleSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? "재설정 중..." : "비밀번호 재설정"}
          </Button>
        </CardContent>
      </Card>

      {result && (
        <Card className="border-emerald-500">
          <CardHeader>
            <CardTitle className="text-base text-emerald-600">
              임시 비밀번호 생성됨
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="text-sm text-muted-foreground">
              {result.username} ({result.email})
            </div>
            <div className="flex items-center gap-3">
              <code className="flex-1 rounded-md bg-muted px-3 py-2 font-mono text-lg tracking-wider">
                {result.temporaryPassword}
              </code>
              <Button variant="outline" onClick={copyTemp}>
                복사
              </Button>
            </div>
            <p className="text-sm text-amber-600">
              이 값은 지금만 표시됩니다. 회원에게 전달한 뒤 로그인하면 사용하세요.
            </p>
          </CardContent>
        </Card>
      )}
    </main>
  );
}
