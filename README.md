# AlgoJ — 스터디용 온라인 저지

알고리즘 스터디를 위한 자체 호스팅 온라인 저지(Online Judge)입니다.
문제 출제부터 제출·채점·풀이 공유까지 스터디에 필요한 흐름을 한 곳에서 처리합니다.

- **웹**: https://oj-oui2.vercel.app (가입은 스터디원 대상)
- **API**: Oracle Cloud Ampere 한 박스, blue-green 무중단 배포

## 주요 기능

- **문제**: Markdown + KaTeX 수식 지원 출제, 태그/난이도 분류, 단일 `.md` 파일로 문제 업로드/다운로드, 지문 이미지 S3 업로드
- **채점**: 자체 채점기 기반 격리 실행 — 제출마다 새 컨테이너 (Python·PyPy3·C++·Java·C·JavaScript), IOI 스타일 서브태스크 부분 점수, 재채점
- **채점 큐**: RabbitMQ durable 큐 — 재시작해도 채점 유실 없음, 워커 크래시 시 자동 재전달, 실패 메시지는 DLQ 격리, PENDING으로 남은 제출은 스위퍼가 주기적으로 재적재
- **탐색**: 제목 검색, 난이도/태그/풀이 상태(맞음·도전 중·안 풂) 필터, 사용자별 해결 표시
- **풀이 공유**: 문제를 맞힌 사람에게만 다른 사람의 정답 코드 공개
- **테스트케이스 생성기**: 제너레이터 + 모범 답안 코드를 실행해 테스트케이스 자동 생성
- **Discord 봇**: 계정 연동, 비밀번호 재설정, `/서버상태` 모니터링, master 머지 시 업데이트 자동 공지
- **계정**: JWT(access + silent refresh) 인증, 관리자 회원 관리

## 아키텍처

모든 구성 요소가 **Oracle Cloud Ampere 한 박스(aarch64, 2 OCPU / 12GB)** 에서 돕니다.
앱은 DB·브로커·채점기를 **주소(`.env`)로만** 알고 있어서 어디에 두든 설정만 바꾸면 됩니다 —
AWS에서 이 박스로 옮길 때 애플리케이션 코드를 거의 건드리지 않은 이유입니다.

```
                     Vercel (Next.js)
                           │ HTTPS  algoj.duckdns.org
                           ▼
┌────────── OCI Ampere (aarch64, 2 OCPU / 12GB) ──────────┐
│ nginx — TLS 종단 + blue-green 업스트림                    │
│   ├─ API   algoj-api-blue|green   127.0.0.1:8081|8082    │
│   └─ 내부 고정 :8080  ◀── Discord 봇 (host network)       │
│                                                          │
│ algoj-net (도커 네트워크 — 호스트 포트를 열지 않는다)       │
│   ├─▶ MySQL 8       DB_HOST=mysql       스키마는 Flyway   │
│   ├─▶ RabbitMQ 4    RABBITMQ_HOST       judge.queue      │
│   └─▶ judge-runner  JUDGE0_URL          채점 샌드박스      │
└──────────────────────────────────────────────────────────┘
```

- **무중단 배포는 한 박스 blue-green** — 새 컨테이너를 비어 있는 포트에 띄우고 `/api/health`가
  통과하면 nginx 업스트림을 갈아끼웁니다. 12GB라 JVM 두 개가 잠깐 겹쳐도 됩니다
  (2GB 시절에는 스왑 때문에 불가능해 박스를 두 대로 나눴었습니다 → [`deploy/redundancy.md`](deploy/redundancy.md))
- **채점기는 자체 구현** — Judge0 공식 이미지가 amd64 전용이라 arm64에서 돌지 않아, 앱이 실제로
  쓰는 Judge0 API 두 개만 구현해 대체했습니다. 격리는 제출마다 새 컨테이너(네트워크 없음·
  capability 제거·읽기전용 루트·메모리/PID 상한)로 얻습니다 →
  [`judge-runner/README.md`](judge-runner/README.md)
- MySQL·RabbitMQ는 **호스트 포트를 열지 않고** 도커 네트워크 안에서만 통신합니다.
  docker의 포트 publish는 iptables INPUT 체인을 우회하므로, 여는 순간 방화벽과 무관하게
  인터넷에 노출됩니다
- 제출 → `judge.queue` 적재 → 리스너 워커가 꺼내 채점 → 결과 저장, 프론트는 폴링으로 갱신.
  PENDING으로 남은 제출은 스위퍼가 1분 주기로 재적재합니다
