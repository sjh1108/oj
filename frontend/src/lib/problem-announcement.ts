/**
 * Default text of the "선정 문제 알림" posted to Discord after a bulk upload.
 *
 * Mirrors the message the admin used to write by hand: a role mention, the
 * week title, then the problems in upload order grouped three to a set as
 * Easy / Medium / Hard. It is only a starting point — the admin edits it
 * before sending.
 *
 * The week ("10월 1주차") is not a calendar week: it is the nth study actually
 * held that month, which the server counts from past notices.
 */

export interface AnnouncedProblem {
  id: number;
  title: string;
}

const LEVELS = ["Easy", "Medium", "Hard"] as const;
const SET_SIZE = LEVELS.length;

export function weekLabel(month: number, weekOfMonth: number): string {
  return `${month}월 ${weekOfMonth}주차`;
}

function headingLine(label: string): string {
  return `# ${label} 선정 문제 공유`;
}

const HEADING = /^# .*선정 문제 공유$/m;

export function buildAnnouncement(
  problems: AnnouncedProblem[],
  opts: { siteUrl: string; weekLabel: string; roleId?: string | null },
): string {
  const site = opts.siteUrl.replace(/\/$/, "");
  const lines: string[] = [];
  if (opts.roleId) lines.push(`<@&${opts.roleId}> `);
  lines.push(headingLine(opts.weekLabel));

  for (let i = 0; i < problems.length; i += SET_SIZE) {
    lines.push(`## Set ${i / SET_SIZE + 1}`);
    problems.slice(i, i + SET_SIZE).forEach((p, j) => {
      lines.push(`### ${LEVELS[j]}`);
      lines.push(`* ${p.title}`);
      lines.push(`${site}/problems/${p.id}`);
    });
    lines.push("");
  }
  return lines.join("\n").trimEnd();
}

/** Swaps the week in an already edited draft, leaving the rest untouched. */
export function replaceWeekLabel(draft: string, label: string): string {
  return HEADING.test(draft) ? draft.replace(HEADING, headingLine(label)) : draft;
}

/** yyyy-MM-dd of the next Monday after today (local time) — the usual study day. */
export function nextMondayIso(today = new Date()): string {
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  d.setDate(d.getDate() + (((8 - d.getDay()) % 7) || 7));
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** Discord rejects messages longer than this. */
export const DISCORD_MESSAGE_LIMIT = 2000;
