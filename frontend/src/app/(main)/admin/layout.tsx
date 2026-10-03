"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { useAuthStore } from "@/lib/auth-store";

// Sends non-admins away from every /admin page. The API enforces ADMIN by
// itself; this only keeps them off a page whose requests would all fail.
export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (user && user.role !== "ADMIN") {
      toast.error("관리자만 접근할 수 있습니다");
      router.replace("/problems");
    }
  }, [user, router]);

  return <>{children}</>;
}
