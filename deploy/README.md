# Deployment — OJ + EOJ 이중화

API는 **두 박스(OJ·EOJ)에서 동시에** 돌고, DB·브로커·샌드박스는 밖으로 빼놨다.
설계 배경은 [`redundancy.md`](redundancy.md), 컴포넌트 분리 경위는
[`offload-components.md`](offload-components.md) 참고.

| 박스 | 역할 | 컨테이너 |
|---|---|---|
| **OJ** (Lightsail, Ubuntu 22.04) | nginx(TLS 종단 + LB) · API #1 · Discord 봇 · **롤링 배포 지휘자** | `algoj-api`(127.0.0.1:8081), `algoj-bot` |
| **EOJ** (EC2, 사설망) | API #2 | `algoj-api`(0.0.0.0:8080) |
| **JJ** (EC2, 사설망) | Judge0 :2358 · RabbitMQ :5672 | `algoj-rabbitmq` 외 Judge0 스택 |
| **RDS** | MySQL 8 (스키마는 Flyway가 관리) | — |
| **S3** | 지문 이미지 ([`aws-s3-images.md`](aws-s3-images.md)) | — |

프론트엔드는 Vercel에 따로 배포된다(이 문서 범위 밖).

> nginx는 OJ에만 있고 **배포 중에도 내려가지 않는다** — 교체되는 건 API 컨테이너뿐이다.
> API는 compose 서비스가 아니라 `deploy-api-single.sh`가 직접 관리한다. 박스에 남은 compose는
> OJ의 봇(`docker-compose.bot.yml`)과 JJ의 브로커(`docker-compose.jj.yml`)뿐이다.
> (`deploy/docker-compose.yml`은 **로컬 개발용 MySQL**이라 운영 박스와 무관하다.)

## 박스 레이아웃

```
/opt/algoj/                     # OJ · EOJ 공통
├── .env                        # 비밀 (chmod 600) — 두 박스가 같은 값, JWT_SECRET 공유 필수
├── deploy-api-single.sh        # 박스 1개 무겹침 배포 (CD가 매 배포마다 갱신)
├── nginx/render-upstream.sh    # OJ 전용 — upstream 드레인/복원
├── rolling-deploy.sh           # OJ 전용 — 두 박스 교차 롤링 지휘
├── eoj.pem                     # OJ 전용 — EOJ 사설 SSH 키 (chmod 600)
└── docker-compose.bot.yml      # OJ 전용 — Discord 봇
```

## 박스 1회 준비 (새 API 박스를 추가할 때)

```bash
sudo mkdir -p /opt/algoj && sudo chown ubuntu:ubuntu /opt/algoj
cd /opt/algoj

# docker 설치 후, .env 를 기존 박스에서 그대로 복사 (JWT_SECRET 이 같아야 토큰이 호환된다)
scp <기존박스>:/opt/algoj/.env .           # 또는 deploy/.env.prod.example 을 채워서 사용
chmod 600 .env

# 첫 기동 — 배포 스크립트는 CD가 복사해주지만, 최초 1회는 수동으로 올려서 검증한다
IMAGE=ghcr.io/sjh1108/oj-api:latest PORT=8080 PUBLISH_ADDR=0.0.0.0 bash deploy-api-single.sh
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/health   # 200
```

