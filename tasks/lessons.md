# Lessons (oj project)

세션 종료 시 마주친 패턴/실수를 짧게 기록. 다음 세션 시작 시 이 파일을 먼저 읽기.

---

## 2026-05-02

### Spring Boot 3.5 + Hibernate 6 dialect
- `hibernate.dialect: org.hibernate.dialect.MySQL8Dialect` 명시는 deprecated. 그냥 비우면 드라이버 메타데이터로 자동 선택됨.
- 굳이 적어야 할 때는 `org.hibernate.dialect.MySQLDialect` 사용.

### bootRun 백그라운드 실행 (Git Bash)
- `./gradlew bootRun > log 2>&1 &` 실행 시 부모 셸이 즉시 exit 0으로 종료되어 "프로세스 죽었나?"로 오해하기 쉬움.
- 실제 프로세스는 살아있고 8080을 LISTENING 함. `netstat -an | grep :8080` 또는 `curl localhost:8080/...`로 확인할 것.
- 종료는 `netstat -aon | grep :8080.*LISTENING` → PID 추출 → `taskkill //F //PID <pid>`.

### MySQL 컨테이너 포트
- 로컬 PostgreSQL 충돌 회피 위해 `oj-mysql` 컨테이너는 `3307:3306`. application.yml의 `localhost:3307` 고정. 잊지 말 것.

### 패키지 명명
- 사용자는 SSAFY 종속 네이밍을 거부함 (`com.ssafy.*` X). 장기 프로젝트 전제.
- 현재 채택: `dev.algoj` (algorithm + judge 축약, 도메인 미정 상태에서 채택).
- 향후 도메인 확정 시 일괄 리팩터링 가능 (지금 단계는 비용 작음).

### Spring Security + permitAll 임시 셋업
- `spring-boot-starter-security`만 의존성에 있어도 모든 엔드포인트 401. `SecurityFilterChain` 빈으로 `anyRequest().permitAll()` 명시 필요.
- STATELESS + csrf disable + formLogin disable 조합이 JWT 기반 API 서버의 기본 출발점.

### RabbitMQ/Redis 의존성 + 컨테이너 미기동
- spring-boot-starter-amqp / spring-boot-starter-data-redis가 classpath에 있어도 시작 시 lazy connection이라 startup 자체는 통과.
- spring-session-data-redis도 마찬가지였음. Phase 3에서 실제 사용 시점에 docker-compose에 redis/rabbitmq 추가하면 됨.

### Workflow
- CLAUDE.md "Plan First": 비자명 작업은 `tasks/todo.md`에 체크리스트 만들고 사용자 확인 받은 후 실행. 이번에 효과 좋았음 — 사용자가 "ㄱㄱ" 한 번에 신뢰하고 위임.
- 작업 완료 후 Review 섹션 채우기 + lessons.md 갱신은 잊기 쉬우니 마지막 task로 명시 관리.

---

### Windows Git Bash + curl 한글 인코딩
- `curl -d '{"title":"한글"}'` 형태로 쓰면 콘솔 코드페이지(cp949)로 인코딩되어 서버에서 `Invalid UTF-8 start byte 0xb5` 에러.
- 해결: 외부 JSON 파일을 만들고 `--data-binary @file.json`으로 전송. 파일은 UTF-8로 저장되므로 안전.
- 검증 fixture는 `tasks/fixtures/` 아래에 모아두기.

### `/tmp` 경로 비일관성
- Git Bash, curl(Windows native), Python(Windows) 셋이 보는 `/tmp` 경로가 모두 다름. `curl -o /tmp/r.json` 후 `python -c "open('/tmp/r.json')"`은 실패.
- 해결: 셸 변수로 직접 받아 `echo "$VAR" | python ...`로 파이프. 파일 매개 안 쓰는 게 안전.

