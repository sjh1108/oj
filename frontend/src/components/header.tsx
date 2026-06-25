"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";

export function Header() {
  const router = useRouter();
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
            <span className="text-sm text-muted-foreground">{user.username}</span>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              logout();
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