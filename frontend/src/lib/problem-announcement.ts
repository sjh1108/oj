/**
 * Default text of the "선정 문제 알림" posted to Discord after a bulk upload.
 *
 * Mirrors the message the admin used to write by hand: a role mention, the
 * week title, then the problems in upload order grouped three to a set as
 * Easy / Medium / Hard. It is only a starting point — the admin edits it
 * before sending.
 */

export interface AnnouncedProblem {
  id: number;
  title: string;
}

const LEVELS = ["Easy", "Medium", "Hard"] as const;
const SET_SIZE = LEVELS.length;

/** "9월 3주차" — weeks start on Monday; the week holding the 1st is week 1. */
export function weekOfMonthLabel(date: Date): string {
  const first = new Date(date.getFullYear(), date.getMonth(), 1);
  const offset = (first.getDay() + 6) % 7; // Monday = 0
  const week = Math.ceil((date.getDate() + offset) / 7);
  return `${date.getMonth() + 1}월 ${week}주차`;
}

export function buildAnnouncement(
  problems: AnnouncedProblem[],
  opts: { siteUrl: string; roleId?: string | null; now?: Date },
): string {
  const site = opts.siteUrl.replace(/\/$/, "");
  const lines: string[] = [];
  if (opts.roleId) lines.push(`<@&${opts.roleId}> `);
  lines.push(`# ${weekOfMonthLabel(opts.now ?? new Date())} 선정 문제 공유`);

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

/** Discord rejects messages longer than this. */
export const DISCORD_MESSAGE_LIMIT = 2000;
