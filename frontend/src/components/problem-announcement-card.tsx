"use client";

import { MegaphoneIcon, SendIcon, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import {
  problemAnnouncementApi,
  type ProblemAnnouncementConfig,
} from "@/lib/admin-api";
import {
  loadAnnouncements,
  recordAnnouncement,
  removeAnnouncement,
  suggestedWeek,
  type AnnouncementRecord,
} from "@/lib/problem-announcement-history";
import {
  buildAnnouncement,
  defaultAnnounceMonth,
  DISCORD_MESSAGE_LIMIT,
  replaceWeekLabel,
  weekLabel,
  yearForMonth,
  type AnnouncedProblem,
} from "@/lib/problem-announcement";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
const WEEKS = [1, 2, 3, 4, 5];
const SELECT_CLASS =
  "h-8 rounded-lg border border-input bg-transparent px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30";

const errorMessage = (err: unknown, fallback: string) =>
  err instanceof ApiError ? err.message : fallback;

/**
 * "선정 문제 알림" — shown on the bulk upload page once problems have landed.
 *
 * The admin picks the month and week from two dropdowns. Picking a month
 * fills in its default week — one past the highest week already sent that
 * month, so weeks the study skips (no notice sent) are not counted. A test
 * send is not recorded.
 */
export function ProblemAnnouncementCard({
  problems,
}: {
  problems: AnnouncedProblem[];
}) {
  const [config, setConfig] = useState<ProblemAnnouncementConfig | null>(null);
  const [year, setYear] = useState(() => defaultAnnounceMonth().year);
  const [month, setMonth] = useState(() => defaultAnnounceMonth().month);
  const [week, setWeek] = useState(1);
  const [draft, setDraft] = useState<string | null>(null);
  const [testOnly, setTestOnly] = useState(false);
  const [sending, setSending] = useState(false);
  const [records, setRecords] = useState<AnnouncementRecord[]>([]);

  useEffect(() => {
    problemAnnouncementApi
      .config()
      .then(setConfig)
      .catch(() => setConfig({ enabled: false, roleId: null }));
    setRecords(loadAnnouncements());
  }, []);

  // Picking a month (or a new record landing) resets the week to that
  // month's default; the admin can still change it afterwards.
  useEffect(() => {
    setWeek(suggestedWeek(records, year, month));
  }, [year, month, records]);

  const label = weekLabel(month, week);

  // Keep an open draft's title in step with the dropdowns, without discarding
  // the admin's other edits.
  useEffect(() => {
    setDraft((d) => (d == null ? d : replaceWeekLabel(d, label)));
  }, [label]);

  const pickMonth = (m: number) => {
    setYear(yearForMonth(m));
    setMonth(m);
  };

  if (problems.length === 0 || config == null) return null;

  const openDraft = () =>
    setDraft(
      buildAnnouncement(problems, {
        siteUrl: window.location.origin,
        weekLabel: label,
        roleId: config.roleId,
      }),
    );

  const send = async () => {
    if (!draft?.trim()) return;
    setSending(true);
    try {
      await problemAnnouncementApi.send(draft);
      toast.success(
        testOnly
          ? "테스트로 보냈습니다 (기록하지 않음)"
          : "디스코드에 선정 문제 알림을 보냈습니다",
      );
      setDraft(null);
      if (!testOnly) setRecords(recordAnnouncement(year, month, week));
    } catch (err) {
      toast.error(errorMessage(err, "알림을 보내지 못했습니다"));
    } finally {
      setSending(false);
    }
  };

  const removeRecord = (r: AnnouncementRecord) => {
    if (
      window.confirm(
        `${r.year}년 ${weekLabel(r.month, r.weekOfMonth)} 기록을 지울까요? 디스코드 메시지는 지워지지 않고, 다음 주차 기본값 계산에서만 빠집니다.`,
      )
    )
      setRecords(removeAnnouncement(r.id));
  };

  const tooLong = (draft?.length ?? 0) > DISCORD_MESSAGE_LIMIT;

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <CardTitle className="text-base">
          선정 문제 알림
          <span className="block text-xs font-normal text-muted-foreground mt-0.5">
            등록된 {problems.length}개 문제를 디스코드 채널에 공유합니다
          </span>
        </CardTitle>
        {draft == null && (
          <Button
            type="button"
            size="sm"
            disabled={!config.enabled}
            onClick={openDraft}
          >
            <MegaphoneIcon className="size-4 mr-1" />
            알림 작성
          </Button>
        )}
      </CardHeader>

      <CardContent className="pt-0 space-y-3">
        {!config.enabled && (
          <p className="text-xs text-muted-foreground">
            서버에 알림 웹훅(DISCORD_PROBLEM_WEBHOOK_URL)이 설정되지 않아 보낼
            수 없습니다.
          </p>
        )}

        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="announce-month" className="text-xs">
              월
            </Label>
            <select
              id="announce-month"
              value={month}
              onChange={(e) => pickMonth(Number(e.target.value))}
              className={SELECT_CLASS}
            >
              {MONTHS.map((m) => (
                <option key={m} value={m}>
                  {m}월
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="announce-week" className="text-xs">
              주차
            </Label>
            <select
              id="announce-week"
              value={week}
              onChange={(e) => setWeek(Number(e.target.value))}
              className={SELECT_CLASS}
            >
              {WEEKS.map((w) => (
                <option key={w} value={w}>
                  {w}주차
                </option>
              ))}
            </select>
          </div>
          <p className="text-xs text-muted-foreground pb-2">
            → {year}년 {label} · 기본값은 그 달에 보낸 마지막 주차 + 1
          </p>
        </div>

        {draft != null && (
          <>
            <p className="text-xs text-muted-foreground">
              목록 순서대로 3개씩 Set으로 묶어 Easy · Medium · Hard를
              붙였습니다. 보내기 전에 자유롭게 고치세요.
            </p>
            <Textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              rows={16}
              className="font-mono text-xs"
              aria-invalid={tooLong}
            />
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-4">
                <span
                  className={`text-xs ${tooLong ? "text-destructive" : "text-muted-foreground"}`}
                >
                  {draft.length} / {DISCORD_MESSAGE_LIMIT}자
                </span>
                <label className="flex items-center gap-1.5 text-xs">
                  <input
                    type="checkbox"
                    checked={testOnly}
                    onChange={(e) => setTestOnly(e.target.checked)}
                  />
                  테스트로 보내기 (기록 안 함 · 주차 계산에 안 들어감)
                </label>
              </div>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={sending}
                  onClick={() => setDraft(null)}
                >
                  취소
                </Button>
                <Button
                  type="button"
                  size="sm"
                  disabled={sending || tooLong || !draft.trim()}
                  onClick={() => void send()}
                >
                  <SendIcon className="size-4 mr-1" />
                  {sending ? "보내는 중…" : "디스코드로 보내기"}
                </Button>
              </div>
            </div>
          </>
        )}

        {records.length > 0 && (
          <div className="border-t pt-3">
            <p className="text-xs font-medium mb-1.5">
              최근 보낸 알림{" "}
              <span className="font-normal text-muted-foreground">
                (이 브라우저에만 저장)
              </span>
            </p>
            <ul className="space-y-1">
              {records.map((r) => (
                <li
                  key={r.id}
                  className="flex items-center justify-between text-xs text-muted-foreground"
                >
                  <span>
                    {r.year}년 {weekLabel(r.month, r.weekOfMonth)}
                  </span>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => removeRecord(r)}
                    aria-label="기록 삭제"
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
