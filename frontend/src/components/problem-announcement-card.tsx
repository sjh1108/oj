"use client";

import { MegaphoneIcon, SendIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import {
  problemAnnouncementApi,
  type ProblemAnnouncementConfig,
} from "@/lib/admin-api";
import {
  buildAnnouncement,
  DISCORD_MESSAGE_LIMIT,
  type AnnouncedProblem,
} from "@/lib/problem-announcement";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";

/**
 * "선정 문제 알림" — shown on the bulk upload page once problems have landed.
 * Opens a pre-filled, editable draft and posts it to the Discord channel the
 * server's webhook points at.
 */
export function ProblemAnnouncementCard({
  problems,
}: {
  problems: AnnouncedProblem[];
}) {
  const [config, setConfig] = useState<ProblemAnnouncementConfig | null>(null);
  const [draft, setDraft] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    problemAnnouncementApi
      .config()
      .then(setConfig)
      .catch(() => setConfig({ enabled: false, roleId: null }));
  }, []);

  if (problems.length === 0 || config == null) return null;

  const openDraft = () =>
    setDraft(
      buildAnnouncement(problems, {
        siteUrl: window.location.origin,
        roleId: config.roleId,
      }),
    );

  const send = async () => {
    if (!draft?.trim()) return;
    setSending(true);
    try {
      await problemAnnouncementApi.send(draft);
      toast.success("디스코드에 선정 문제 알림을 보냈습니다");
      setDraft(null);
    } catch (err) {
      toast.error(
        err instanceof ApiError ? err.message : "알림을 보내지 못했습니다",
      );
    } finally {
      setSending(false);
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
            disabled={!config.enabled}
            onClick={openDraft}
          >
            <MegaphoneIcon className="size-4 mr-1" />
            알림 작성
          </Button>
        )}
      </CardHeader>
      {!config.enabled && (
        <CardContent className="pt-0 text-xs text-muted-foreground">
          서버에 알림 웹훅(DISCORD_PROBLEM_WEBHOOK_URL)이 설정되지 않아 보낼 수
          없습니다.
        </CardContent>
      )}
      {draft != null && (
        <CardContent className="pt-0 space-y-3">
          <p className="text-xs text-muted-foreground">
            목록 순서대로 3개씩 Set으로 묶어 Easy · Medium · Hard를 붙였습니다.
            보내기 전에 자유롭게 고치세요.
          </p>
          <Textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={16}
            className="font-mono text-xs"
            aria-invalid={tooLong}
          />
          <div className="flex items-center justify-between gap-3">
            <span
              className={`text-xs ${tooLong ? "text-destructive" : "text-muted-foreground"}`}
            >
              {draft.length} / {DISCORD_MESSAGE_LIMIT}자
            </span>
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
        </CardContent>
      )}
    </Card>
  );
}
