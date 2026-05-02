import { Badge } from "@/components/ui/badge";
import type { Difficulty, SubmissionStatus } from "@/types/api";

const STATUS_LABEL: Record<SubmissionStatus, string> = {
  PENDING: "대기",
  JUDGING: "채점중",
  ACCEPTED: "정답",
  WRONG_ANSWER: "오답",
  TIME_LIMIT: "시간초과",
  COMPILE_ERROR: "컴파일 에러",
  RUNTIME_ERROR: "런타임 에러",
  SYSTEM_ERROR: "시스템 에러",
};

const STATUS_CLASS: Record<SubmissionStatus, string> = {
  PENDING: "bg-muted text-foreground",
  JUDGING: "bg-blue-500/15 text-blue-500 border-blue-500/30",
  ACCEPTED: "bg-green-500/15 text-green-500 border-green-500/30",
  WRONG_ANSWER: "bg-red-500/15 text-red-500 border-red-500/30",
  TIME_LIMIT: "bg-orange-500/15 text-orange-500 border-orange-500/30",
  COMPILE_ERROR: "bg-yellow-500/15 text-yellow-500 border-yellow-500/30",
  RUNTIME_ERROR: "bg-red-500/15 text-red-500 border-red-500/30",
  SYSTEM_ERROR: "bg-red-500/15 text-red-500 border-red-500/30",
};

export function StatusBadge({ status }: { status: SubmissionStatus }) {
  return (
    <Badge variant="outline" className={STATUS_CLASS[status]}>
      {STATUS_LABEL[status]}
    </Badge>
  );
}

const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  EASY: "쉬움",
  MEDIUM: "보통",
  HARD: "어려움",
};

const DIFFICULTY_CLASS: Record<Difficulty, string> = {
  EASY: "bg-green-500/15 text-green-500 border-green-500/30",
  MEDIUM: "bg-yellow-500/15 text-yellow-500 border-yellow-500/30",
  HARD: "bg-red-500/15 text-red-500 border-red-500/30",
};

export function DifficultyBadge({ difficulty }: { difficulty: Difficulty }) {
  return (
    <Badge variant="outline" className={DIFFICULTY_CLASS[difficulty]}>
      {DIFFICULTY_LABEL[difficulty]}
    </Badge>
  );
}
