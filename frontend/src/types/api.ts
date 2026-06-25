export type Role = "USER" | "ADMIN";

export type Difficulty = "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "DIAMOND";

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
}

export interface ProblemListItem {
  id: number;
  title: string;
  difficulty: Difficulty;
  authorUsername: string | null;
  isPublic: boolean;
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
  authorUsername: string | null;
  isPublic: boolean;
  sampleTestCases: TestCaseResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface TestCaseRequest {
  input: string;
  expectedOutput: string;
  orderIndex: number;
  isSample: boolean;
}

export interface GenerateTestCaseRequest {
  generatorLanguage: Language;
  generatorCode: string;
  generatorStdin?: string;
  solutionLanguage: Language;
  solutionCode: string;
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
}

export interface CreateProblemRequest {
  title: string;
  description: string;
  inputDescription?: string;
  outputDescription?: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: Difficulty;
  isPublic: boolean;
  testCases: TestCaseRequest[];
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
  passedTestCases: number;
  totalTestCases: number;
  isPublic: boolean;
  createdAt: string;
}

export interface SubmissionDetailResponse extends SubmissionResponse {
  sourceCode: string;
  errorMessage: string | null;
}

export interface VisibilityRequest {
  isPublic: boolean;
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