### Spring Security AccessDenied vs Unauthenticated 분리
- `AuthenticationEntryPoint`: 인증 정보 자체가 없을 때 (401)
- `AccessDeniedHandler` + `@PreAuthorize` 거부: 인증은 됐지만 권한 부족 (403)
- 둘을 같은 ErrorCode로 묶지 말 것. 사용자 디버깅 어려움. ErrorCode `UNAUTHENTICATED`(401)와 `FORBIDDEN`(403) 분리.
- `@PreAuthorize` 거부는 메서드 시큐리티 단계에서 던져지므로 SecurityFilterChain의 `accessDeniedHandler`만으로 부족할 수 있음 — `GlobalExceptionHandler`에서도 `AccessDeniedException` 처리 추가 필요 (양쪽 다 잡기).

### Spring Boot DevTools 자동 재시작
- gradle bootRun 으로 띄운 상태에서 src/main/java 파일을 외부 도구로 수정해도 자동 재컴파일 안 됨 (devtools는 classes 디렉토리를 watch).
- 수동으로 kill/restart 필요. 또는 `gradle compileJava`를 추가 트리거.

### bootRun 첫 실행 후 빠른 재실행 시 "Bad address: listen"
- 이전 process(LiveReload 35729)이 완전히 정리되지 않은 상태에서 재bind 시도하면 발생.
- 보통 한 번 더 시도하면 성공. 또는 1~2초 대기 후 재시작.

---

### Spring RestClient: `RestClient.builder()` 직접 사용 시 메시지 컨버터 누락
- `RestClient.builder()`로 새로 빌더를 만들면 Spring Boot가 자동 등록한 `HttpMessageConverters`(Jackson 등)가 빠진 채 빌드됨.
- 결과: `body(record)` 호출이 직렬화되지 않거나 빈 본문이 가는 케이스 발생.
- 해결: `RestClient.Builder` **빈을 주입**받아서 `baseUrl`, `requestFactory`만 추가해 빌드.
- 그래도 record 직렬화 안 되는 경우는 `ObjectMapper`로 직접 String 만든 뒤 `.body(json)`이 가장 신뢰성 있음.

### Judge0 base64_encoded=true 필수
- 기본 호출(`base64_encoded=false`)은 source_code/stdin/stderr/compile_output가 모두 UTF-8이어야 함.
- C/C++ 컴파일 에러 메시지 등이 비-UTF-8 바이트를 포함하면 422 + `"some attributes for this submission cannot be converted to UTF-8, use base64_encoded=true"`.
- 운영에서는 항상 `base64_encoded=true`로 호출. 클라이언트에서 인코딩(Base64.getEncoder())/디코딩 처리.

### Java `Base64.getDecoder()` vs `getMimeDecoder()`
- 표준 디코더는 padding/whitespace에 strict — 줄바꿈이 있으면 `IllegalArgumentException`.
- Judge0 응답에 trailing `\n`이 자주 끼어 있어 표준 디코더로는 실패.
- 해결: `Base64.getMimeDecoder()` — 화이트스페이스 무시하고 디코딩.

### `@JsonNaming`/`@JsonProperty` 우선순위
- record + Jackson에서 snake_case 변환은 `@JsonNaming(SnakeCaseStrategy)`가 가장 안정적 (component마다 `@JsonProperty` 붙이는 것보다).
- 응답 디코딩 시에도 같은 어노테이션이 양방향(serialize/deserialize)으로 동작.

---

## 2026-05-02 (Phase 4 — Next.js)

### shadcn 4.x ↔ Tailwind 버전 매칭
- `npx shadcn@latest init`은 기본 `base-nova` 스타일을 사용하며 Tailwind v4 가정 (CSS-first `@theme`, `@import "tailwindcss"`).
- Next.js 14 scaffolding(`create-next-app@14 --tailwind`)은 TW v3 설치. 그대로 두면 `border-border` 등 토큰 클래스가 정의되지 않아 빌드 깨짐.
- 해결: `tailwindcss@^4 @tailwindcss/postcss@^4`로 업그레이드, `postcss.config.mjs`의 plugin을 `@tailwindcss/postcss`로, `tailwind.config.ts` 삭제, `globals.css`를 `@import "tailwindcss"; @theme inline { ... }` 형태로 변경.
- 향후 새 프로젝트는 처음부터 TW v4로 셋업.

