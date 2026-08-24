import { api } from "@/lib/api";
import type {
  AppendTestCaseChunkRequest,
  CreateProblemRequest,
  GenerateTestCaseRequest,
  GenerateTestCaseResponse,
  PageResponse,
  ProblemDetailResponse,
  ProblemListItem,
  ProblemListParams,
  ProblemNoteResponse,
  TestCaseMetaRequest,
  TestCaseRequest,
  TestCaseResponse,
  TestCaseSummaryResponse,
  TestCaseUploadStatusResponse,
  UploadImageRequest,
  UploadImageResponse,
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

  // 문제별 개인 메모 — 언제나 로그인한 본인 것만 오간다.
  // 빈 내용으로 저장하면 서버가 행을 지운다(별도 삭제 API 없음).
  getNote: (problemId: number) =>
    api<ProblemNoteResponse>(`/api/problems/${problemId}/note`),
  saveNote: (problemId: number, content: string) =>
    api<ProblemNoteResponse>(`/api/problems/${problemId}/note`, {
      method: "PUT",
      body: { content },
    }),
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

  // Sizes + head of the data only. Full cases run into the megabytes, so they
  // are fetched one at a time with getTestCase when actually opened.
  listTestCases: (problemId: number) =>
    api<TestCaseSummaryResponse[]>(`/api/problems/${problemId}/test-cases`),
  getTestCase: (problemId: number, tcId: number) =>
    api<TestCaseResponse>(`/api/problems/${problemId}/test-cases/${tcId}`),
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
  updateTestCaseMeta: (problemId: number, tcId: number, body: TestCaseMetaRequest) =>
    api<void>(`/api/problems/${problemId}/test-cases/${tcId}/meta`, {
      method: "PATCH",
      body,
    }),
  appendTestCaseChunk: (
    problemId: number,
    tcId: number,
    body: AppendTestCaseChunkRequest,
  ) =>
    api<TestCaseUploadStatusResponse>(
      `/api/problems/${problemId}/test-cases/${tcId}/append`,
      { method: "PATCH", body },
    ),
  finalizeTestCase: (problemId: number, tcId: number) =>
    api<TestCaseUploadStatusResponse>(
      `/api/problems/${problemId}/test-cases/${tcId}/finalize`,
      { method: "POST" },
    ),
  deleteTestCase: (problemId: number, tcId: number) =>
    api<void>(`/api/problems/${problemId}/test-cases/${tcId}`, {
      method: "DELETE",
    }),
  // 지문 이미지 업로드 (S3) — 응답 url을 마크다운 ![alt](url)로 사용.
  uploadImage: (body: UploadImageRequest) =>
    api<UploadImageResponse>("/api/images", { method: "POST", body }),

  generateTestCase: (problemId: number, body: GenerateTestCaseRequest) =>
    api<GenerateTestCaseResponse>(
      `/api/problems/${problemId}/test-cases/generate`,
      { method: "POST", body },
    ),
};
