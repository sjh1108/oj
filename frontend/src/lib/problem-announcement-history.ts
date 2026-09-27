/**
 * Which weeks this admin has already announced, kept in localStorage.
 *
 * It only seeds the week dropdown's default (one past the month's highest
 * week), so it belongs to the browser rather than the account: on another
 * device the default simply starts at 1 and the admin picks the week. Storage
 * can be unavailable (private mode, blocked site data) — every access is
 * guarded and the card works without it.
 */

export interface AnnouncementRecord {
  id: string;
  year: number;
  month: number;
  weekOfMonth: number;
  sentAt: string;
}

const KEY = "algoj-problem-announcements";
const KEEP = 20;

export function loadAnnouncements(): AnnouncementRecord[] {
  try {
    const raw = localStorage.getItem(KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? (parsed as AnnouncementRecord[]) : [];
  } catch {
    return [];
  }
}

function save(records: AnnouncementRecord[]): AnnouncementRecord[] {
  const sorted = [...records]
    .sort(
      (a, b) =>
        b.year - a.year ||
        b.month - a.month ||
        b.weekOfMonth - a.weekOfMonth ||
        b.sentAt.localeCompare(a.sentAt),
    )
    .slice(0, KEEP);
  try {
    localStorage.setItem(KEY, JSON.stringify(sorted));
  } catch {
    // Storage unavailable — the list just won't survive a reload.
  }
  return sorted;
}

export function recordAnnouncement(
  year: number,
  month: number,
  weekOfMonth: number,
): AnnouncementRecord[] {
  return save([
    ...loadAnnouncements(),
    {
      // Unique even for two sends in the same millisecond — it keys delete.
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
      year,
      month,
      weekOfMonth,
      sentAt: new Date().toISOString(),
    },
  ]);
}

export function removeAnnouncement(id: string): AnnouncementRecord[] {
  return save(loadAnnouncements().filter((r) => r.id !== id));
}

/** One past the highest week already announced for the month (1 if none). */
export function suggestedWeek(
  records: AnnouncementRecord[],
  year: number,
  month: number,
): number {
  const max = records
    .filter((r) => r.year === year && r.month === month)
    .reduce((m, r) => Math.max(m, r.weekOfMonth), 0);
  return max + 1;
}
