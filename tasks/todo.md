# Phase 2: Spring Boot Backend Skeleton — Todo

## 확정 결정사항
- **Group**: `dev.algoj`
- **Base package**: `dev.algoj`
- **Application class**: `OjApplication` (유지)
- **Spring Boot**: 3.5.14 / Java 21
- **설정 파일**: `application.yml`
- **의존성 정책**: amqp/redis/session-redis는 Phase 3 대비 유지

---

## 작업 순서

### 1. 빌드 / 설정 정리
- [ ] `build.gradle`: `group` → `dev.algoj`
- [ ] `build.gradle`: JWT 의존성 추가 (`jjwt-api/impl/jackson` 0.12.6)
- [ ] `build.gradle`: `spring-boot-devtools` 추가 (developmentOnly)
- [ ] `application.properties` 삭제, `application.yml` 작성 (datasource/jpa/judge0/jwt/logging)

### 2. 패키지 이동
- [ ] `OjApplication` → `src/main/java/dev/algoj/OjApplication.java`
- [ ] `OjApplicationTests` → `src/test/java/dev/algoj/OjApplicationTests.java`
- [ ] 빈 `com/thdwngjs/oj` 디렉토리 제거
- [ ] `oj.iml` 등 IntelliJ 메타파일 그대로 유지 (모듈명 oj)

### 3. 도메인 Entity (명세 그대로)
- [ ] `dev.algoj.domain.user.entity.User` + `Role` enum
- [ ] `dev.algoj.domain.problem.entity.Problem` + `Difficulty` enum
- [ ] `dev.algoj.domain.problem.entity.TestCase`
- [ ] `dev.algoj.domain.submission.entity.Submission` + `Language` / `Status` enum

### 4. Repository (JpaRepository 빈껍데기)
- [ ] `UserRepository` (findByUsername / findByEmail / existsByUsername / existsByEmail)
- [ ] `ProblemRepository`
- [ ] `TestCaseRepository`
- [ ] `SubmissionRepository`

### 5. Health Controller
- [ ] `dev.algoj.global.controller.HealthController` — `GET /api/health` → `{ "status": "UP" }`
- [ ] Spring Security가 health 엔드포인트 차단 안 하도록 임시 SecurityConfig (전체 permitAll, 후속 세션에서 JWT 필터 추가 시 보강)

### 6. 검증
- [ ] `./gradlew build` 통과
- [ ] `./gradlew bootRun` 시작 → `GET http://localhost:8080/api/health` 200 OK
- [ ] MySQL `oj_dev`에 `users / problems / test_cases / submissions` 4개 테이블 생성 확인 (ddl-auto=update)

---

## 다음 세션(Phase 2 후반) 예정
- JWT 토큰 발급/검증 유틸
- 회원가입/로그인 API
- Spring Security 필터 체인 (JWT)
- 문제 CRUD API + TestCase 업로드
- Submission 생성 API + Judge0Client + 동기 채점

---

## Review (2026-05-02)

### 결과
- ✅ build.gradle: group `dev.algoj`, JWT 0.12.6 + devtools 추가
- ✅ application.yml 전환 완료, `application.properties` 제거
- ✅ 패키지 `com.thdwngjs.oj` → `dev.algoj` 이동 (`OjApplication`, `OjApplicationTests`)
- ✅ Entity 4개 (User, Problem, TestCase, Submission) + 4개 enum
- ✅ Repository 4개 (UserRepository는 username/email 조회 메서드 포함)
- ✅ HealthController (`GET /api/health` → `{"status":"UP","timestamp":...}`)
- ✅ 임시 SecurityConfig: csrf/formLogin/httpBasic disable, STATELESS, anyRequest permitAll
- ✅ `gradlew build` SUCCESS
- ✅ bootRun → 8080 LISTENING, 헬스체크 200 OK
- ✅ MySQL `oj_dev` 4개 테이블 자동 생성, FK 정상

