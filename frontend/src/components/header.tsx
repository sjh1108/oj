"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";

export function Header() {
  const router = useRouter();
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  return (
    <header className="border-b">
      <div className="max-w-6xl mx-auto px-6 h-14 flex items-center justify-between">
        <Link href="/problems" className="font-semibold">
          algoj
        </Link>
        <nav className="flex items-center gap-3">
          <Link
            href="/problems"
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            문제
          </Link>
          <Link
            href="/submissions"
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            채점 현황
          </Link>
          <Link
            href="/submissions/me"
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            내 제출
          </Link>
          {user?.role === "ADMIN" && (
            <Link
              href="/admin/problems/new"
              className="text-sm text-muted-foreground hover:text-foreground"
            >
              문제 출제
            </Link>
          )}
          {user?.role === "ADMIN" && (
            <Link
              href="/admin/users"
              className="text-sm text-muted-foreground hover:text-foreground"
            >
              회원 관리
            </Link>
          )}
          {user && (
            <Link
              href="/account"
              className="text-sm text-muted-foreground hover:text-foreground"
            >
              {user.username}
            </Link>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              logout();
              // 캐시에는 계정 설정·푼 문제 등 개인 데이터가 남아 있다 —
              // 공용 PC에서 다음 사람에게 보이지 않도록 비운다.
              qc.clear();
              router.push("/login");
            }}
          >
            로그아웃
          </Button>
        </nav>
      </div>
    </header>
  );
}