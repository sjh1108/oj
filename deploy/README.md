# Deployment — Oracle Cloud 단일 박스

모든 것이 **Oracle Cloud Ampere 한 박스(aarch64, 2 OCPU / 12GB)** 에서 돈다.
AWS 무료 크레딧이 끊기면서 네 조각(OJ·EOJ·JJ·RDS)으로 흩어져 있던 구성을 여기로 합쳤다 —
경위는 [`oracle-cloud-migration.md`](oracle-cloud-migration.md), AWS 시절의 설계·이전 기록은
[`archive/`](archive/README.md).

| 컴포넌트 | 어디에 | 비고 |
|---|---|---|
| nginx | 호스트 | TLS 종단 + blue-green 업스트림. 배포 중에도 안 내려간다 |
| API | `algoj-api-blue` / `-green` | 127.0.0.1:8081·8082, `deploy-api.sh`가 직접 관리 |
| MySQL 8 · RabbitMQ 4 | `docker-compose.oci.yml` | `algoj-net` 안에서만 — **호스트 포트를 열지 않는다** |
| 채점기 | `docker-compose.judge.yml` | Judge0 대체 ([`judge-runner/`](../judge-runner/README.md)) |
| Discord 봇 | `docker-compose.bot.yml` | host network로 nginx 내부 진입점(:8080) 사용 |

프론트엔드는 Vercel에 따로 배포된다(이 문서 범위 밖).

> **docker의 포트 publish는 iptables INPUT 체인을 우회한다.** DB·브로커에 `-p`를 붙이는 순간
> 방화벽 설정과 무관하게 인터넷에 열린다. 그래서 두 서비스는 도커 네트워크 안에서만 통신하고,
> API도 루프백에만 바인딩한다.

## 박스 레이아웃

박스에 있어야 할 것들이다. **compose 파일은 저장소 클론에서 복사해 와야 한다** —
`docker compose -f <파일>`은 현재 디렉터리에서 찾으므로, 없으면
`no such file or directory`로 막힌다.

```
/opt/algoj/
├── .env                        # 비밀 (chmod 600)
├── backup.env                  # DB 백업 업로드 설정 (chmod 600, 선택) — backup.md
├── rclone/rclone.conf          # Google Drive 로그인 토큰 (chmod 700 디렉터리) — backup.md
├── repo/                       # 이 저장소 클론 — compose 파일과 스크립트의 출처
├── deploy-api.sh               # 한 박스 blue-green 배포 (CD가 매 배포마다 갱신)
├── docker-compose.oci.yml      # MySQL + RabbitMQ
├── docker-compose.judge.yml    # 채점기
├── docker-compose.bot.yml      # Discord 봇
├── mysql-data/                 # MySQL 데이터
├── rabbitmq-data/              # 브로커 데이터 (durable 큐)
├── judge-work/                 # 채점 작업 디렉터리 (실행 후 비워진다)
├── backups/                    # DB 백업 최근 7개 (cron, backup-db.sh)
└── backup.log                  # 백업 실행 기록
```

```bash
# 새로 세울 때 compose 파일 세 개를 한 번에
cp /opt/algoj/repo/deploy/docker-compose.{oci,judge,bot}.yml /opt/algoj/
```

> ⚠️ **`--remove-orphans`를 붙이지 말 것.** compose 파일 세 개가 같은 디렉터리에 있어
> 프로젝트 이름을 공유하므로, 하나를 올릴 때마다 나머지 컨테이너를 "orphan"이라고 경고한다.
> 경고 자체는 무해하지만, 안내대로 `--remove-orphans`를 붙이면 **MySQL·브로커·채점기가 함께
> 삭제된다.** 데이터는 bind mount라 남지만 서비스는 내려간다. 경고는 그냥 무시한다.

## 박스를 새로 세울 때

전체 순서(인스턴스 준비 → compose 기동 → 덤프 적재 → 채점기 → nginx·TLS → CD 연결)는
[`oracle-cloud-migration.md`](oracle-cloud-migration.md)를 그대로 따른다.