### 트레이드오프 / 이슈
- **Hibernate dialect 명시 제거**: `MySQL8Dialect` deprecation 경고 발생 → application.yml에서 `dialect` 항목 제거. Hibernate가 드라이버 메타데이터로 자동 선택.
- **RabbitMQ/Redis 미기동 영향 없음**: 의존성에 amqp/data-redis/session-redis 있지만 lazy connection이라 startup 통과. Phase 3 진입 시 docker-compose에 추가 필요.
- **Spring Security 기본 비밀번호 경고**: permitAll로 임시 처리. 다음 세션에서 JWT 필터 + UserDetailsService 작성 시 해소.
- **MySQL8Dialect → MySQLDialect**: Hibernate 6.x에서 추천. 명시 제거로 자동 선택되므로 별도 변경 불필요.

### 다음에 가져갈 lessons
- (별도 lessons.md에 기록)

### 다음 세션 (Phase 2 후반) — 시작점
1. `JwtTokenProvider` 유틸 (생성/검증/Claim 추출)
2. `JwtAuthenticationFilter` (요청당 토큰 검사)
3. `UserDetailsService` 구현 + `AuthenticationManager` 빈
4. `AuthController`: `POST /api/auth/signup`, `POST /api/auth/login`
5. `SecurityConfig` 보강: `/api/auth/**`, `/api/health` permitAll, 그 외 인증 요구
6. CORS 설정 (Next.js 프론트 대비)

---

# Phase 2 후반 — Step A: 인증 풀스택 (2026-05-02 진행)

## 목표
회원가입 → 로그인 → JWT 발급 → 보호된 엔드포인트 호출까지 end-to-end 동작.

## 설계 결정
- **Access + Refresh 토큰 둘 다 발급**, 단 회전(rotation) 없음. Refresh 저장소(Redis)는 Phase 3에서 도입.
- 패스워드: BCrypt (이미 빈 등록됨)
- 응답 본문 표준화: 단순 DTO. 글로벌 에러는 `ErrorResponse {code, message}` 통일.
- JWT 라이브러리: jjwt 0.12.6 (이미 의존성 추가됨)

## 작업 순서

### A1. 예외 처리 인프라
- [ ] `dev.algoj.global.exception.ErrorCode` enum
- [ ] `dev.algoj.global.exception.BusinessException`
- [ ] `dev.algoj.global.exception.ErrorResponse` (record)
- [ ] `dev.algoj.global.exception.GlobalExceptionHandler` (@RestControllerAdvice)

### A2. JWT 인프라
- [ ] `dev.algoj.global.security.JwtTokenProvider` (createAccessToken, createRefreshToken, parseClaims, validateToken)
- [ ] `dev.algoj.global.security.UserPrincipal` (UserDetails 구현, User 래핑)
- [ ] `dev.algoj.global.security.CustomUserDetailsService` (loadUserByUsername)
- [ ] `dev.algoj.global.security.JwtAuthenticationFilter` (OncePerRequestFilter)

### A3. SecurityConfig 보강 + CORS
- [ ] `SecurityConfig`: `/api/auth/**`, `/api/health` permitAll, 그 외 authenticated. JwtAuthenticationFilter 등록. AuthenticationManager 빈
- [ ] `dev.algoj.global.config.CorsConfig` (Next.js 프론트 대비, 일단 `localhost:3000` 허용)

### A4. Auth API
- [ ] DTO: `SignupRequest`, `LoginRequest`, `TokenResponse`, `UserResponse`
- [ ] `AuthService` (signup / login)
- [ ] `AuthController` (POST /api/auth/signup, POST /api/auth/login)
- [ ] `UserController` (GET /api/users/me — JWT 검증 동작 확인용)

### A5. 검증
- [x] `gradlew build` 통과
- [x] `bootRun` 시작
- [x] `POST /api/auth/signup` 201 + DB users 행 생성
- [x] `POST /api/auth/login` 200 + accessToken/refreshToken 응답
- [x] `GET /api/users/me` 토큰 없이 401, 토큰 있으면 200
- [x] 잘못된 토큰 401
- [x] 중복 signup 409 USERNAME_DUPLICATED
- [x] 잘못된 비밀번호 401 INVALID_CREDENTIALS
- [x] @Valid 위반 400 + fieldErrors 배열

