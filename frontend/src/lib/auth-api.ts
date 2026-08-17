import { api } from "@/lib/api";
import type {
  ChangePasswordRequest,
  DiscordLinkCodeResponse,
  LoginRequest,
  SignupRequest,
  TokenResponse,
  UserPreferences,
  UserResponse,
} from "@/types/api";

export const authApi = {
  signup: (body: SignupRequest) =>
    api<UserResponse>("/api/auth/signup", { method: "POST", body, auth: false }),
  login: (body: LoginRequest) =>
    api<TokenResponse>("/api/auth/login", { method: "POST", body, auth: false }),
  me: () => api<UserResponse>("/api/users/me"),
  mySolvedProblems: () => api<number[]>("/api/users/me/solved-problems"),
  changePassword: (body: ChangePasswordRequest) =>
    api<void>("/api/users/me/password", { method: "PUT", body }),
  preferences: () => api<UserPreferences>("/api/users/me/preferences"),
  updatePreferences: (body: UserPreferences) =>
    api<UserPreferences>("/api/users/me/preferences", { method: "PUT", body }),
  discordLinkCode: () =>
    api<DiscordLinkCodeResponse>("/api/users/me/discord/link-code", {
      method: "POST",
    }),
};