- 자바·jar를 박스에 깔 필요 없다 — API는 GHCR 이미지로만 돈다.
- 스키마는 **Flyway**가 부팅 시 적용한다. `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 넣던
  옛 절차는 더 쓰지 않는다 (아래 [DB 마이그레이션](#db-마이그레이션-flyway) 참고).
- 보안그룹: EOJ의 `:8080`은 **OJ 사설 IP만**, `:22`도 OJ 사설 IP만 열어둔다.

## 트러블슈팅

- **Spring 시작 실패 (env 누락)**: `docker logs algoj-api --tail 100`에서
  `Could not resolve placeholder 'DB_PASSWORD'` 같은 메시지 확인. `.env`의 변수 이름/값 점검.
- **DB 연결 실패**: RDS 보안그룹 inbound 3306에 해당 박스 사설 IP가 있는지 먼저 본다.
  `docker exec algoj-api env | grep DB_HOST`로 컨테이너가 실제로 받은 값도 확인.
- **채점이 PENDING에서 안 넘어감**: JJ 브로커 연결 문제일 가능성이 높다.
  JJ에서 `docker exec algoj-rabbitmq rabbitmqctl list_queues name messages consumers` —
  `judge.queue`의 consumers가 0이면 어느 API도 안 붙은 것이다.
- **OOM / slow (메모리 압박 → 스왑)**: `free -h`로 swap 사용량, `docker stats --no-stream`으로
  컨테이너별 RSS 확인. 두 박스 다 ≈2GB라 여유가 빠듯하고, 한 번 밀려난 콜드 페이지는 저절로
  안 빠져 `free` 수치가 계속 높게 보인다.
  - **자동(코드에 반영됨)**: `deploy-api-single.sh`가 JVM 힙을 `-Xms128m -Xmx300m`(여유 부족 시
    256m) + SerialGC로 캡한다 — `-Xmx` 미지정 시 JVM이 호스트의 25%(~500MB)를 잡는 걸 막는다.
    봇은 `docker-compose.bot.yml`에서 128m, JJ 브로커는 `docker-compose.jj.yml`에서 384m로 묶여 있다.
  - **잔류 스왑 청소**(필요할 때만): `free -h`에서 **available > swap used**를 확인한 뒤에만
    `sudo swapoff -a && sudo swapon -a` (아니면 OOM 위험).
  - `mem_limit` 변경은 컨테이너 **재생성** 시 반영된다: 봇은 OJ에서
    `docker compose -f docker-compose.bot.yml --env-file .env up -d`, RabbitMQ는 JJ에서
    `docker compose -f docker-compose.jj.yml --env-file .env up -d`.

## 운영 명령 cheat sheet

```bash
# 두 박스 공통
docker ps                                   # algoj-api 상태 (OJ는 algoj-bot 도)
docker logs algoj-api -f                    # 라이브 로그
docker restart algoj-api                    # 재시작 (배포 없이)
curl -s http://127.0.0.1:8081/api/health    # OJ (EOJ는 8080)

# OJ 전용 — 수동 롤링 배포 (CD가 하는 것과 동일)
cd /opt/algoj && IMAGE=ghcr.io/sjh1108/oj-api:latest bash rolling-deploy.sh

# OJ 전용 — 한 박스만 임시로 빼기/되돌리기
bash nginx/render-upstream.sh eoj-down      # EOJ 격리 (OJ가 100% 서빙)
bash nginx/render-upstream.sh none          # 둘 다 활성으로 복원
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
3. **deploy** (`DEPLOY_ENABLED=true`일 때만): `rolling-deploy.sh` · `deploy-api-single.sh` ·
   `nginx/render-upstream.sh`를 **OJ로 복사**한 뒤 SSH로 `rolling-deploy.sh`를 실행한다.
   OJ가 지휘자가 되어 두 박스를 **교차 롤링**으로 교체한다(아래 참고). EOJ로는 CD가 직접
   접속하지 않는다 — OJ가 사설망 SSH로 대신 배포하므로 EOJ의 `:22`는 OJ에만 열면 된다.
4. **공지**: 배포 성공 시 PR 본문의 `## 공지` 섹션만 디스코드 공지 채널에 게시한다.

### 필요한 GitHub Secrets / Variables

저장소 **Settings → Secrets and variables → Actions**에서 설정. **OJ 접속 정보만** 있으면 된다.

| 종류 | 이름 | 설명 |
|------|------|------|
| Variable | `DEPLOY_ENABLED` | `true`여야 deploy 잡이 동작. 미설정 시 build+push까지만. |
| Secret | `SSH_HOST` | **OJ** IP/호스트 (지휘자 겸 LB) |
| Secret | `SSH_USER` | SSH 사용자 (예: `ubuntu`) |
| Secret | `SSH_KEY` | **OJ** SSH 개인키 (PEM 전체) |
| Secret | `SSH_PORT` | (선택) 기본 22 |
| Secret | `GHCR_PAT` | (선택) 박스에서 GHCR pull용 read:packages 토큰. 패키지를 public으로 두면 불필요. |

