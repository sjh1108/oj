import { api } from "@/lib/api";
import type {
  LoginRequest,
  SignupRequest,
  TokenResponse,
  UserResponse,
} from "@/types/api";

export const authApi = {
  signup: (body: SignupRequest) =>
    api<UserResponse>("/api/auth/signup", { method: "POST", body, auth: false }),
  login: (body: LoginRequest) =>
    api<TokenResponse>("/api/auth/login", { method: "POST", body, auth: false }),
  me: () => api<UserResponse>("/api/users/me"),
};
