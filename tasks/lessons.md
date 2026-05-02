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
