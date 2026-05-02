export type Role = "USER" | "ADMIN";

export type Difficulty = "EASY" | "MEDIUM" | "HARD";

export type Language = "JAVA" | "PYTHON3" | "CPP" | "C" | "JAVASCRIPT";

export type SubmissionStatus =
  | "PENDING"
  | "JUDGING"
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "TIME_LIMIT"
  | "COMPILE_ERROR"
  | "RUNTIME_ERROR"
  | "SYSTEM_ERROR";

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

export interface TestCaseSummary {
  id: number;
  input: string;
  expectedOutput: string;
  isSample: boolean;
}

export interface ProblemListItem {
  id: number;
  title: string;
  difficulty: Difficulty;
  isPublic: boolean;
}

export interface ProblemDetailResponse {
  id: number;
  title: string;
  description: string;
  inputFormat: string;
  outputFormat: string;
  timeLimitMs: number;
  memoryLimitKb: number;
  difficulty: Difficulty;
  isPublic: boolean;
  sampleTestCases: TestCaseSummary[];
}

export interface CreateProblemRequest {
  title: string;
  description: string;
  inputFormat: string;
  outputFormat: string;
  timeLimitMs: number;
  memoryLimitKb: number;
  difficulty: Difficulty;
  isPublic: boolean;
  testCases: TestCaseRequest[];
}

export interface TestCaseRequest {
  input: string;
  expectedOutput: string;
  isSample: boolean;
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
  runtimeMs: number | null;
  memoryKb: number | null;
  createdAt: string;
}

export interface SubmissionDetailResponse extends SubmissionResponse {
  sourceCode: string;
  errorMessage: string | null;
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

export interface ErrorResponse {
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}