- 자바·jar를 박스에 깔 필요 없다 — API는 GHCR 이미지로만 돈다.
- 스키마는 **Flyway**가 부팅 시 적용한다. `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 넣던
  옛 절차는 더 쓰지 않는다 (아래 [DB 마이그레이션](#db-마이그레이션-flyway) 참고).
- `.env`의 `JWT_SECRET`을 바꾸면 기존 로그인 세션이 전부 끊긴다. 옮길 때는 값을 그대로 가져온다.

## 트러블슈팅

- **Spring 시작 실패 (env 누락)**: `docker logs algoj-api-blue --tail 100`(또는 `-green`)에서
  `Could not resolve placeholder 'DB_PASSWORD'` 같은 메시지 확인. `.env`의 변수 이름/값 점검.
- **DB 연결 실패**: `docker compose -f docker-compose.oci.yml ps`로 `algoj-mysql`이 healthy인지,
  API 컨테이너가 `algoj-net`에 붙어 있는지(`docker inspect -f '{{json .NetworkSettings.Networks}}' algoj-api-blue`)
  본다. `.env`는 `DB_HOST=mysql`이어야 한다.
- **채점이 PENDING에서 안 넘어감**: 브로커나 채점기 문제다.
  `docker exec algoj-rabbitmq rabbitmqctl list_queues name messages consumers` —
  `judge.queue`의 consumers가 0이면 API 워커가 안 붙은 것이다. 채점기는
  [`judge-runner/README.md`](../judge-runner/README.md#문제가-생기면)의 **문제가 생기면** 절을 본다.
- **메모리**: 12GB라 평소에는 여유가 크다. `free -h`, `docker stats --no-stream`으로 컨테이너별
  RSS를 본다. `deploy-api.sh`는 박스 전체 RAM이 4GB 이상이면 `-Xms512m -Xmx1500m`, 그보다 작으면
  예전 소형 박스용 캡(`-Xmx300m` + SerialGC)을 자동으로 고른다. 봇은 128m, 브로커는 512m로 묶여 있다.

## DB 백업

MySQL이 박스 안에 있으므로 백업도 직접 한다. `deploy/backup-db.sh`를 cron으로 매일 돌려
박스에 7개를 보관하고, 설정하면 Google Drive(rclone)로도 올린다. 설치·복구 절차는
[`backup.md`](backup.md).

## 운영 명령 cheat sheet

```bash
docker ps                                   # algoj-api-blue|green, mysql, rabbitmq, judge-runner, bot
docker logs algoj-api-blue -f               # 라이브 로그 (활성 색은 upstream 포트로 확인)
cat /etc/nginx/conf.d/algoj-upstream.conf   # 8081=blue, 8082=green
curl -s http://127.0.0.1:8080/api/health    # nginx 내부 진입점 → 활성 API

# 수동 배포 (CD가 하는 것과 동일)
cd /opt/algoj && IMAGE=ghcr.io/sjh1108/oj-api:latest bash deploy-api.sh
```

---

## CI/CD 파이프라인 (GitHub Actions)

워크플로우는 `.github/workflows/`에 있다.

### `ci.yml` — PR / 브랜치 push 시 검증
- **backend**: MySQL 서비스 컨테이너를 띄우고 `./gradlew build` (테스트 포함) 실행.
- **frontend**: `npm ci` → `npm run lint` → `npm run build`.
- **docker-build**: 백엔드 Docker 이미지가 빌드되는지만 확인 (push 안 함).

### `cd.yml` — `master` push 시 배포
1. **test**: 백엔드 테스트 재실행.
2. **build-and-push**: 이미지 빌드 후 `ghcr.io/<owner>/oj-api:latest` + `:sha-<커밋>`로 push.
   GHCR 인증은 Actions 기본 `GITHUB_TOKEN`을 사용.
3. **deploy** (`DEPLOY_ENABLED=true`일 때만): `deploy-api.sh`를 박스로 복사한 뒤 SSH로 실행한다.
   **blue-green**으로 교체한다(아래 [무중단 배포](#무중단-배포-blue-green) 참고).
4. **공지**: 배포 성공 시 PR 본문의 `## 공지` 섹션만 디스코드 공지 채널에 게시한다.

### 필요한 GitHub Secrets / Variables

저장소 **Settings → Secrets and variables → Actions**에서 설정.

| 종류 | 이름 | 설명 |
|------|------|------|
| Variable | `DEPLOY_ENABLED` | `true`여야 deploy 잡이 동작. 미설정 시 build+push까지만. |
| Secret | `SSH_HOST` | 박스 공인 IP/호스트 |
| Secret | `SSH_USER` | SSH 사용자 (예: `ubuntu`) |
| Secret | `SSH_KEY` | 박스 SSH 개인키 (PEM 전체) |
| Secret | `SSH_PORT` | (선택) 기본 22 |
| Secret | `GHCR_PAT` | (선택) 박스에서 GHCR pull용 read:packages 토큰. 패키지를 public으로 두면 불필요. |

