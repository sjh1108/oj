import { useQuery } from "@tanstack/react-query";

import { authApi } from "@/lib/auth-api";
import { useAuthStore } from "@/lib/auth-store";

/** 로그인한 유저가 맞은 문제 id 집합. 비로그인이면 빈 집합. */
export function useSolvedProblems(): Set<number> {
  const user = useAuthStore((s) => s.user);
  const solved = useQuery({
    queryKey: ["my-solved-problems"],
    queryFn: authApi.mySolvedProblems,
    enabled: !!user,
    staleTime: 60_000,
  });
  return new Set(user ? solved.data : []);
}