## Step A 완료 (2026-05-02)

### 결과
- 인증 풀스택 14파일 추가 (예외 4 + 보안 4 + 설정 2 + DTO 4 + 서비스/컨트롤러 3)
- 8개 시나리오 end-to-end 통과 (HTTP 코드/응답 본문 모두 의도대로)
- HS512 서명, expiresIn 3600s (access), 604800s (refresh)
- AuthenticationEntryPoint/AccessDeniedHandler 커스텀 → 401/403도 ErrorResponse JSON으로 통일

### 트레이드오프 / 이슈
- **Refresh token 회전 없음**: Phase 3에서 Redis 도입 후 회전/블랙리스트 추가. 현재는 클라이언트가 보관만.
- **JWT secret 평문 yml**: 운영 시 환경변수로 이전 필요 (이미 노트됨).
- **CORS allowed origins**: `localhost:3000` 한 개만. 운영 도메인 확정 시 추가.
- **GET /api/users/me에서 DB 재조회**: principal 신뢰해서 그냥 응답해도 되지만, 안전하게 다시 조회. 자주 쓰면 캐시 도입.

### 다음 단계 — Step B: 문제 CRUD + TestCase
- ProblemService / ProblemController + DTO (CreateProblemRequest, ProblemDetailResponse, ProblemListResponse)
- TestCase 업로드: ProblemController 내부 또는 별도 TestCaseController. JSON으로 한 번에 N개 입력 받기
- 권한: 문제 출제는 ROLE_ADMIN 필요 (`@PreAuthorize("hasRole('ADMIN')")`) → SecurityConfig에 `@EnableMethodSecurity` 추가

---

# Phase 2 후반 — Step B: 문제 CRUD + TestCase (2026-05-02 진행)

## 설계 결정
- **문제 출제/수정/삭제**: ADMIN만 (method security)
- **문제 조회**: 인증된 사용자. `is_public=false`는 ADMIN만 노출
- **TestCase**: 문제 생성 시 함께 입력 가능, 또는 별도 엔드포인트로 추가/삭제
- **샘플 TC**: 일반 사용자는 `is_sample=true`만 응답에 포함, 채점용 TC는 ADMIN 응답에만
- **시드 admin**: ApplicationRunner로 `admin/admin1234` 자동 생성(없을 때만) — 검증 편의

## 엔드포인트
- `POST /api/problems` (ADMIN) — 문제 + 초기 TC들 한꺼번에
- `GET /api/problems` (인증) — 목록 (page/size 단순 페이징)
- `GET /api/problems/{id}` (인증) — 상세 (샘플 TC 포함)
- `PUT /api/problems/{id}` (ADMIN)
- `DELETE /api/problems/{id}` (ADMIN)
- `POST /api/problems/{id}/test-cases` (ADMIN) — TC 단건 추가
- `GET /api/problems/{id}/test-cases` (ADMIN) — 전체 TC 조회
- `DELETE /api/problems/{id}/test-cases/{tcId}` (ADMIN)

## 작업 순서

### B1. 엔티티 보강 + Method Security
- [ ] `Problem` 도메인 메서드: `update(...)`, `addTestCase(TestCase)`, `removeTestCase(Long)`
- [ ] `SecurityConfig` `@EnableMethodSecurity`
- [ ] `ApplicationRunner`로 admin 시드

### B2. Problem DTO
- [ ] `CreateProblemRequest` (+ 내부 `TestCaseRequest`)
- [ ] `UpdateProblemRequest`
- [ ] `ProblemListResponse`
- [ ] `ProblemDetailResponse` (sampleTestCases 포함)

### B3. TestCase DTO + Repository 보강
- [ ] `TestCaseRequest` (단건 추가용)
- [ ] `TestCaseResponse` (ADMIN용)
- [ ] `ProblemRepository`에 페이지네이션 / 권한별 조회 메서드 (필요 시)

### B4. Service
- [ ] `ProblemService`: create / list / detail / update / delete
- [ ] `TestCaseService`: add / list / delete