> GHCR 패키지는 기본 **private**이다. 박스가 이미지를 받으려면 `GHCR_PAT`로 로그인하거나,
> GHCR 패키지 페이지에서 visibility를 **public**으로 바꾼다.

`master`에 머지하면 자동 배포된다. 수동 트리거는 Actions 탭의 **CD → Run workflow**.

---

## 무중단 배포 (blue-green)

`deploy-api.sh`가 한 박스 안에서 두 색을 번갈아 쓴다:

```
비활성 포트(8081|8082)에 새 컨테이너 기동 → /api/health 통과 → nginx upstream 전환·reload → 구 컨테이너 드레인
```

- 12GB라 두 JVM이 잠깐 겹쳐도 된다. nginx는 reload만 하므로 연결이 끊기지 않는다.
- **롤백**: 새 컨테이너가 `/api/health`(DB까지 확인)를 통과하지 못하면 nginx를 건드리지 않고
  새 컨테이너만 지운 채 non-zero로 끝난다 → 사이트는 구버전으로 계속 서빙되고 CD만 빨간불.
- 배포 중 다운타임 관찰(정상이라면 계속 200):
  ```bash
  while true; do curl -s -o /dev/null -w "%{http_code}\n" https://algoj.duckdns.org/api/health; sleep 0.2; done
  ```

### nginx 1회 설정

설치 명령은 [`oracle-cloud-migration.md`](oracle-cloud-migration.md)의 nginx 단계에 있다.
`deploy/nginx/`의 세 파일이 하는 일:

| 파일 | 설치 위치 | 역할 |
|---|---|---|
| `algoj-site.conf` | `/etc/nginx/sites-available/algoj` | 공개 사이트. certbot이 443 블록을 붙인다 |
| `algoj-upstream.conf` | `/etc/nginx/conf.d/` | 활성 API 포트. **손으로 고치지 않는다** — `deploy-api.sh`가 매 배포마다 다시 쓴다 |
| `algoj-internal.conf` | `/etc/nginx/conf.d/` | `127.0.0.1:8080` → 활성 API. 봇 등 박스 안 클라이언트용 고정 진입점 |

배포 유저(ubuntu)에게 `nginx`와 upstream 파일 쓰기용 무인증 sudo가 필요하다
(`/etc/sudoers.d/algoj-deploy`).

---

## nginx 보안 공지 대응 절차

클라우드 사업자/보안 공지로 nginx CVE 권고가 오면 **버전 숫자만 보고 판단하지 않는다.**
이 박스의 nginx는 우분투 배포판 패키지(`/usr/sbin/nginx`, apt)라 업스트림 버전(1.18.0)이
그대로 남고 **패치만 백포트**된다 — 공지의 "1.30.4 미만 취약" 표에는 항상 걸리는 것처럼 보인다.

판단은 이 두 가지로 한다.

```bash
# 1) 우분투 패키지 버전 — 여기가 최신이면 apt로 할 수 있는 건 끝
dpkg -l | grep nginx
sudo apt update && sudo apt install --only-upgrade nginx nginx-core nginx-common

# 2) 취약 코드 경로를 실제로 쓰는지 — include까지 펼친 실효 설정에서 확인
sudo nginx -T 2>/dev/null | grep -nE '^\s*(map|slice)\b'
```

2번이 핵심이다. nginx CVE는 특정 지시어(`map` + 정규식 캡처, `slice` 등)를 쓸 때만 트리거되는
경우가 많아서, **해당 지시어가 설정에 없으면 패키지가 미패치여도 실질 노출이 없다.**
`/etc/nginx/`를 grep하면 기본 문자셋 파일(`koi-utf`, `koi-win`, `win-utf`)의 `charset_map`과
주석이 잡히는데 이건 `map` 지시어가 아니다 — `nginx -T` 쪽이 정확하다.

우분투 보안 상태는 `https://ubuntu.com/security/CVE-XXXX-XXXXX`에서 릴리스별로 확인한다
(`Vulnerable` / `Fixed`). 패키지가 최신인데도 `Vulnerable`이면 **아직 패치가 안 나온 것**이므로
기다린다. 후속 패치를 놓치지 않으려면 `unattended-upgrades`를 켜둔다.

