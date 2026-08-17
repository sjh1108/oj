"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { authApi } from "@/lib/auth-api";
import { useAuthStore } from "@/lib/auth-store";
import type { UserPreferences } from "@/types/api";

// 계정에 저장되는 개인 설정 — 기기/브라우저가 달라도 따라온다.
// (기기에 매인 설정인 에디터 글꼴·글씨 크기는 editor-settings.ts의 localStorage 쪽.)
export const DEFAULT_PREFERENCES: UserPreferences = {
  // 태그는 풀이 방향을 알려주는 스포일러라 기본은 숨김.
  showTags: false,
};

const QUERY_KEY = ["preferences"] as const;

export function usePreferences(): {
  preferences: UserPreferences;
  updatePreferences: (patch: Partial<UserPreferences>) => void;
} {
  const qc = useQueryClient();
  const accessToken = useAuthStore((s) => s.accessToken);

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: authApi.preferences,
    enabled: Boolean(accessToken),
    staleTime: 5 * 60 * 1000,
  });

  const preferences = query.data ?? DEFAULT_PREFERENCES;

  // 저장은 낙관적 반영 — 토글은 즉시 움직이고, 실패하면 이전 값으로 되돌린다.
  const mutation = useMutation({
    mutationFn: authApi.updatePreferences,
    onMutate: async (next) => {
      await qc.cancelQueries({ queryKey: QUERY_KEY });
      const previous = qc.getQueryData<UserPreferences>(QUERY_KEY);
      qc.setQueryData<UserPreferences>(QUERY_KEY, next);
      return { previous };
    },
    onError: (_err, _next, context) => {
      qc.setQueryData<UserPreferences>(QUERY_KEY, context?.previous);
      toast.error("설정을 저장하지 못했습니다");
    },
    onSuccess: (saved) => qc.setQueryData<UserPreferences>(QUERY_KEY, saved),
  });

  return {
    preferences,
    updatePreferences: (patch) => mutation.mutate({ ...preferences, ...patch }),
  };
}