### base-ui 기반 shadcn에는 `asChild` prop 없음
- 새 shadcn `Button`은 `@base-ui/react`(base-ui.com) 위에 만들어져 Radix Slot을 사용하지 않음 → `<Button asChild><Link/></Button>` 패턴 깨짐.
- 해결: `buttonVariants()`을 export하고 `<Link className={cn(buttonVariants(), ...)}>` 패턴으로 className 합성. 또는 base-ui의 `render` prop 사용.

### shadcn `form` 컴포넌트 install이 hang
- `npx shadcn@latest add form` 명령이 "Checking registry"에서 멈추는 현상 (재시도/-y/--overwrite 모두 무력).
- 원인 미확인. 우회: shadcn form abstraction 없이 react-hook-form을 직접 사용 (`<form onSubmit={form.handleSubmit(...)}>` + Input/Label/Button 조합). 코드량 거의 동일하고 의존도 한 단계 줄어듦.

### Next.js 14 + create-next-app이 깐 layout.tsx의 함정
- shadcn init이 layout.tsx를 수정하면서 `Geist`(Google fonts에 없음)를 `next/font/google`에서 import하는 코드를 끼워넣음 → 빌드 실패 ("Unknown font Geist").
- 해결: 원래 `localFont` 기반(`./fonts/GeistVF.woff`, `GeistMonoVF.woff`)으로 되돌리고 Google import 제거.

### Git Bash + curl `-w "$path = ..."` 인자에서 MSYS 경로 변환
- `$path=/`인 채로 curl `-w` template에 `$path` 끼우면 MSYS가 leading `/`를 `C:/Program Files/Git/`로 변환 → 출력이 깨짐.
- 해결: 단일 따옴표로 인용하거나 `MSYS_NO_PATHCONV=1` 설정. 검증 스크립트 작성 시 주의.

### enum / DTO 필드명은 추측하지 말고 항상 백엔드 소스 확인
- 프론트 types/api.ts 만들 때 백엔드 enum/필드명을 확인 안 하고 통상적인 값으로 추측 → 런타임에 mismatch 발견 시점이 늦어짐.
- 이번 사고:
  - `Difficulty`: 백엔드 BRONZE/SILVER/GOLD/PLATINUM/DIAMOND ↔ 프론트 추측 EASY/MEDIUM/HARD → 배지가 빈 채로 표시되다가 admin 문제 생성 시 Jackson 역직렬화 500.
  - `timeLimit`/`memoryLimit` ↔ 추측 `timeLimitMs`/`memoryLimitKb` → 문제 상세에서 `undefined ms` 표시.
  - `inputDescription`/`outputDescription` ↔ 추측 `inputFormat`/`outputFormat` → 빈 카드.
  - `runtime`/`memory` ↔ 추측 `runtimeMs`/`memoryKb` → 제출 상세 시간/메모리 0 표시.
- 룰: 새 DTO 정의할 때 무조건 해당 record/entity 파일을 먼저 읽고 그대로 옮기기. 추측 금지. 단위(ms/KB)도 필드명에 안 들어 있으면 추가 안 하기 (백엔드 컨벤션 따라가기).
- 빠른 검증: `curl ... | python -m json.tool`로 실제 응답 키 확인 후 타입 정의.

### Zustand `persist` hydration race — 풀 페이지 새로고침 시 보호 라우트 layout이 잘못 redirect
- 보호 layout 패턴: `useEffect(() => { if (!accessToken) router.replace("/login"); }, [accessToken])`
- Zustand persist는 localStorage를 **비동기**로 읽음. 첫 React 마운트 직후엔 store 초기값(`null`) 상태 → useEffect가 그걸 보고 `/login` redirect → 직후 hydrate 완료되지만 이미 navigated.
- SPA 내부 `<Link>` / `router.push()`로는 layout이 unmount되지 않아 안 보이지만, **URL 직접 타이핑/F5 새로고침 = 풀 페이지 SSR**에서 hydration race가 노출됨. 사용자는 "로그인 멀쩡한데 왜 로그인 페이지로 튕기지?" 혼란.
- 해결: `useAuthStore.persist.hasHydrated()` + `onFinishHydration`으로 hydration 완료 후에만 토큰 체크. hydration 전엔 `return null` (또는 스켈레톤).
- 디버깅 단서: localStorage `algoj-auth`는 그대로 남아있는데 페이지는 /login에 있음 → 토큰이 없어서 redirect된 게 아니라 **타이밍** 때문.
- `@base-ui/react/select` 기반. value/onValueChange API는 맞지만 React 18 + portal 조합에서 onValueChange 콜백이 안 발화하거나 트리거 텍스트가 갱신 안 되는 케이스 관찰됨.
- 단순 form control(언어 선택 5개 옵션 등)은 native `<select>`로 가는 게 안정적. shadcn 컴포넌트는 진짜로 커스텀 키보드 내비게이션/검색이 필요한 콤보박스에서만 쓸 것.