> nginx.org 공식 저장소로 갈아타 최신 업스트림을 직접 올리는 건 권장하지 않는다 —
> certbot 연동·설정 경로가 바뀌고, 배포 파이프라인이 `/etc/sudoers.d/algoj-deploy`의
> `/usr/sbin/nginx` 경로에 무인증 sudo를 물고 있어 blue-green의 upstream 전환이 깨질 수 있다.
> apt 업그레이드는 reload만 하고 리스닝 소켓을 유지하므로 무중단 배포에 영향 없다.

### 대응 기록

| 일자 | 공지 | 판단 |
|---|---|---|
| 2026-07 | CVE-2026-42533 (`map` + 정규식 캡처 heap overflow), CVE-2026-60005 (`ngx_http_slice_module` 초기화되지 않은 메모리), CVE-2026-56434 (`ngx_http_ssi_module` UAF) | 패키지 `1.18.0-6ubuntu14.18`(jammy 최신) — 60005·56434는 USN-8563-1에서 패치됨. **42533은 ABI 문제로 USN-8563-2에서 롤백**되어 미패치 상태였으나, `nginx -T`에 `map`·`slice` 지시어가 **하나도 없어** 트리거 경로 없음 → 조치 불필요. 후속 USN 나오면 평소대로 `apt upgrade`. |

---

## RabbitMQ 채점 큐

제출 채점은 인프로세스 스레드풀(`@Async`)이 아니라 **RabbitMQ 큐**를 통해 처리된다.
제출 시 API가 `judge.queue`에 submission id를 durable 메시지로 넣고, 리스너 워커
(기본 동시성 2, `JUDGE_WORKER_CONCURRENCY`)가 꺼내서 Judge0로 채점한다.

- **재시작 내구성**: 큐/메시지가 durable이라 API·브로커가 재시작해도 대기 중인 채점이 유실되지 않는다.
  채점 도중 워커가 죽으면 unacked 메시지가 재전달되어 다시 채점된다(JUDGING 고아 상태 방지).
- **스위퍼**: 브로커에 메시지가 유실돼 PENDING으로 남은 제출은 `PendingSubmissionSweeper`가
  1분 주기로 재적재한다. 기본 활성이다(`SWEEPER_ENABLED`).
- **DLQ**: 역직렬화 실패 등으로 reject된 메시지는 `judge.queue.dlq`로 빠진다. 쌓이면 조사할 것.

> **브로커 위치**: RabbitMQ는 `docker-compose.oci.yml`로 같은 박스에서 돈다. 포트를 열지 않고
> `algoj-net` 안에서만 통신하므로 API `.env`는 `RABBITMQ_HOST=rabbitmq`다.
> 큐 상태 확인: `docker exec algoj-rabbitmq rabbitmqctl list_queues name messages consumers`

---

## DB 마이그레이션 (Flyway)

스키마는 **Flyway**가 관리한다. `.env`에 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 임시로
넣거나 서버에서 SQL을 직접 치는 방식은 **더 이상 쓰지 않는다**.

- 마이그레이션 파일: `src/main/resources/db/migration/V<N>__<설명>.sql`
- 앱이 **부팅할 때** `flyway_schema_history` 테이블과 대조해 빠진 버전만 순서대로 적용한다.
  배포 = 머지, 별도 서버 작업 없음.
- 한 번 적용된 파일은 체크섬으로 잠긴다 — 수정하지 말고 항상 **새 버전 파일을 추가**할 것.
- `V1__baseline.sql`은 Flyway 도입 시점의 스키마다. **기존 DB(프로드/로컬)는 첫 부팅 때
  baseline-on-migrate로 "이미 V1" 도장만 찍히고 V1은 실행되지 않는다** — 그 뒤 V2부터
  순서대로 적용된다. 빈 DB(새 로컬, CI)에서만 V1부터 전부 실행된다.
- Hibernate는 모든 프로필에서 `ddl-auto=validate`: 엔티티와 DB가 어긋나면 부팅이 실패한다.
  blue-green 배포에서는 새 컨테이너가 헬스체크를 통과 못 하고 롤백되므로 **구버전이 계속 서빙된다**.

### 새 스키마 변경을 만들 때

1. 엔티티 수정
2. `SchemaDdlGenerator` 테스트를 돌려 `build/baseline-ddl.sql`(전체 DDL)을 뽑고,
   바뀐 부분만 `V2__...sql` 같은 새 파일로 작성
3. 커밋 → 머지하면 배포 시 자동 적용

---

## Discord 봇 (선택) — 비밀번호 분실 / 계정 연동

