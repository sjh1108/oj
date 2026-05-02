import { api } from "@/lib/api";
import type {
  PageResponse,
  SubmissionDetailResponse,
  SubmissionResponse,
  SubmitRequest,
} from "@/types/api";

export const submissionsApi = {
  submit: (body: SubmitRequest) =>
    api<SubmissionResponse>("/api/submissions", { method: "POST", body }),
  me: (page = 0, size = 20) =>
    api<PageResponse<SubmissionResponse>>(
      `/api/submissions/me?page=${page}&size=${size}`,
    ),
  detail: (id: number) =>
    api<SubmissionDetailResponse>(`/api/submissions/${id}`),
};
