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

export interface ProblemAnnouncementWeek {
  studyDate: string;
  month: number;
  weekOfMonth: number;
}

export interface ProblemAnnouncementRecord {
  id: number;
  studyDate: string;
  weekOfMonth: number;
  createdAt: string;
}

export const problemAnnouncementApi = {
  config: () =>
    api<ProblemAnnouncementConfig>("/api/admin/problem-announcements/config"),
  week: (studyDate: string) =>
    api<ProblemAnnouncementWeek>(
      `/api/admin/problem-announcements/week?studyDate=${studyDate}`,
    ),
  recent: () =>
    api<ProblemAnnouncementRecord[]>("/api/admin/problem-announcements"),
  send: (body: { content: string; studyDate: string; record: boolean }) =>
    api<void>("/api/admin/problem-announcements", { method: "POST", body }),
  remove: (id: number) =>
    api<void>(`/api/admin/problem-announcements/${id}`, { method: "DELETE" }),
};
