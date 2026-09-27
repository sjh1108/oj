import { api } from "@/lib/api";
import type {
  AdminResetPasswordRequest,
  AdminResetPasswordResponse,
} from "@/types/api";

export const adminApi = {
  resetPassword: (body: AdminResetPasswordRequest) =>
    api<AdminResetPasswordResponse>("/api/admin/users/reset-password", {
      method: "POST",
      body,
    }),
};

export interface ProblemAnnouncementConfig {
  enabled: boolean;
  roleId: string | null;
}

export interface ProblemAnnouncementSuggestion {
  year: number;
  month: number;
  weekOfMonth: number;
}

export interface ProblemAnnouncementRecord {
  id: number;
  year: number;
  month: number;
  weekOfMonth: number;
  createdAt: string;
}

export const problemAnnouncementApi = {
  config: () =>
    api<ProblemAnnouncementConfig>("/api/admin/problem-announcements/config"),
  suggestion: (year: number, month: number) =>
    api<ProblemAnnouncementSuggestion>(
      `/api/admin/problem-announcements/suggestion?year=${year}&month=${month}`,
    ),
  recent: () =>
    api<ProblemAnnouncementRecord[]>("/api/admin/problem-announcements"),
  send: (body: {
    content: string;
    year: number;
    month: number;
    weekOfMonth: number;
    record: boolean;
  }) =>
    api<void>("/api/admin/problem-announcements", { method: "POST", body }),
  remove: (id: number) =>
    api<void>(`/api/admin/problem-announcements/${id}`, { method: "DELETE" }),
};