### B5. Controller
- [ ] `ProblemController`
- [ ] `TestCaseController` (problem 하위 경로)

### B6. 검증
- [x] build 성공
- [x] admin 시드 동작 → admin/admin1234 로그인 → 토큰 확보
- [x] alice(USER)가 문제 생성 시도 → 403 FORBIDDEN
- [x] admin이 A+B 문제 + TC 3개 생성 → 201
- [x] alice 목록 조회 → 200, public 1개
- [x] alice 상세 조회 → sample 2개만 포함 (채점 TC는 숨김)
- [x] admin TC 단건 추가 → 201 (총 4개)
- [x] alice TC 추가 시도 → 403
- [x] admin 전체 TC 조회 → 4개 모두 노출
- [x] admin 문제 수정 (PUT) → 200, 필드 반영
- [x] admin TC 삭제 (DELETE) → 204
- [x] admin 문제 삭제 (DELETE) → 204
- [x] 삭제 후 조회 → 404 PROBLEM_NOT_FOUND

## Step B 완료 (2026-05-02)

### 결과
- Problem/TestCase 도메인 12파일 추가 (DTO 6 + Service 2 + Controller 2 + 보강 2)
- 13개 시나리오 end-to-end 통과
- ROLE 기반 권한 분리 (admin/user) + 비공개 문제 가시성 격리 동작
- AdminSeeder로 admin/admin1234 자동 생성 (개발 편의)

### 트레이드오프 / 이슈
- **시드 admin 비밀번호 평문 노출**: 운영 시 환경변수 또는 별도 init 스크립트로 분리 필요. 현재는 README/문서로 경고 + 첫 로그인 시 변경 강제는 미구현.
- **검증 파이썬 파싱 우회**: Windows curl이 한글을 cp949로 인코딩 → fixture 파일을 `tasks/fixtures/`에 저장. 실제 한글 처리는 정상 (서버는 UTF-8 정상 처리).
- **Page<ProblemListResponse>**: 엔드포인트가 Spring Page를 그대로 직렬화 → 프론트와 계약 일치 위해 추후 `PageResponse<T>` 같은 래퍼 도입 권장 (Phase 4 프론트 작업 시).
- **AccessDenied vs Unauthenticated**: 401과 403을 같은 ErrorCode로 묶었던 버그 발견 → FORBIDDEN(A005) 추가, accessDeniedHandler/GlobalExceptionHandler 양쪽 분리.

### 다음 단계 — Step C: Submission + Judge0Client (다음 세션)
- `Judge0Client` (RestClient): POST /submissions?wait=true
- 단건 동기 채점 → 결과 → DB 저장
- `SubmissionService`: 사용자 코드 + 문제의 채점용 TC들로 순차 실행, 첫 실패 시 단축
- `SubmissionController`: POST /api/submissions, GET /api/submissions/{id}, GET /api/submissions/me

---

# Phase 2 후반 — Step C: Submission + Judge0Client (2026-05-02 진행)

## Judge0 접근 방식: SSH 터널
- 사용자가 별도 터미널에서 `ssh -L 2358:localhost:2358 ubuntu@43.200.90.85` 실행 유지
- application.yml의 `judge0.url`을 `http://localhost:2358`로 변경
- 운영 배포 시 `JUDGE0_URL` 환경변수로 오버라이드

## 설계 결정
- **동기 채점** (Judge0 `wait=true`): TC마다 단건 POST 반복. 첫 실패 시 단축.
- **권한**: 제출은 인증된 사용자(USER/ADMIN). `GET /api/submissions/{id}`는 본인 또는 ADMIN만.
- **Submission.status 흐름**: PENDING → JUDGING → 최종 status (ACCEPTED/WRONG_ANSWER/...)
- **첫 실패 단축**: TC 1개라도 틀리면 그 status로 종료. AC = 모든 TC 통과.
- **runtime/memory**: 가장 큰 값을 기록 (max). Judge0 응답의 `time`(초), `memory`(KB).

