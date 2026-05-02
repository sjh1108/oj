"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";

import { authApi } from "@/lib/auth-api";
import { ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  username: z
    .string()
    .min(3, "3자 이상")
    .max(20, "20자 이하")
    .regex(/^[a-zA-Z0-9_]+$/, "영문/숫자/언더스코어만"),
  email: z.string().email("올바른 이메일 형식이 아닙니다"),
  password: z.string().min(8, "8자 이상"),
});

type FormValues = z.infer<typeof schema>;

export default function SignupPage() {
  const router = useRouter();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: "", email: "", password: "" },
  });

  const mutation = useMutation({
    mutationFn: authApi.signup,
    onSuccess: () => {
      toast.success("회원가입 완료. 로그인하세요.");
      router.push("/login");
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        if (err.fieldErrors?.length) {
          for (const fe of err.fieldErrors) {
            form.setError(fe.field as keyof FormValues, { message: fe.message });
          }
        } else {
          toast.error(err.message);
        }
      } else {
        toast.error("회원가입 실패");
      }
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>회원가입</CardTitle>
        <CardDescription>algoj 계정을 만드세요</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
          className="space-y-4"
        >
          <div className="space-y-2">
            <Label htmlFor="username">아이디</Label>
            <Input id="username" autoComplete="username" {...form.register("username")} />
            {form.formState.errors.username && (
              <p className="text-sm text-destructive">
                {form.formState.errors.username.message}
              </p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              {...form.register("email")}
            />
            {form.formState.errors.email && (
              <p className="text-sm text-destructive">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">비밀번호</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              {...form.register("password")}
            />
            {form.formState.errors.password && (
              <p className="text-sm text-destructive">
                {form.formState.errors.password.message}
              </p>
            )}
          </div>
          <Button type="submit" className="w-full" disabled={mutation.isPending}>
            {mutation.isPending ? "가입 중..." : "회원가입"}
          </Button>
          <p className="text-sm text-center text-muted-foreground">
            이미 계정이 있나요?{" "}
            <Link href="/login" className="text-primary hover:underline">
              로그인
            </Link>
          </p>
        </form>
      </CardContent>
    </Card>
  );
}