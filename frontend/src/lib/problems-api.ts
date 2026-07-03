import { api } from "@/lib/api";
import type {
  CreateProblemRequest,
  GenerateTestCaseRequest,
  GenerateTestCaseResponse,
  PageResponse,
  ProblemDetailResponse,
  ProblemListItem,
  ProblemListParams,
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
  tags?: string[];
  isPublic: boolean;
}

export const problemsApi = {
  list: ({ page = 0, size = 20, keyword, difficulty, tag, solved }: ProblemListParams = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword?.trim()) params.set("keyword", keyword.trim());
    if (difficulty) params.set("difficulty", difficulty);
    if (tag) params.set("tag", tag);
    if (solved && solved !== "ALL") params.set("solved", solved);
    return api<PageResponse<ProblemListItem>>(`/api/problems?${params}`);
  },
  tags: () => api<string[]>("/api/problems/tags"),
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
  generateTestCase: (problemId: number, body: GenerateTestCaseRequest) =>
    api<GenerateTestCaseResponse>(
      `/api/problems/${problemId}/test-cases/generate`,
      { method: "POST", body },
    ),
};