## Status 매핑
| Judge0 status.id | Submission.Status |
|---|---|
| 1 | PENDING |
| 2 | JUDGING |
| 3 | ACCEPTED |
| 4 | WRONG_ANSWER |
| 5 | TIME_LIMIT |
| 6 | COMPILE_ERROR |
| 7-12 | RUNTIME_ERROR |
| 13, 14 | SYSTEM_ERROR |

## 작업 순서

### C1. Judge0 클라이언트 인프라
- [ ] `dev.algoj.global.client.dto.Judge0SubmissionRequest` (record)
- [ ] `dev.algoj.global.client.dto.Judge0SubmissionResponse` (+ inner Status)
- [ ] `dev.algoj.global.client.Judge0StatusMapper`
- [ ] `dev.algoj.global.config.Judge0Config` (RestClient bean)
- [ ] `dev.algoj.global.client.Judge0Client` (`submitAndWait(...)`)
- [ ] application.yml `judge0.url` → `${JUDGE0_URL:http://localhost:2358}`

### C2. Submission API
- [ ] `dev.algoj.domain.submission.dto.SubmitRequest` (problemId, language, sourceCode)
- [ ] `dev.algoj.domain.submission.dto.SubmissionResponse` (요약: id, status, runtime, memory)
- [ ] `dev.algoj.domain.submission.dto.SubmissionDetailResponse` (소스코드 포함)
- [ ] `SubmissionRepository` 보강 (`findAllByUserIdOrderByCreatedAtDesc`)
- [ ] `dev.algoj.domain.submission.service.SubmissionService`
- [ ] `dev.algoj.domain.submission.controller.SubmissionController` (POST /, GET /me, GET /{id})
- [ ] `ErrorCode`: SUBMISSION_NOT_FOUND, JUDGE0_ERROR, NO_TEST_CASES

### C3. 검증 (SSH 터널 전제)
- [x] SSH 터널 띄움 확인 (`curl localhost:2358/about`)
- [x] build → bootRun
- [x] admin이 A+B 문제 + TC 3개 생성 (재실행)
- [x] alice가 정답 코드 제출 (Python3) → ACCEPTED, 16ms/3264KB
- [x] alice가 오답 코드 제출 → WRONG_ANSWER
- [x] alice가 무한루프 코드 제출 → TIME_LIMIT, msg="Time limit exceeded"
- [x] alice가 컴파일 에러 코드(C++) → COMPILE_ERROR + g++ 메시지
- [x] GET /api/submissions/me → 본인 제출 11건
- [x] bob 회원가입 후 alice 제출 조회 시도 → 403 A005
- [x] admin이 alice 제출 조회 → 200 (전체 가시성)
- [x] 토큰 없이 제출 → 401 A004

## Step C 완료 (2026-05-02)

### 결과
- Submission/Judge0 풀스택 12파일 추가 (DTO 4 + Service/Controller 2 + Judge0 client 4 + ErrorCode 보강 + AdminSeeder 보존)
- 4가지 채점 결과(AC/WA/TLE/CE) end-to-end 동작 확인
- 권한 분리: 본인/관리자만 상세 조회, 그 외 USER는 403

### 트레이드오프 / 이슈
- **동기 채점**: TC 개수만큼 직렬 호출 → 클라이언트 응답 지연. Phase 3에서 RabbitMQ 비동기 + WebSocket으로 개선.
- **`wait=true`**: Judge0 단건 폴링 회피, 단 응답 시간이 cpu_time_limit + 컴파일 시간만큼 늘어남.
- **base64 인코딩 오버헤드**: 약 33% 본문 증가. 작은 코드는 무시 가능.
- **CE 메시지의 비-UTF-8 surrogate**: g++ ANSI 시퀀스가 디코딩 후에도 일부 깨진 글자로 남음. 운영 시 정규화/이스케이프 처리 검토.
- **Page<SubmissionResponse>**: ProblemController와 마찬가지로 Spring Page를 그대로 직렬화. 추후 PageResponse 래퍼로 통일 권장.

