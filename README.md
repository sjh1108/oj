# AlgoJ — 스터디용 온라인 저지

알고리즘 스터디를 위한 자체 호스팅 온라인 저지(Online Judge)입니다.
문제 출제부터 제출·채점·풀이 공유까지 스터디에 필요한 흐름을 한 곳에서 처리합니다.

- **웹**: https://oj-oui2.vercel.app (가입은 스터디원 대상)
- **API**: Lightsail 박스에서 블루-그린 무중단 배포

## 주요 기능

- **문제**: Markdown + KaTeX 수식 지원 출제, 태그/난이도 분류, 단일 `.md` 파일로 문제 업로드/다운로드
- **채점**: [Judge0](https://judge0.com/) 기반 격리 실행 (Python·C++·Java·C·JavaScript), IOI 스타일 서브태스크 부분 점수, 재채점
- **채점 큐**: RabbitMQ durable 큐 — 재시작해도 채점 유실 없음, 워커 크래시 시 자동 재전달, 실패 메시지는 DLQ 격리
- **탐색**: 제목 검색, 난이도/태그/풀이 상태(맞음·도전 중·안 풂) 필터, 사용자별 해결 표시
- **풀이 공유**: 문제를 맞힌 사람에게만 다른 사람의 정답 코드 공개
- **테스트케이스 생성기**: 제너레이터 + 모범 답안 코드를 실행해 테스트케이스 자동 생성
- **Discord 봇**: 계정 연동, 비밀번호 재설정, `/서버상태` 모니터링, master 머지 시 업데이트 자동 공지
- **계정**: JWT(access + silent refresh) 인증, 관리자 회원 관리

## 아키텍처

```
Vercel (Next.js) ──HTTPS──▶ nginx ──▶ Spring Boot API (blue/green :8081|:8082)
                                        │           │
                                        │           ├─▶ MySQL 8 (Docker)
                                        │           ├─▶ RabbitMQ 4 (judge.queue → 채점 워커)
                                        │           └─▶ Judge0 (:2358, 코드 실행 샌드박스)
                                        └── Discord 봇 (host network, /api/internal/** 호출)
```

- 제출 → `judge.queue`에 적재 → 리스너 워커(기본 동시성 2)가 Judge0로 채점 → 결과 저장, 프론트는 폴링으로 갱신
- DB 스키마는 **Flyway**(`src/main/resources/db/migration/`)가 관리하고 Hibernate는 `validate`만 수행

## 기술 스택

| 영역 | 스택 |
|------|------|
| 백엔드 | Java 21, Spring Boot 3.5 (Web·Security·Data JPA·AMQP·Validation·Actuator), Flyway, JJWT |
| 프론트엔드 | Next.js 14, React 18, TypeScript, Tailwind CSS 4, TanStack Query, Monaco Editor, react-markdown + KaTeX |
| 인프라 | MySQL 8, RabbitMQ 4, Judge0, Docker Compose, nginx, GitHub Actions CI/CD |
| 봇 | Node.js 20, discord.js 14 |

## 저장소 구조

```
├── src/                  # Spring Boot 백엔드 (dev.algoj)
│   ├── domain/           #   user · problem · submission · run
│   └── global/           #   config · security · Judge0 클라이언트 · 모니터링
├── frontend/             # Next.js 프론트엔드
├── discord-bot/          # Discord 봇 (연동/비밀번호/서버상태/배포공지)
├── deploy/               # 배포 자료 (compose, nginx, blue-green 스크립트, 운영 가이드)
├── scripts/dev.sh        # 로컬 백엔드 실행 스크립트
└── .github/workflows/    # ci.yml (검증) · cd.yml (배포)
```

## 로컬 개발

### 0. 사전 준비

- JDK 21, Node 20+, Docker
- Judge0 인스턴스 (로컬 실행 또는 운영 박스로 SSH 터널: `ssh -L 2358:localhost:2358 <박스>`)

### 1. 환경 변수

```bash
cp .env.example .env.dev   # 값 채우기 (DB, JWT_SECRET, JUDGE0_URL 등)
```

### 2. 인프라 (MySQL + RabbitMQ)

```bash
cd deploy
docker compose --env-file ../.env.dev up -d   # mysql :3306, rabbitmq :5672 (+관리 UI :15672)
```

### 3. 백엔드

```bash
./scripts/dev.sh           # .env.dev 로드 후 bootRun (dev 프로필)
```

첫 부팅 시 Flyway가 스키마를 자동 생성합니다. dev 프로필은 기본으로 `admin / admin1234` 계정을 시드합니다.

### 4. 프론트엔드

```bash
cd frontend
npm ci
npm run dev                # http://localhost:3000, API는 NEXT_PUBLIC_API_BASE_URL (기본 :8080)
```

## 테스트

```bash
./gradlew test             # 백엔드 전체 (통합 테스트는 MySQL 필요 — CI와 동일)
./gradlew test --tests 'dev.algoj.domain.*' --tests 'dev.algoj.global.*'
                           # DB 없이 실행 가능한 단위/H2 테스트만
cd frontend && npm run lint && npm run build
```

- 리포지토리 검색 테스트는 MySQL 모드 H2로 돌아가므로 Docker 없이 실행됩니다.
- 엔티티를 바꿀 때는 `SchemaDdlGenerator` 테스트로 DDL을 뽑아 diff를 확인하고 `db/migration/V<N>__*.sql`을 추가하세요.

## 배포

`master`에 머지되면 GitHub Actions(`cd.yml`)가 자동으로:

1. 백엔드 테스트 재실행 → API·봇 이미지 빌드 후 GHCR push
2. 박스에 SSH 접속해 MySQL/RabbitMQ 기동 확인 → **블루-그린 배포** (헬스체크 통과 시에만 nginx 전환, 실패 시 자동 롤백)
3. 배포 성공 시 머지된 PR 본문으로 **Discord 업데이트 공지**

Flyway 마이그레이션은 앱이 부팅하면서 자동 적용되므로 스키마 변경에 별도 서버 작업이 필요 없습니다.
서버 초기 세팅, nginx/TLS, 봇 운영 등 자세한 내용은 **[deploy/README.md](deploy/README.md)** 참고.

## Discord 봇 명령

| 명령 | 설명 |
|------|------|
| `/연동 <코드>` | OJ 계정 페이지에서 발급한 코드로 디스코드 계정 연동 |
| `/비밀번호분실` | 연동된 계정의 임시 비밀번호 발급 (본인에게만 표시) |
| `/서버상태` | DB · Judge0 · 채점 큐(대기/워커/DLQ) · 제출 현황 · JVM 상태 확인 |

배포 공지는 명령이 아니라 자동입니다 — `DISCORD_ANNOUNCE_CHANNEL_ID`가 설정된 채널로 머지된 PR 내용이 올라갑니다.
