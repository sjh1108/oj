import { api } from "@/lib/api";
import type {
  CreateProblemRequest,
  PageResponse,
  ProblemDetailResponse,
  ProblemListItem,
  TestCaseRequest,
  TestCaseResponse,
} from "@/types/api";

export interface UpdateProblemRequest {
  title: string;
  description: string;
  inputDescription?: string;
  outputDescription?: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "DIAMOND";
  isPublic: boolean;
}

export const problemsApi = {
  list: (page = 0, size = 20) =>
    api<PageResponse<ProblemListItem>>(
      `/api/problems?page=${page}&size=${size}`,
    ),
  detail: (id: number) =>
    api<ProblemDetailResponse>(`/api/problems/${id}`),
  create: (body: CreateProblemRequest) =>
    api<ProblemDetailResponse>("/api/problems", { method: "POST", body }),
  update: (id: number, body: UpdateProblemRequest) =>
    api<ProblemDetailResponse>(`/api/problems/${id}`, {
      method: "PUT",
      body,
    }),
  delete: (id: number) =>
    api<void>(`/api/problems/${id}`, { method: "DELETE" }),
  rejudge: (id: number) =>
    api<{ queued: number }>(`/api/problems/${id}/rejudge`, { method: "POST" }),

  listTestCases: (problemId: number) =>
    api<TestCaseResponse[]>(`/api/problems/${problemId}/test-cases`),
  addTestCase: (problemId: number, body: TestCaseRequest) =>
    api<TestCaseResponse>(`/api/problems/${problemId}/test-cases`, {
      method: "POST",
      body,
    }),
  updateTestCase: (problemId: number, tcId: number, body: TestCaseRequest) =>
    api<TestCaseResponse>(`/api/problems/${problemId}/test-cases/${tcId}`, {
      method: "PUT",
      body,
    }),
  deleteTestCase: (problemId: number, tcId: number) =>
    api<void>(`/api/problems/${problemId}/test-cases/${tcId}`, {
      method: "DELETE",
    }),
};