### 도중에 잡은 버그 (lessons.md에 기록)
1. `RestClient.builder()` 직접 사용 시 메시지 컨버터 누락 → `RestClient.Builder` 빈 주입
2. record `body(record)` 직렬화 불안정 → ObjectMapper로 String 변환 후 body
3. Judge0 비-UTF-8 출력에 422 → `base64_encoded=true` 필수
4. `Base64.getDecoder()`는 newline 거부 → `getMimeDecoder()` 사용

---

# Phase 2 완료 ✅ (2026-05-02)

**전체 통계:**
- 작성 파일: 38 (Step1: 13 + Step A: 14 + Step B: 12 + Step C: 12, 보강/수정 제외)
- 검증 시나리오: 30 (8 + 13 + 9)
- 잡은 버그: 6개 (lessons.md 누적)

**다음 Phase:**
- Phase 3 (비동기 채점): RabbitMQ 도입 → submission queue → WebSocket(STOMP) 실시간 결과 push + Redis 캐시
- Phase 4 (프론트엔드): Next.js 14 + Monaco Editor
- Phase 5 (운영): 도메인/HTTPS/백업/스터디원 7명 가입

---

# Phase 4: Next.js 14 프론트엔드 (2026-05-02 진행)

## 목표
백엔드 API 전 영역(인증/문제/제출)을 사용할 수 있는 프론트 MVP. 4/28 백준 종료 전 스터디원 7명 온보딩 가능 상태.

## 스택 / 결정사항
- **Next.js 14 (App Router)** + TypeScript strict
- **Tailwind CSS + shadcn/ui** — 빠른 컴포넌트, 다크모드 기본
- **TanStack Query v5** — 서버 상태 / 캐시
- **Zustand (persist)** — 인증 토큰 클라이언트 상태
- **@monaco-editor/react** — 코드 에디터
- **react-hook-form + zod** — 폼 / 검증
- **fetch wrapper (`lib/api.ts`)** — Authorization 헤더 자동, 401 시 logout
- **위치**: 모노레포 — `oj/frontend/` 서브디렉토리 (백엔드와 같은 git repo)
- **패키지 매니저**: npm (pnpm 미설치, 추가 설치 비용 회피)
- **인증 토큰 저장**: localStorage (Phase 5에서 httpOnly cookie 강화)
- **포트**: 3000 (백엔드 CORS 이미 등록됨)

## 페이지 구조
- `/` 홈 (소개 + 문제 목록 진입)
- `/(auth)/login`, `/(auth)/signup`
- `/(main)/problems` 문제 목록 (페이지네이션)
- `/(main)/problems/[id]` 문제 상세 + Monaco 에디터 + 제출
- `/(main)/submissions/me` 내 제출 목록
- `/(main)/submissions/[id]` 제출 상세 (코드 + 결과)
- `/(admin)/admin/problems/new` 문제 생성 (ADMIN 전용, 후순위)

## 작업 순서

### D1. 프로젝트 셋업
- [x] `frontend/` 생성, `create-next-app@14 --ts --tailwind --app --src-dir --eslint`
- [x] **Tailwind v4 업그레이드** (shadcn 4.6 base-nova가 TW v4 요구) + shadcn init + button/input/label/card/badge/sonner/select/textarea
- [x] Root providers: TanStack Query + Toaster(sonner) + ThemeProvider(dark) — `src/components/providers.tsx`
- [x] `.env.local`: `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`
- [x] `lib/api.ts` (fetch wrapper, ApiError, 401 시 logout) + `types/api.ts` (백엔드 DTO 타입 전체)
- [x] `npm run dev` → 200, 빌드 통과, /api/health fetch 동작

### D2. 인증
- [x] Zustand auth store (persist, `algoj-auth`) — accessToken / refreshToken / user
- [x] `/login`, `/signup` 폼 (rhf+zod, 백엔드 fieldErrors → form.setError)
- [x] 로그인 성공 → `/problems` 이동
- [x] 보호 라우트: `(main)/layout.tsx`에서 토큰 체크 + me 자동 fetch
- [x] Header: username + 로그아웃 + ADMIN 전용 메뉴 표시

