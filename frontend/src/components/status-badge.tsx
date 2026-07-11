import { Badge } from "@/components/ui/badge";
import type {
  Difficulty,
  SubmissionResponse,
  SubmissionStatus,
} from "@/types/api";

const STATUS_LABEL: Record<SubmissionStatus, string> = {
  PENDING: "대기",
  JUDGING: "채점중",
  ACCEPTED: "정답",
  PARTIAL: "부분점수",
  WRONG_ANSWER: "오답",
  TIME_LIMIT: "시간초과",
  MEMORY_LIMIT: "메모리초과",
  COMPILE_ERROR: "컴파일 에러",
  RUNTIME_ERROR: "런타임 에러",
  SYSTEM_ERROR: "시스템 에러",
};

const STATUS_CLASS: Record<SubmissionStatus, string> = {
  PENDING: "bg-muted text-foreground",
  JUDGING: "bg-blue-500/15 text-blue-500 border-blue-500/30",
  ACCEPTED: "bg-green-500/15 text-green-500 border-green-500/30",
  PARTIAL: "bg-amber-500/15 text-amber-500 border-amber-500/30",
  WRONG_ANSWER: "bg-red-500/15 text-red-500 border-red-500/30",
  TIME_LIMIT: "bg-orange-500/15 text-orange-500 border-orange-500/30",
  MEMORY_LIMIT: "bg-orange-500/15 text-orange-500 border-orange-500/30",
  COMPILE_ERROR: "bg-yellow-500/15 text-yellow-500 border-yellow-500/30",
  RUNTIME_ERROR: "bg-red-500/15 text-red-500 border-red-500/30",
  SYSTEM_ERROR: "bg-red-500/15 text-red-500 border-red-500/30",
};

export function StatusBadge({
  status,
  progress,
  score,
  maxScore,
}: {
  status: SubmissionStatus;
  // 0-100 while judging (from the API); counts are never exposed.
  progress?: number | null;
  score?: number | null;
  maxScore?: number | null;
}) {
  const showProgress = status === "JUDGING" && progress != null;
  // Show the earned score for partial results (and full-score subtask problems).
  const showScore =
    (status === "PARTIAL" || status === "ACCEPTED") &&
    score != null &&
    maxScore != null &&
    maxScore > 0;

  let label = STATUS_LABEL[status];
  if (showProgress) label = `채점중 ${progress}%`;
  else if (status === "PARTIAL" && showScore) label = `부분 ${score}/${maxScore}`;
  else if (status === "ACCEPTED" && showScore && score < maxScore!)
    label = `${score}/${maxScore}`;

  return (
    <Badge variant="outline" className={STATUS_CLASS[status]}>
      {label}
    </Badge>
  );
}

export function isPending(s: Pick<SubmissionResponse, "status">) {
  return s.status === "PENDING" || s.status === "JUDGING";
}

const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  BRONZE: "브론즈",
  SILVER: "실버",
  GOLD: "골드",
  PLATINUM: "플래티넘",
  DIAMOND: "다이아",
};

const DIFFICULTY_CLASS: Record<Difficulty, string> = {
  BRONZE: "bg-amber-700/15 text-amber-700 border-amber-700/30",
  SILVER: "bg-slate-400/15 text-slate-400 border-slate-400/30",
  GOLD: "bg-yellow-500/15 text-yellow-500 border-yellow-500/30",
  PLATINUM: "bg-emerald-400/15 text-emerald-400 border-emerald-400/30",
  DIAMOND: "bg-cyan-400/15 text-cyan-400 border-cyan-400/30",
};

export function DifficultyBadge({ difficulty }: { difficulty: Difficulty }) {
  return (
    <Badge variant="outline" className={DIFFICULTY_CLASS[difficulty]}>
      {DIFFICULTY_LABEL[difficulty]}
    </Badge>
  );
}
