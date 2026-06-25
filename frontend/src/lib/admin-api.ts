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