> **EOJ 키는 Secret이 아니다.** OJ의 `/opt/algoj/eoj.pem`(chmod 600)에 두면
> `rolling-deploy.sh`가 그걸로 EOJ에 접속한다. EOJ IP가 바뀌면 스크립트의 `EOJ_HOST`
> 기본값(`172.31.32.237`)을 고치거나 env로 주입한다.
>
> GHCR 패키지는 기본 **private**이다. 박스가 이미지를 받으려면 `GHCR_PAT`로 로그인하거나,
> GHCR 패키지 페이지에서 visibility를 **public**으로 바꾼다.

`master`에 머지하면 자동 배포된다. 수동 트리거는 Actions 탭의 **CD → Run workflow**.

---

## 무중단 배포 (OJ + EOJ 교차 롤링)

**박스당 JVM 1개**가 원칙이다. 한 박스에서 블루-그린으로 JVM 2개를 겹치면 ≈2GB 박스가 스왑을
갈아 오히려 무중단이 깨졌기 때문에, 겹침 대신 **두 박스를 번갈아** 교체한다.

`rolling-deploy.sh`(OJ에서 실행)가 지휘하는 순서:

```
EOJ 드레인 → EOJ 교체·헬스체크 → EOJ 복귀 → OJ 드레인 → OJ 교체·헬스체크 → OJ 복귀
   (그동안 OJ가 100% 서빙)              (그동안 EOJ가 100% 서빙)
```

- **드레인**은 nginx 레이어에서 한다 — `nginx/render-upstream.sh`가 해당 박스를 upstream에서
  `down`으로 표시하고 reload. 드레인된 박스는 트래픽이 없으므로 **구 컨테이너를 먼저 내리고**
  새 것을 단독 부팅해도 사용자에게는 보이지 않는다.
- **박스 단위 롤백**: `deploy-api-single.sh`는 새 컨테이너가 `/api/health`(DB까지 확인)를 통과하지
  못하면 구 컨테이너(`algoj-api-prev`)를 되살린다.
- **전체 실패 시 안전망**: 어느 단계에서 죽어도 `rolling-deploy.sh`의 ERR 트랩이 upstream을
  `none`(둘 다 활성)으로 되돌리고 non-zero로 끝낸다 → 사이트는 계속 서빙되고 CD만 빨간불.
- 배포 중 다운타임 관찰(정상이라면 계속 200):
  ```bash
  while true; do curl -s -o /dev/null -w "%{http_code}\n" https://algoj.duckdns.org/api/health; sleep 0.2; done
  ```

> `deploy/deploy-api.sh`(단일 박스 블루-그린)는 이중화 전환 전에 쓰던 스크립트로, 지금은
> CD 경로에서 쓰이지 않는다. 이력 참고용으로만 남아 있다.

### OJ nginx 1회 설정

```bash
# 1) nginx 설정 두 개 설치 (repo의 deploy/nginx/)
sudo cp /opt/algoj/nginx/algoj-upstream.conf  /etc/nginx/conf.d/algoj-upstream.conf
sudo cp /opt/algoj/nginx/algoj-internal.conf  /etc/nginx/conf.d/algoj-internal.conf
#  - algoj-upstream.conf : 활성 API 포트(배포 스크립트가 자동으로 다시 씀)
#  - algoj-internal.conf : 127.0.0.1:8080 → algoj_api (봇 등 온박스 클라이언트용 고정 진입점)

# 2) 공개 사이트(TLS) server 블록의 proxy_pass 를 upstream 으로 변경
#    proxy_pass http://127.0.0.1:8080;   →   proxy_pass http://algoj_api;

# 3) 배포 유저(ubuntu)에 nginx reload + upstream 파일 쓰기용 무인증 sudo 부여
sudo tee /etc/sudoers.d/algoj-deploy >/dev/null <<'EOF'
ubuntu ALL=(root) NOPASSWD: /usr/sbin/nginx, /usr/bin/tee /etc/nginx/conf.d/algoj-upstream.conf
EOF
sudo chmod 440 /etc/sudoers.d/algoj-deploy

sudo nginx -t && sudo systemctl reload nginx
```

