import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** "DP, 그래프" → ["DP", "그래프"] (max 10, matching the API limit). */
export function splitTags(raw: string): string[] {
  return raw
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean)
    .slice(0, 10);
}

/**
 * 서버의 LocalDateTime("2026-10-05T14:03:27.123456")을 "2026-10-05 14:03:27"로.
 * 서버가 Asia/Seoul 벽시계 시각을 타임존 없이 보내므로 Date로 변환하지 않고
 * 문자열 그대로 자른다 — 브라우저 타임존이 달라도 같은 시각이 보인다.
 */
export function formatDateTime(raw: string): string {
  return raw.replace("T", " ").slice(0, 19);
}
