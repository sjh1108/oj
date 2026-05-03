import { api } from "@/lib/api";
import type {
  PageResponse,
  SubmissionDetailResponse,
  SubmissionResponse,
  SubmitRequest,
  VisibilityRequest,
} from "@/types/api";

export const submissionsApi = {
  submit: (body: SubmitRequest) =>
    api<SubmissionResponse>("/api/submissions", { method: "POST", body }),
  list: (page = 0, size = 30) =>
    api<PageResponse<SubmissionResponse>>(
      `/api/submissions?page=${page}&size=${size}`,
    ),
  me: (page = 0, size = 20) =>
    api<PageResponse<SubmissionResponse>>(
      `/api/submissions/me?page=${page}&size=${size}`,
    ),
  detail: (id: number) =>
    api<SubmissionDetailResponse>(`/api/submissions/${id}`),
  updateVisibility: (id: number, body: VisibilityRequest) =>
    api<SubmissionResponse>(`/api/submissions/${id}/visibility`, {
      method: "PATCH",
      body,
    }),
  solutions: (problemId: number, page = 0, size = 20) =>
    api<PageResponse<SubmissionResponse>>(
      `/api/problems/${problemId}/solutions?page=${page}&size=${size}`,
    ),
  rejudge: (id: number) =>
    api<{ submissionId: number; queued: number }>(
      `/api/submissions/${id}/rejudge`,
      { method: "POST" },
    ),
};