> `algoj-upstream.conf`는 설치 후 **손으로 고치지 않는다** — `render-upstream.sh`가 매 배포마다
> 두 박스(`least_conn`, `max_fails=2 fail_timeout=10s`)로 다시 쓴다. 한쪽만 `down`으로 표시할 수
> 있고 둘 다 내리는 건 막혀 있다(nginx가 전부 down인 upstream을 거부한다).
>
> 봇은 그대로 `OJ_API_BASE_URL=http://127.0.0.1:8080` 을 쓰면 된다 — `algoj-internal.conf`가
> 8080을 그때그때 살아있는 API로 연결해준다.

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
> `/usr/sbin/nginx` 경로에 무인증 sudo를 물고 있어 롤링 배포의 upstream 전환이 깨질 수 있다.
> apt 업그레이드는 reload만 하고 리스닝 소켓을 유지하므로 무중단 배포에 영향 없다.

### 대응 기록

| 일자 | 공지 | 판단 |
|---|---|---|
| 2026-07 | CVE-2026-42533 (`map` + 정규식 캡처 heap overflow), CVE-2026-60005 (`ngx_http_slice_module` 초기화되지 않은 메모리), CVE-2026-56434 (`ngx_http_ssi_module` UAF) | 패키지 `1.18.0-6ubuntu14.18`(jammy 최신) — 60005·56434는 USN-8563-1에서 패치됨. **42533은 ABI 문제로 USN-8563-2에서 롤백**되어 미패치 상태였으나, `nginx -T`에 `map`·`slice` 지시어가 **하나도 없어** 트리거 경로 없음 → 조치 불필요. 후속 USN 나오면 평소대로 `apt upgrade`. |

---

## RAM 증설 (Lightsail 2GB → 4GB) — 하지 않기로 결정

> **결론: 증설하지 않는다.** 검토 기록으로만 남긴다 — 다시 안건으로 올리지 말 것.

한 박스에서 JVM 2개를 겹치는 블루-그린이 ≈2GB에서 스왑을 갈아 무중단이 깨졌던 시절,
**RAM을 4GB로 올려 겹침을 되살리자**는 안이 있었다(2GB≈$12/월 → 4GB≈$24/월).

**대신 OJ+EOJ 이중화로 갔고, 그걸로 무중단 배포는 이미 확보됐다** — 박스당 JVM 1개씩
번갈아 교체하므로 겹칠 일이 없고, 증설의 목적이 사라졌다. 이중화는 무중단에 더해 장애
이중화(HA)까지 주므로 같은 돈이면 이쪽이 낫다. 비용·한계 비교는
[`redundancy.md`](redundancy.md)의 **6. 비용 / 한계** 참고.

