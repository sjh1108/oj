"use client";

import { MegaphoneIcon, SendIcon, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import {
  problemAnnouncementApi,
  type ProblemAnnouncementConfig,
  type ProblemAnnouncementRecord,
} from "@/lib/admin-api";
import {
  buildAnnouncement,
  DISCORD_MESSAGE_LIMIT,
  nextMondayIso,
  replaceWeekLabel,
  weekLabel,
  type AnnouncedProblem,
} from "@/lib/problem-announcement";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

const errorMessage = (err: unknown, fallback: string) =>
  err instanceof ApiError ? err.message : fallback;

/**
 * "선정 문제 알림" — shown on the bulk upload page once problems have landed.
 *
 * The week in the title is the nth study actually held in the study date's
 * month; the server derives it from notices recorded so far, so weeks the
 * study skips (no notice sent) are not counted. A test send is not recorded.
 */
export function ProblemAnnouncementCard({
  problems,
}: {
  problems: AnnouncedProblem[];
}) {
  const [config, setConfig] = useState<ProblemAnnouncementConfig | null>(null);
  const [studyDate, setStudyDate] = useState(nextMondayIso);
  const [label, setLabel] = useState<string | null>(null);
  const [draft, setDraft] = useState<string | null>(null);
  const [testOnly, setTestOnly] = useState(false);
  const [sending, setSending] = useState(false);
  const [records, setRecords] = useState<ProblemAnnouncementRecord[]>([]);

  const loadRecords = useCallback(() => {
    problemAnnouncementApi
      .recent()
      .then(setRecords)
      .catch(() => setRecords([]));
  }, []);

  useEffect(() => {
    problemAnnouncementApi
      .config()
      .then(setConfig)
      .catch(() => setConfig({ enabled: false, roleId: null }));
    loadRecords();
  }, [loadRecords]);

  // Recompute "N월 N주차" whenever the study date changes, and patch it into a
  // draft that is already open without discarding the admin's edits.
  useEffect(() => {
    if (!studyDate) return;
    let cancelled = false;
    problemAnnouncementApi
      .week(studyDate)
      .then((w) => {
        if (cancelled) return;
        const next = weekLabel(w.month, w.weekOfMonth);
        setLabel(next);
        setDraft((d) => (d == null ? d : replaceWeekLabel(d, next)));
      })
      .catch(() => !cancelled && setLabel(null));
    return () => {
      cancelled = true;
    };
  }, [studyDate, records]);

  if (problems.length === 0 || config == null) return null;

  const openDraft = () =>
    setDraft(
      buildAnnouncement(problems, {
        siteUrl: window.location.origin,
        weekLabel: label ?? "N월 N주차",
        roleId: config.roleId,
      }),
    );

  const send = async () => {
    if (!draft?.trim() || !studyDate) return;
    setSending(true);
    try {
      await problemAnnouncementApi.send({
        content: draft,
        studyDate,
        record: !testOnly,
      });
      toast.success(
        testOnly
          ? "테스트로 보냈습니다 (기록하지 않음)"
          : "디스코드에 선정 문제 알림을 보냈습니다",
      );
      setDraft(null);
      if (!testOnly) loadRecords();
    } catch (err) {
      toast.error(errorMessage(err, "알림을 보내지 못했습니다"));
    } finally {
      setSending(false);
    }
  };

  const removeRecord = async (r: ProblemAnnouncementRecord) => {
    if (
      !window.confirm(
        `${r.studyDate} (${r.weekOfMonth}주차) 기록을 지울까요? 디스코드 메시지는 지워지지 않고, 이후 주차 계산에서만 빠집니다.`,
      )
    )
      return;
    try {
      await problemAnnouncementApi.remove(r.id);
      loadRecords();
    } catch (err) {
      toast.error(errorMessage(err, "기록을 지우지 못했습니다"));
    }
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
            disabled={!config.enabled || !studyDate}
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
            <Label htmlFor="announce-study-date" className="text-xs">
              스터디 날짜
            </Label>
            <Input
              id="announce-study-date"
              type="date"
              value={studyDate}
              onChange={(e) => setStudyDate(e.target.value)}
              className="w-44"
            />
          </div>
          <p className="text-xs text-muted-foreground pb-2">
            {label
              ? `→ ${label} (그 달에 기록된 스터디 수 + 1, 쉬는 주는 세지 않음)`
              : "주차를 계산하는 중…"}
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
                  disabled={sending || tooLong || !draft.trim() || !studyDate}
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
            <p className="text-xs font-medium mb-1.5">최근 보낸 알림</p>
            <ul className="space-y-1">
              {records.map((r) => (
                <li
                  key={r.id}
                  className="flex items-center justify-between text-xs text-muted-foreground"
                >
                  <span>
                    {r.studyDate} 스터디 ·{" "}
                    {weekLabel(Number(r.studyDate.slice(5, 7)), r.weekOfMonth)}
                  </span>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => void removeRecord(r)}
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
