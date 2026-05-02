import { api } from "@/lib/api";
import type {
  CreateProblemRequest,
  PageResponse,
  ProblemDetailResponse,
  ProblemListItem,
} from "@/types/api";

export const problemsApi = {
  list: (page = 0, size = 20) =>
    api<PageResponse<ProblemListItem>>(
      `/api/problems?page=${page}&size=${size}`,
    ),
  detail: (id: number) =>
    api<ProblemDetailResponse>(`/api/problems/${id}`),
  create: (body: CreateProblemRequest) =>
    api<ProblemDetailResponse>("/api/problems", { method: "POST", body }),
};