메모리가 다시 빠듯해지면 증설보다 먼저 볼 것: 힙 캡(`-Xmx300m`)·봇 128m·브로커 384m가
그대로인지, 잔류 스왑이 남아 있지 않은지 (위 [트러블슈팅](#트러블슈팅)).

---

## RabbitMQ 채점 큐

제출 채점은 인프로세스 스레드풀(`@Async`)이 아니라 **RabbitMQ 큐**를 통해 처리된다.
제출 시 API가 `judge.queue`에 submission id를 durable 메시지로 넣고, 리스너 워커
(기본 동시성 2, `JUDGE_WORKER_CONCURRENCY`)가 꺼내서 Judge0로 채점한다.

- **재시작 내구성**: 큐/메시지가 durable이라 API·브로커가 재시작해도 대기 중인 채점이 유실되지 않는다.
  채점 도중 워커가 죽으면 unacked 메시지가 재전달되어 다시 채점된다(JUDGING 고아 상태 방지).
- **경쟁 소비**: 두 박스의 워커가 같은 큐를 나눠 받는다. 롤링 배포로 한쪽이 잠깐 빠져도 남은
  박스가 계속 소비하므로 채점이 멈추지 않는다.
- **스위퍼**: 브로커에 메시지가 유실돼 PENDING으로 남은 제출은 `PendingSubmissionSweeper`가
  1분 주기로 재적재한다. 중복 실행이 낭비라 **한 박스에서만** 켠다 — OJ는 미설정(=활성),
  EOJ는 `.env`에 `SWEEPER_ENABLED=false`. 확인:
  `docker exec algoj-api env | grep SWEEPER_ENABLED` (자세한 건 `redundancy.md` §4).
- **DLQ**: 역직렬화 실패 등으로 reject된 메시지는 `judge.queue.dlq`로 빠진다. 쌓이면 조사할 것.

> **브로커 위치**: RabbitMQ는 OJ가 아니라 **JJ(EC2) 박스**에서 돈다(Judge0와 함께 채점 인프라 통합).
> API는 `.env`의 `RABBITMQ_HOST`로 접근한다. 브로커 기동·보안그룹·이전 절차는
> `deploy/offload-components.md`의 **C. RabbitMQ → JJ**를, 이중화 설계는 `deploy/redundancy.md`를 참고.

### JJ 박스 브로커 기동 (1회)

```bash
# JJ 박스에서 — docker-compose.jj.yml 을 복사해두고
cd /opt/algoj                              # 또는 judge0 디렉터리

# 1) .env에 브로커 계정 (OJ .env의 값과 동일하게)
openssl rand -base64 24   # → RABBITMQ_PASSWORD
#   RABBITMQ_USER=algoj
#   RABBITMQ_PASSWORD=<위 값>

# 2) 브로커 기동
docker compose -f docker-compose.jj.yml --env-file .env up -d
docker logs algoj-rabbitmq --tail 20

# 3) JJ 보안그룹 inbound 5672 를 OJ·EOJ 사설 IP로만 허용 (0.0.0.0/0 금지)

# 4) OJ·EOJ 양쪽 .env 에 RABBITMQ_HOST=<JJ 사설IP> 설정 후 재배포
#    (배포 스크립트는 RABBITMQ_HOST 를 주입하지 않는다 → .env 값이 그대로 쓰인다)
cd /opt/algoj && IMAGE=ghcr.io/sjh1108/oj-api:latest bash rolling-deploy.sh   # OJ에서
```

> 큐 상태 확인(JJ에서): `docker exec algoj-rabbitmq rabbitmqctl list_queues name messages consumers`

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
  롤링 배포에서는 첫 박스가 헬스체크를 통과 못 하고 롤백되므로 **구버전이 계속 서빙된다**
  (두 번째 박스는 건드리기 전에 중단된다).

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

CD가 롤링 배포를 **성공**하고, 머지된 PR 본문에 **`## 공지` 섹션이 있을 때만**
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
> 진입점**(`algoj-internal.conf`)이다 — 그때그때 살아있는 API로 넘겨주므로 롤링 배포 중에도
> 주소가 안 바뀐다. 봇은 `docker-compose.bot.yml`의 `network_mode: host` +
> `OJ_API_BASE_URL=http://127.0.0.1:8080`으로 호스트 네트워크를 공유해 접근한다.
> (브릿지 `host.docker.internal`로는 `ECONNREFUSED`가 난다.) `/opt/algoj`에
> `docker-compose.bot.yml`이 없으면 repo의 `deploy/docker-compose.bot.yml`을 그대로 올려두면
> 된다 — CD는 배포 스크립트만 복사하고 봇 compose는 건드리지 않는다.

### 사용 흐름 (회원)

1. OJ 로그인 → 우상단 본인 이름(`/account`) → **디스코드 연동 → 연동 코드 발급**
2. 디스코드에서 `/연동 <코드>` 입력 → "○○ 계정과 연동되었습니다"
3. 비밀번호를 잊으면 `/비밀번호분실` → **본인만 보이는** 임시 비밀번호 수신
4. 그 비밀번호로 로그인 → `/account`에서 새 비밀번호로 변경

> 연동은 "비번을 잊기 전"에 해둬야 한다(잊은 뒤 미연동자는 로그인 불가 → 연동도 불가).
> 그런 경우엔 관리자가 **회원 관리** 페이지에서 직접 재설정하면 된다.