회원이 디스코드에서 `/비밀번호분실` 로 임시 비밀번호를 받고(`/연동` 으로 미리 계정 연결),
받은 비밀번호로 로그인 후 `/account` 에서 바꾸는 흐름이다. 봇 소스는 repo의 `discord-bot/`.

> **보안 모델**: 디스코드 사용자 ↔ OJ 계정을 **미리 연동**해두고, `/비밀번호분실`은 명령을 친
> 본인의 연동 계정만 리셋한다(아이디 입력 방식 아님 → 계정 탈취 불가). 봇은 `/api/internal/**`를
> `BOT_API_KEY` 헤더로 호출하며, 이 경로는 JWT 대신 그 키로만 보호된다(키 없으면 fail-closed).

### 1단계 — Discord 앱/봇 만들기

1. https://discord.com/developers/applications → **New Application**
2. **General Information** → `Application ID` 복사 → `DISCORD_CLIENT_ID`
3. 좌측 **Bot** → `Reset Token` → 토큰 복사 → `DISCORD_TOKEN` (한 번만 보임)
4. 봇을 서버에 초대: **OAuth2 → URL Generator** → scopes에 `bot` + `applications.commands`
   체크 → 생성된 URL로 본인 서버에 초대
5. 서버(길드) ID: 디스코드 **설정 → 고급 → 개발자 모드** 켜고, 서버 아이콘 우클릭 →
   **서버 ID 복사** → `DISCORD_GUILD_ID`

### 2단계 — 박스 `.env`에 값 추가

`/opt/algoj/.env` (백엔드와 봇이 공유):

```bash
# 백엔드 ↔ 봇 공유 시크릿 (양쪽 동일해야 함)
openssl rand -base64 32      # → BOT_API_KEY 에 붙임
# .env 에 추가:
#   BOT_API_KEY=<위 값>
#   DISCORD_TOKEN=<봇 토큰>
#   DISCORD_CLIENT_ID=<application id>
#   DISCORD_GUILD_ID=<서버 id>
#   OJ_WEB_BASE_URL=https://<프론트 Vercel 도메인>   # 예: https://algoj.vercel.app
```

> `BOT_API_KEY`는 백엔드(`/api/internal/**` 검증)와 봇(요청 헤더)이 **같은 값**을 써야 한다.
> 백엔드는 `.env`의 `BOT_API_KEY`를 자동으로 읽는다.
>
> ⚠️ `OJ_WEB_BASE_URL`은 **프론트엔드(Vercel) 웹 도메인**이다. API 도메인
> (`algoj.duckdns.org`)을 넣으면 `/비밀번호분실` 링크의 `/account`가 백엔드(Spring)로 가서
> **JSON 401 인증 에러 페이지**만 뜬다. (봇이 API를 호출하는 주소 `OJ_API_BASE_URL`과 혼동 주의 —
> 그건 백엔드, `OJ_WEB_BASE_URL`은 프론트.)

### 3단계 — 봇 실행

봇 이미지는 CD가 `ghcr.io/<owner>/oj-bot:latest`로 빌드/푸시한다(패키지 public 또는 GHCR 로그인 필요).

```bash
cd /opt/algoj
export BOT_IMAGE=ghcr.io/sjh1108/oj-bot
docker compose -f docker-compose.bot.yml --env-file .env pull
docker compose -f docker-compose.bot.yml --env-file .env up -d
docker logs algoj-bot --tail 30      # "Logged in as ..." + 슬래시 명령 등록 로그
```

컨테이너 시작 시 슬래시 명령(`/연동`, `/비밀번호분실`, `/서버상태`)을 길드에 자동 등록한다.

### 모니터링 — `/서버상태`

디스코드에서 `/서버상태`를 치면 봇이 백엔드의 `/api/internal/monitor`(봇 키 보호)를 호출해
**DB · Judge0 · 채점 큐(대기/워커/DLQ) · 제출 현황(대기/채점중/오늘) · JVM 메모리/업타임**을
임베드로 보여준다. 별도 설정 불필요 — 봇만 떠 있으면 된다.

### 배포 공지 — master 머지 시 자동 (opt-in)

CD가 배포를 **성공**하고, 머지된 PR 본문에 **`## 공지` 섹션이 있을 때만**
그 섹션의 내용을 봇의 로컬 공지 리스너(`127.0.0.1:3910`, `BOT_API_KEY`로 보호,
외부 노출 없음)로 전달하고 봇이 지정 채널에 업데이트 임베드를 올린다.