### shadcn 4.x base-nova `Input`/`Textarea`에 `forwardRef` 누락 → react-hook-form 바인딩 깨짐
- shadcn CLI가 깐 `src/components/ui/input.tsx`, `textarea.tsx`가 plain function component (`function Input({...}) { return <InputPrimitive .../> }`).
- React 18에서 function component는 ref를 받을 수 없음 → react-hook-form의 `register()`가 반환하는 ref가 silent하게 drop → input 값이 form state에 바인딩 안 됨.
- 증상: 사용자가 필드에 무엇을 입력하든 `form.getValues()`에 빈 문자열로 들어가서 zod 검증이 빈 값 기준으로 실패 → 모든 필드에서 `min(N)` 또는 default `"Invalid input"` 메시지 표시. 사용자는 "왜 valid 한 입력에 invalid input이 뜨지?"로 인식.
- **콘솔 단서**: `Warning: Function components cannot be given refs. Did you mean to use React.forwardRef()? Check the render method of \`SignupPage\`. at Input ...` — 이 경고 보면 즉시 의심할 것.
- **해결**: shadcn이 깔아둔 모든 form-related primitive(Input/Textarea/Select 트리거/Checkbox 등)를 `React.forwardRef`로 감싸고 ref를 base-ui primitive에 전달.
- **근본 원인**: 새 shadcn은 React 19 native ref-as-prop 가정 (forwardRef 불필요). React 18 + rhf 조합에선 깨짐. 향후 React 19 업그레이드 시 forwardRef wrapper 제거 가능.
- v3: `z.string().min(8, "8자 이상")` 두 번째 인자에 그냥 string 가능.
- v4: 같은 코드가 silent하게 무시되고 default 영어 메시지("Invalid input")가 표시됨. 사용자는 한글 message 안 보이고 영어 generic만 봄.
- 해결: `{ error: "..." }` 객체 형태로 변경. 또는 `{ message: "..." }`도 호환되지만 `error`가 canonical.
  - `z.string().min(3, { error: "3자 이상" })`
  - `z.string().regex(pattern, { error: "..." })`
  - `z.email({ error: "..." })` (top-level 권장; `z.string().email()`은 deprecated)
- 혼동 포인트: 폼이 멀쩡히 submit되고 백엔드가 200/400 정상 응답해도, 프론트에서 zod resolver가 일찍 차단하면 사용자 보기엔 "필드별 한글 메시지가 안 떠요"로 인식됨.

### `npm run dev`가 도는 동안 `npm run build` 절대 돌리지 말 것
- `next build`가 `.next/` 디렉토리를 production 산출물로 덮어쓰면, 같은 디렉토리를 watch 중인 dev 서버의 `static/chunks/*.js`, `static/css/*.css`가 404로 떨어짐.
- 증상: 페이지는 SSR로 200 응답하지만 JS 자산 404 → React hydration 실패 → form `onSubmit` handler가 안 붙어서 native HTML form submit 발동 → `<form>`(action 없음)이 GET으로 현재 URL에 query string 붙여 제출 → **비밀번호가 URL에 노출됨**.
- 해결: 빌드 검증은 별도 머신/디렉토리에서 하거나, 검증 끝나면 `rm -rf frontend/.next && npm run dev` 재시작.
- 예방: 검증 흐름은 `npm run dev` 한 가지로 통일 (TypeScript 에러는 `npx tsc --noEmit`만 돌리면 됨).
