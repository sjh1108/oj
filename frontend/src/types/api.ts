export type Role = "USER" | "ADMIN";

export type Difficulty = "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "DIAMOND";

export type Language = "JAVA" | "PYTHON3" | "PYPY3" | "CPP" | "C" | "JAVASCRIPT";

export type SubmissionStatus =
  | "PENDING"
  | "JUDGING"
  | "ACCEPTED"
  | "PARTIAL"
  | "WRONG_ANSWER"
  | "TIME_LIMIT"
  | "MEMORY_LIMIT"
  | "COMPILE_ERROR"
  | "RUNTIME_ERROR"
  | "SYSTEM_ERROR";

export interface SubtaskInfo {
  id: number;
  label: string;
  points: number;
  orderIndex: number;
}

export interface SubtaskResult {
  label: string;
  points: number;
  earned: number;
  passed: boolean;
  status: string;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface TestCaseResponse {
  id: number;
  input: string;
  expectedOutput: string;
  orderIndex: number;
  isSample: boolean;
  isDraft: boolean;
}

export type SolvedFilter = "ALL" | "SOLVED" | "ATTEMPTED" | "UNSOLVED";

export interface ProblemListParams {
  page?: number;
  size?: number;
  keyword?: string;
  difficulty?: Difficulty | "";
  tag?: string;
  solved?: SolvedFilter;
}

export interface ProblemListItem {
  id: number;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  authorUsername: string | null;
  isPublic: boolean;
  solved: boolean;
  attempted: boolean;
  createdAt: string;
}

export interface ProblemDetailResponse {
  id: number;
  title: string;
  description: string;
  inputDescription: string | null;
  outputDescription: string | null;
  timeLimit: number;
  memoryLimit: number;
  difficulty: Difficulty;
  tags: string[];
  authorUsername: string | null;
  isPublic: boolean;
  sampleTestCases: TestCaseResponse[];
  subtasks: SubtaskInfo[];
  createdAt: string;
  updatedAt: string;
}

export interface TestCaseRequest {
  input: string;
  expectedOutput: string;
  orderIndex: number;
  isSample: boolean;
  // Create as a draft so data can be appended in <1MB chunks; drafts are
  // excluded from judging until finalized.
  draft?: boolean;
}

export interface AppendTestCaseChunkRequest {
  inputChunk?: string;
  expectedOutputChunk?: string;
}

export interface TestCaseUploadStatusResponse {
  id: number;
  inputLength: number;
  expectedOutputLength: number;
  draft: boolean;
}

export interface GenerateTestCaseRequest {
  generatorLanguage: Language;
  generatorCode: string;
  generatorStdin?: string;
  solutionLanguage: Language;
  solutionCode: string;
  // Optional second correct solution — must reproduce the expected output on
  // the generated input (server rejects the case otherwise).
  validatorLanguage?: Language;
  validatorCode?: string;
  orderIndex: number;
  isSample: boolean;
}

export interface GenerateTestCaseResponse {
  id: number;
  orderIndex: number;
  isSample: boolean;
  inputSize: number;
  outputSize: number;
  inputPreview: string;
  outputPreview: string;
  generatorRuntimeMs: number | null;
  solutionRuntimeMs: number | null;
  validatorRuntimeMs: number | null;
}

export interface SubtaskRequest {
  label?: string;
  points: number;
  testCases: TestCaseRequest[];
}

export interface CreateProblemRequest {
  title: string;
  description: string;
  inputDescription?: string;
  outputDescription?: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: Difficulty;
  tags?: string[];
  isPublic: boolean;
  testCases?: TestCaseRequest[];
  subtasks?: SubtaskRequest[];
}

export interface SubmitRequest {
  problemId: number;
  language: Language;
  sourceCode: string;
}

export interface SubmissionResponse {
  id: number;
  problemId: number;
  problemTitle: string;
  username: string;
  language: Language;
  status: SubmissionStatus;
  runtime: number | null;
  memory: number | null;
  // 0-100 while PENDING/JUDGING, null once finished. Absolute test-case
  // counts are not exposed (BOJ-style).
  progress: number | null;
  score: number | null;
  maxScore: number | null;
  isPublic: boolean;
  createdAt: string;
}

export interface SubmissionDetailResponse extends SubmissionResponse {
  sourceCode: string;
  errorMessage: string | null;
  subtaskResults: SubtaskResult[];
}

export interface VisibilityRequest {
  isPublic: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface DiscordLinkCodeResponse {
  code: string;
  expiresInSeconds: number;
}

export interface AdminResetPasswordRequest {
  usernameOrEmail: string;
}

export interface AdminResetPasswordResponse {
  userId: number;
  username: string;
  email: string;
  temporaryPassword: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface UploadImageRequest {
  contentType: string;
  base64Data: string;
}

export interface UploadImageResponse {
  url: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

export type RunStatus =
  | "OK"
  | "TIME_LIMIT"
  | "RUNTIME_ERROR"
  | "COMPILE_ERROR"
  | "SYSTEM_ERROR";

export interface RunRequest {
  problemId: number;
  language: Language;
  sourceCode: string;
  stdin: string;
}

export interface RunResponse {
  status: RunStatus;
  stdout: string | null;
  stderr: string | null;
  compileOutput: string | null;
  runtimeMs: number | null;
  memoryKb: number | null;
  errorMessage: string | null;
}