```markdown
## 요약
개발자용 상세 설명...           ← 공지에 안 나감

## 공지
🔍 문제 검색/필터가 생겼어요!    ← 이 부분만 채널에 게시됨
- 제목 검색, 난이도/태그 필터

## 테스트
...                             ← 다음 H2부터는 제외
```

`## 공지` 섹션이 없는 PR(문서, 내부 리팩토링, 관리자 도구 등)은 공지 없이 조용히
배포된다 — 유저에게 영향 있는 변경만 골라서 알리는 구조.

설정 (1회): `/opt/algoj/.env`에 공지 채널 ID 추가 후 봇 재시작.

```bash
# 디스코드: 설정 → 고급 → 개발자 모드 ON → 공지 채널 우클릭 → "채널 ID 복사"
echo "DISCORD_ANNOUNCE_CHANNEL_ID=<채널ID>" >> /opt/algoj/.env
docker compose -f docker-compose.bot.yml --env-file .env up -d --force-recreate bot
```

- 채널 ID를 안 넣으면 공지 기능만 조용히 꺼진다(배포는 정상 진행).
- 봇이 죽어 있어도 배포는 실패하지 않는다 — 공지만 건너뛴다.

> **봇 → 백엔드 연결**: 봇이 부르는 `127.0.0.1:8080`은 API가 아니라 **nginx의 내부 고정
> 진입점**(`algoj-internal.conf`)이다 — 그때그때 활성인 API로 넘겨주므로 blue-green 전환 중에도
> 주소가 안 바뀐다. 봇은 `docker-compose.bot.yml`의 `network_mode: host` +
> `OJ_API_BASE_URL=http://127.0.0.1:8080`으로 호스트 네트워크를 공유해 접근한다.
> (브릿지 `host.docker.internal`로는 `ECONNREFUSED`가 난다.) `/opt/algoj`에
> `docker-compose.bot.yml`이 없으면 repo의 `deploy/docker-compose.bot.yml`을 그대로 올려두면
> 된다 — CD는 `deploy-api.sh`만 복사하고 봇 compose는 건드리지 않는다.

### 선정 문제 알림 — 일괄 업로드 화면의 버튼

관리자 **문제 일괄 업로드** 화면에서 등록이 끝나면 "선정 문제 알림" 카드가 나온다.
**알림 작성**을 누르면 등록한 문제가 목록 순서대로 3개씩 Set으로 묶이고 Easy · Medium ·
Hard가 붙은 초안이 채워지고, 고친 뒤 **디스코드로 보내기**로 올린다.

봇이 아니라 **채널 웹훅**으로 보낸다. 봇의 공지 리스너는 박스의 루프백에만 열려 있어
`algoj-net` 안의 API 컨테이너에서는 닿지 않기 때문이다.

설정 (1회): `/opt/algoj/.env`에 두 값을 넣고 API를 재배포(`bash deploy-api.sh`)한다.

```bash
# 채널 설정 → 연동 → 웹후크 → 새 웹후크 → (이름·프로필 지정) → 웹후크 URL 복사
DISCORD_PROBLEM_WEBHOOK_URL=https://discord.com/api/webhooks/...
# 개발자 모드 켠 뒤 서버 설정 → 역할 → 멘션할 역할 우클릭 → ID 복사
DISCORD_PROBLEM_ROLE_ID=1528378236780806306
```

- 웹훅 URL은 비밀이다 — 아는 사람은 누구나 그 채널에 글을 쓸 수 있다. `.env`에만 둔다.
- 멘션은 `DISCORD_PROBLEM_ROLE_ID` 역할만 울린다. 초안을 고치다 `@everyone`을 넣어도 울리지 않는다.
- URL이 없으면 카드에 "웹훅이 설정되지 않음"이 표시되고 버튼이 꺼진다.

### 사용 흐름 (회원)

1. OJ 로그인 → 우상단 본인 이름(`/account`) → **디스코드 연동 → 연동 코드 발급**
2. 디스코드에서 `/연동 <코드>` 입력 → "○○ 계정과 연동되었습니다"
3. 비밀번호를 잊으면 `/비밀번호분실` → **본인만 보이는** 임시 비밀번호 수신
4. 그 비밀번호로 로그인 → `/account`에서 새 비밀번호로 변경

> 연동은 "비번을 잊기 전"에 해둬야 한다(잊은 뒤 미연동자는 로그인 불가 → 연동도 불가).
> 그런 경우엔 관리자가 **회원 관리** 페이지에서 직접 재설정하면 된다.
