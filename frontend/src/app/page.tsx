"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { api } from "@/lib/api";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface Health {
  status: string;
  timestamp: string;
}

export default function HomePage() {
  const health = useQuery({
    queryKey: ["health"],
    queryFn: () => api<Health>("/api/health", { auth: false }),
  });

  return (
    <main className="min-h-screen flex items-center justify-center p-8">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">algoj</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            알고리즘 스터디용 온라인 저지
          </p>
          <div className="text-sm">
            <span className="text-muted-foreground">백엔드 상태: </span>
            {health.isLoading && <span>확인 중...</span>}
            {health.isError && (
              <span className="text-destructive">연결 실패</span>
            )}
            {health.data && (
              <span className="text-green-500">{health.data.status}</span>
            )}
          </div>
          <div className="flex gap-2">
            <Link
              href="/login"
              className={cn(buttonVariants({ size: "lg" }), "flex-1")}
            >
              로그인
            </Link>
            <Link
              href="/signup"
              className={cn(
                buttonVariants({ variant: "outline", size: "lg" }),
                "flex-1",
              )}
            >
              회원가입
            </Link>
          </div>
        </CardContent>
      </Card>
    </main>
  );
}