- DB 스키마는 **Flyway**(`src/main/resources/db/migration/`)가 관리하고 Hibernate는 `validate`만 수행합니다
- 이전 경위는 [`deploy/oracle-cloud-migration.md`](deploy/oracle-cloud-migration.md),
  AWS 시절의 이중화·컴포넌트 분리 기록은 [`deploy/redundancy.md`](deploy/redundancy.md)와
  [`deploy/offload-components.md`](deploy/offload-components.md)에 남아 있습니다

## 기술 스택

| 영역 | 스택 |
|------|------|
| 백엔드 | Java 21, Spring Boot 3.5 (Web·Security·Data JPA·AMQP·Validation·Actuator), Flyway, JJWT, AWS SDK v2 (S3 호환 스토리지) |
| 프론트엔드 | Next.js 14, React 18, TypeScript, Tailwind CSS 4, TanStack Query, Monaco Editor, react-markdown + KaTeX |
| 인프라 | Oracle Cloud(Ampere/arm64), MySQL 8, RabbitMQ 4, 자체 채점기, Docker, nginx, GitHub Actions CI/CD |
| 봇 | Node.js 20, discord.js 14 |

## 저장소 구조

```
├── src/                  # Spring Boot 백엔드 (dev.algoj)
│   ├── domain/           #   user · problem · submission · run · image
│   └── global/           #   config · security · Judge0 클라이언트 · 모니터링
├── frontend/             # Next.js 프론트엔드
├── judge-runner/         # 채점기 (Judge0 대체) — 서비스 + 샌드박스 이미지
├── discord-bot/          # Discord 봇 (연동/비밀번호/서버상태/배포공지)
├── deploy/               # 배포 자료 + 운영 가이드
│   ├── rolling-deploy.sh #   OJ+EOJ 교차 롤링 배포 (OJ에서 지휘)
│   ├── deploy-api-single.sh #  박스 1개 무겹침 배포 (nginx는 안 건드림)
│   ├── nginx/            #   upstream 렌더링(드레인) · 내부 :8080 고정 진입점
│   ├── judge0/           #   Judge0 박스 세팅 (PyPy 추가, 메모리 제한)
│   └── docker-compose*.yml # 로컬 인프라 · JJ RabbitMQ · Discord 봇
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

로컬 전용 구성입니다. 운영에서는 DB가 RDS, RabbitMQ·Judge0가 JJ 박스에 있어
API 박스에는 봇 말고 compose 서비스가 없습니다.

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
2. OJ 박스에 SSH 접속해 **교차 롤링 배포**(`rolling-deploy.sh`) 실행 —
   EOJ 드레인(nginx upstream에서 `down`) → EOJ 교체 → 복귀 → OJ 드레인 → OJ 교체 → 복귀.
   드레인된 박스의 트래픽을 반대쪽이 100% 받으므로 사용자 체감 다운타임이 없습니다.
   새 컨테이너가 `/api/health`를 통과하지 못하면 그 박스는 이전 이미지로 롤백하고,
   어느 단계에서 실패하든 양쪽 upstream을 원상 복구합니다.
3. 배포 성공 시 머지된 PR 본문의 `## 공지` 섹션으로 **Discord 업데이트 공지**

Flyway 마이그레이션은 앱이 부팅하면서 자동 적용되므로 스키마 변경에 별도 서버 작업이 필요 없습니다.
서버 초기 세팅, nginx/TLS, 봇 운영 등 자세한 내용은 **[deploy/README.md](deploy/README.md)** 참고.

## Discord 봇 명령

| 명령 | 설명 |
|------|------|
| `/연동 <코드>` | OJ 계정 페이지에서 발급한 코드로 디스코드 계정 연동 |
| `/비밀번호분실` | 연동된 계정의 임시 비밀번호 발급 (본인에게만 표시) |
| `/서버상태` | DB · Judge0 · 채점 큐(대기/워커/DLQ) · 제출 현황 · JVM 상태 확인 |

배포 공지는 명령이 아니라 자동입니다 — PR 본문에 **`## 공지` 섹션**을 쓰면, 머지·배포 성공 시
그 섹션 내용만 `DISCORD_ANNOUNCE_CHANNEL_ID` 채널에 올라갑니다. 섹션이 없는 PR은 공지 없이 배포됩니다.
