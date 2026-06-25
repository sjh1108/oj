"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { authApi } from "@/lib/auth-api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { DiscordLinkCodeResponse } from "@/types/api";

const schema = z
  .object({
    currentPassword: z.string().min(1, { error: "현재 비밀번호를 입력하세요" }),
    newPassword: z
      .string()
      .min(8, { error: "비밀번호는 8자 이상이어야 합니다" })
      .max(100, { error: "비밀번호는 100자 이하여야 합니다" }),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    error: "새 비밀번호가 일치하지 않습니다",
    path: ["confirmPassword"],
  });

type FormValues = z.infer<typeof schema>;

export default function AccountPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (user === null) {
      router.replace("/login");
    }
  }, [user, router]);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const mutation = useMutation({
    mutationFn: authApi.changePassword,
    onSuccess: () => {
      toast.success("비밀번호를 변경했습니다");
      form.reset();
    },
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("비밀번호 변경 실패");
    },
  });

  const [linkCode, setLinkCode] = useState<DiscordLinkCodeResponse | null>(null);
  const linkMutation = useMutation({
    mutationFn: authApi.discordLinkCode,
    onSuccess: (res) => setLinkCode(res),
    onError: (err) => {
      if (err instanceof ApiError) toast.error(err.message);
      else toast.error("연동 코드 발급 실패");
    },
  });

  return (
    <main className="max-w-md mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-semibold">내 계정</h1>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">비밀번호 변경</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={form.handleSubmit((v) =>
              mutation.mutate({
                currentPassword: v.currentPassword,
                newPassword: v.newPassword,
              }),
            )}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="currentPassword">현재 비밀번호</Label>
              <Input
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                {...form.register("currentPassword")}
              />
              {form.formState.errors.currentPassword && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.currentPassword.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="newPassword">새 비밀번호</Label>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                {...form.register("newPassword")}
              />
              {form.formState.errors.newPassword && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.newPassword.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">새 비밀번호 확인</Label>
              <Input
                id="confirmPassword"
                type="password"
                autoComplete="new-password"
                {...form.register("confirmPassword")}
              />
              {form.formState.errors.confirmPassword && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.confirmPassword.message}
                </p>
              )}
            </div>
            <Button
              type="submit"
              className="w-full"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? "변경 중..." : "비밀번호 변경"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">디스코드 연동</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            연동해두면 비밀번호를 잊었을 때 디스코드에서{" "}
            <code className="text-xs">/비밀번호분실</code> 로 임시 비밀번호를
            받을 수 있습니다. 아래 코드를 받아 디스코드에서{" "}
            <code className="text-xs">/연동 &lt;코드&gt;</code> 를 입력하세요.
          </p>
          <Button
            variant="outline"
            onClick={() => linkMutation.mutate()}
            disabled={linkMutation.isPending}
          >
            {linkMutation.isPending ? "발급 중..." : "연동 코드 발급"}
          </Button>
          {linkCode && (
            <div className="rounded-md border p-3 space-y-1">
              <code className="font-mono text-2xl tracking-[0.3em]">
                {linkCode.code}
              </code>
              <p className="text-sm text-muted-foreground">
                디스코드에서{" "}
                <code className="text-xs">/연동 {linkCode.code}</code> 입력 ·{" "}
                {Math.round(linkCode.expiresInSeconds / 60)}분 내 유효
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    </main>
  );
}
