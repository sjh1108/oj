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

export const problemAnnouncementApi = {
  config: () =>
    api<ProblemAnnouncementConfig>("/api/admin/problem-announcements/config"),
  send: (content: string) =>
    api<void>("/api/admin/problem-announcements", {
      method: "POST",
      body: { content },
    }),
};