### D3. 문제 목록 / 상세 + 제출
- [x] `/problems`: 목록 fetch + 페이지네이션 (Spring Page 호환)
- [x] `/problems/[id]`: 좌측 본문(설명/입출력/샘플 TC) + 우측 Monaco + 언어 선택(5종) + starter 코드 + 제출 버튼
- [x] 제출 → toast 표시 + `/submissions/[id]` 이동

### D4. 내 제출 / 제출 상세
- [x] `/submissions/me`: 본인 제출 목록 + StatusBadge (AC=초록, WA=빨강, TLE=주황, CE=노랑)
- [x] `/submissions/[id]`: 메타 카드 4개 + errorMessage (있을 때) + 읽기전용 Monaco

### D5. (선택) ADMIN 문제 생성 — **다음 세션으로 미룸**
- [ ] `/admin/problems/new`: 폼 + TC 동적 추가/삭제
- [ ] role 체크, USER 접근 시 `/problems`로 redirect

### D6. 검증
- [x] `npm run build` 통과 (9 routes — `/`, `/login`, `/signup`, `/problems`, `/problems/[id]`, `/submissions/me`, `/submissions/[id]`, `/_not-found`, `_error`)
- [x] `npm run dev` 200 OK
- [x] 백엔드 `/api/health` GET 200 (CORS preflight 200, frontend → backend 통신 가능)
- [ ] **브라우저 검증 (사용자 수동)**: signup → login → 문제 풀이 → 제출 → 결과 확인

## 트레이드오프 / 이슈
- **Tailwind v3 → v4 강제 전환**: shadcn 4.6 "base-nova" 스타일이 TW v4 (CSS-first @theme) 가정. Next.js 14 scaffolding이 깔아둔 v3와 충돌해서 v4로 업그레이드 (`@tailwindcss/postcss`, `@import "tailwindcss"`, `tailwind.config.ts` 삭제, globals.css OKLCH 토큰 전환).
- **`@base-ui/react` 채택 (vs Radix UI)**: 새 shadcn은 base-ui 기반. asChild prop 없음 → Link 스타일링 시 `buttonVariants()` 호출로 className 합성하는 패턴 사용.
- **shadcn `form` 컴포넌트 install hang**: 원인 미상 — react-hook-form 직접 사용으로 우회 (form abstraction 불필요).
- **localStorage JWT**: XSS 취약. Phase 5에서 Next.js Route Handler 프록시 + httpOnly cookie로 전환 예정.
- **Spring Page<T> 직렬화**: `content/totalElements/...` 구조 그대로 사용. PageResponse 래퍼 미도입.
- **CORS allowed origins**: `localhost:3000` 한 개. 운영 도메인 확정 시 백엔드 yml 추가 필요.

## Phase 4 진행 상황 (2026-05-02)

### 작성 파일 (frontend/)
- 설정: `package.json`(deps 15개 추가), `postcss.config.mjs`(TW v4), `globals.css`(OKLCH 토큰), `.env.local`
- shadcn UI: `button/input/label/card/badge/sonner/select/textarea` (8개)
- 타입: `types/api.ts` (백엔드 DTO 전체)
- API client: `lib/api.ts`, `lib/auth-api.ts`, `lib/problems-api.ts`, `lib/submissions-api.ts`
- 상태: `lib/auth-store.ts` (zustand persist)
- 컴포넌트: `components/providers.tsx`, `components/header.tsx`, `components/code-editor.tsx`, `components/status-badge.tsx`
- 페이지: `app/page.tsx`, `app/layout.tsx`, `app/(auth)/{layout,login,signup}`, `app/(main)/{layout,problems,problems/[id],submissions/me,submissions/[id]}`

### 다음 세션 (Phase 4 마무리 / Phase 5 진입)
1. **D5 — ADMIN 문제 생성 페이지** (TC 동적 폼)
2. 사용자 브라우저 검증 결과 반영 (버그 발견 시)
3. (선택) Refresh token 자동 회전 — accessToken 만료 시 refresh 호출
4. **Phase 5 진입**: 도메인 / HTTPS / 백업 / 스터디원 7명 온보딩
