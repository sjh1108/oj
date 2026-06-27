# Deployment — Lightsail (Ubuntu 22.04)

같은 박스에 이미 Judge0가 떠 있다는 전제. 이 가이드는 백엔드(Spring Boot) + MySQL을 추가로 올린다.

> **두 가지 배포 방식이 있다.**
> - **(A) 자동 — GitHub Actions CI/CD** (권장): `master`에 머지되면 이미지를 GHCR에 push 하고
>   서버가 pull 해서 컨테이너로 재기동한다. → 아래 [CI/CD 파이프라인](#cicd-파이프라인-github-actions) 참고.
> - **(B) 수동 — systemd + jar**: 로컬에서 `bootJar` 빌드 후 `scp`로 jar를 올리고 systemd로 실행.
>   → 이 문서의 1~7단계.
>
> A 방식은 백엔드를 **컨테이너**로 돌리므로 `algoj-api.service`(systemd) 대신
> `docker-compose.prod.yml`의 `api` 서비스를 쓴다. 같은 박스에서 둘을 **동시에 켜지 말 것**
> (8080 포트 충돌).

## 박스 레이아웃 (목표)

```
/opt/algoj/
├── .env                 # 비밀 (chmod 600)
├── algoj.jar            # bootJar 산출물
├── docker-compose.yml   # MySQL 컨테이너
└── mysql-data/          # MySQL 영속 볼륨
```

systemd unit은 `/etc/systemd/system/algoj-api.service`에 배치.

## 1단계 — 박스 준비

SSH로 박스 접속 후:

```bash
# 디렉토리 + 권한
sudo mkdir -p /opt/algoj
sudo chown ubuntu:ubuntu /opt/algoj
cd /opt/algoj

# Java 21 (JRE만)
sudo apt update
sudo apt install -y openjdk-21-jre-headless
java -version
```

## 2단계 — 파일 업로드 (로컬에서 실행)

```bash
# 프로젝트 루트에서
./gradlew clean bootJar
# → build/libs/algoj.jar 생성됨

# Lightsail로 배포 자료 + jar 전송
scp build/libs/algoj.jar ubuntu@<박스IP>:/opt/algoj/algoj.jar
scp deploy/docker-compose.yml ubuntu@<박스IP>:/opt/algoj/docker-compose.yml
scp deploy/.env.prod.example ubuntu@<박스IP>:/opt/algoj/.env
scp deploy/algoj-api.service ubuntu@<박스IP>:/tmp/algoj-api.service
```

## 3단계 — 박스에서 .env 작성

```bash
cd /opt/algoj

# 강력한 secret 생성 후 .env 편집
openssl rand -base64 24    # → DB_PASSWORD에 붙임
openssl rand -base64 24    # → MYSQL_ROOT_PASSWORD에 붙임
openssl rand -base64 48    # → JWT_SECRET에 붙임

vim .env    # __GENERATE__ 자리에 위 값들 + Vercel 도메인 채우기
chmod 600 .env
```

## 4단계 — MySQL 컨테이너 기동

```bash
cd /opt/algoj
docker compose --env-file .env up -d
docker logs algoj-mysql --tail 50

# 헬스체크 통과까지 30초 정도 대기 후
docker exec algoj-mysql mysqladmin ping -uroot -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2)"
# → mysqld is alive
```

## 5단계 — Spring Boot systemd 등록

```bash
sudo mv /tmp/algoj-api.service /etc/systemd/system/algoj-api.service
sudo systemctl daemon-reload
sudo systemctl enable algoj-api
sudo systemctl start algoj-api

# 로그 보기
journalctl -u algoj-api -f
```

스타트 시 첫 부팅에선 Hibernate `ddl-auto=validate`라 테이블이 없으면 실패한다 → 첫 1회만 `.env`에서 `JPA_DDL_AUTO=update`로 띄워 테이블 생성 후, 이후 `validate`로 복구. 또는 prod profile yml의 ddl-auto를 `update`로 잠시 바꾼다.

대안: 첫 deploy 직전에 `.env`에 다음 한 줄 추가:

```
JPA_DDL_AUTO=update
```

이 값은 application.yml의 `${JPA_DDL_AUTO:...}`를 override한다. (현재 yml에 그 변수 없으면 prod profile의 `validate`가 우선이라 추가 작업 필요 — 아래 6단계 참고.)

### 6단계 (선택) — 첫 deploy 시 스키마 자동 생성

prod profile에서 `ddl-auto: validate`로 묶여있어서 빈 DB에선 startup 실패한다. 첫 deploy에는:

```bash
# 옵션 A: 임시로 prod yml override
sudo systemctl stop algoj-api
# .env에 추가
echo "SPRING_JPA_HIBERNATE_DDL_AUTO=update" | sudo tee -a /opt/algoj/.env
sudo systemctl start algoj-api
journalctl -u algoj-api -f
# 테이블 생성된 거 확인 후
docker exec -it algoj-mysql mysql -ualgoj -p"$(grep '^DB_PASSWORD=' /opt/algoj/.env | cut -d= -f2)" algoj -e "SHOW TABLES;"
# 다시 .env에서 SPRING_JPA_HIBERNATE_DDL_AUTO 줄 제거 → systemctl restart
```

> Spring 환경변수 매핑: `SPRING_JPA_HIBERNATE_DDL_AUTO` → `spring.jpa.hibernate.ddl-auto`로 해석됨 (`SPRING_*`는 자동 매핑).

## 7단계 — 헬스체크

```bash
# 박스 내부에서
curl http://localhost:8080/api/health
# → {"status":"UP",...}
```

여기까지 **백엔드가 박스 내부에서 동작**. 외부 노출은 nginx + Let's Encrypt (다음 가이드 L6).

## 트러블슈팅

- **Spring 시작 실패 (env 누락)**: `journalctl -u algoj-api -f`에서 `Could not resolve placeholder 'DB_PASSWORD'` 같은 메시지 확인. `.env`의 변수 이름/값 점검.
- **MySQL connection refused**: `docker ps`로 algoj-mysql 상태 + `docker logs algoj-mysql`에서 startup 로그 확인. 포트 충돌 시 `127.0.0.1:3306`이 다른 프로세스에 잡혀있는지 `sudo ss -tlnp | grep 3306`.
- **OOM / slow**: `free -h`로 swap 사용량 확인. JVM heap 줄이기: systemd unit의 `-Xmx400m`을 `-Xmx256m`으로 (단 너무 줄이면 GC 비용 증가).

## 운영 명령 cheat sheet

```bash
sudo systemctl status algoj-api      # 상태
sudo systemctl restart algoj-api     # 재시작
journalctl -u algoj-api -f           # 라이브 로그
docker compose --env-file .env logs -f mysql
docker compose --env-file .env restart mysql
```

새 jar 배포:

```bash
# 로컬
./gradlew clean bootJar
scp build/libs/algoj.jar ubuntu@<박스IP>:/opt/algoj/algoj.jar

# 박스
sudo systemctl restart algoj-api
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
3. **deploy** (`DEPLOY_ENABLED=true`일 때만): `docker-compose.prod.yml`을 박스로 복사한 뒤
   SSH로 접속해 `docker compose pull && up -d` 실행.

### 필요한 GitHub Secrets / Variables

저장소 **Settings → Secrets and variables → Actions**에서 설정.

| 종류 | 이름 | 설명 |
|------|------|------|
| Variable | `DEPLOY_ENABLED` | `true`여야 deploy 잡이 동작. 미설정 시 build+push까지만. |
| Secret | `SSH_HOST` | 배포 박스 IP/호스트 |
| Secret | `SSH_USER` | SSH 사용자 (예: `ubuntu`) |
| Secret | `SSH_KEY` | SSH 개인키 (PEM 전체) |
| Secret | `SSH_PORT` | (선택) 기본 22 |
| Secret | `GHCR_PAT` | (선택) 박스에서 GHCR pull용 read:packages 토큰. 패키지를 public으로 두면 불필요. |

> GHCR 패키지는 기본 **private**이다. 박스가 이미지를 받으려면 `GHCR_PAT`로 로그인하거나,
> GHCR 패키지 페이지에서 visibility를 **public**으로 바꾼다.

### 박스 1회 준비 (컨테이너 배포용)

```bash
cd /opt/algoj
# .env 는 기존 그대로 사용 (B 방식과 공유). DB_HOST/JUDGE0_URL 는 compose가 override 한다.
# docker-compose.prod.yml 은 CD가 매 배포마다 자동 복사하므로 수동 준비 불필요.

# 기존 systemd jar 방식과 충돌 방지 — 컨테이너로 전환한다면 systemd unit 중지
sudo systemctl disable --now algoj-api || true

# 첫 배포 시 스키마 생성 (prod 는 ddl-auto=validate 라 빈 DB면 실패)
echo "SPRING_JPA_HIBERNATE_DDL_AUTO=update" >> /opt/algoj/.env   # 1회만, 이후 제거
```

이후 `master`에 머지하면 자동 배포된다. 수동 트리거는 Actions 탭의 **CD → Run workflow**.

---

## 무중단 배포 (블루-그린)

API를 새 이미지로 교체할 때 다운타임이 없도록, 배포는 **구/신 컨테이너를 잠깐 동시에**
띄우고 헬스 통과 후 nginx를 전환한다(`deploy/deploy-api.sh`). API는 더 이상 compose 서비스가
아니며(`docker-compose.prod.yml`은 MySQL만 관리), `algoj-api-blue`/`algoj-api-green`가
포트 `8081`/`8082`를 번갈아 쓴다. nginx는 `upstream algoj_api`로 활성 색에 프록시한다.

### 박스 1회 설정

```bash
# 0) (기존 운영 박스 전환 시만) 기존 compose api 컨테이너를 내려 8080 포트를 비운다.
#    새 nginx-internal.conf 가 127.0.0.1:8080 을 점유하므로 충돌 방지.
docker rm -f algoj-api 2>/dev/null || true

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

> 봇은 그대로 `OJ_API_BASE_URL=http://127.0.0.1:8080` 을 쓰면 된다 — `algoj-internal.conf`가
> 8080을 활성 색으로 항상 연결해준다(블루-그린 포트와 무관).

### 동작 / 운영

- CD가 `deploy-api.sh`를 SSH로 실행: 새 색 컨테이너 기동 → `/api/health`(DB까지 확인) 통과 대기 →
  `algoj-upstream.conf` 포트 교체 → `nginx -s reload` → 구 컨테이너 graceful drain(최대 60s).
- **자동 안전 롤백**: 새 컨테이너가 헬스 통과 못 하면 nginx를 건드리지 않고 종료 → 구 컨테이너가 계속 서빙.
- **메모리**: 겹침 구간에 JVM 2개가 잠깐 뜬다. `free -h`로 여유 확인하고, 빠듯하면 swap을 추가하거나
  스크립트가 가용 RAM이 임계(기본 700MB) 미만일 때 힙을 자동 축소(`-Xmx320m`)한다. 수동 지정은
  `JAVA_OPTS=... bash deploy-api.sh`.
- 무중단 확인:
  ```bash
  while true; do curl -s -o /dev/null -w "%{http_code}\n" https://algoj.duckdns.org/api/health; sleep 0.2; done
  # 배포 중에도 200이 끊기지 않아야 함
  ```

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

### 3단계 — 백엔드 스키마 업데이트 (1회)

이번 변경으로 `users` 테이블에 `discord_user_id` 컬럼이 추가된다. prod는 `ddl-auto=validate`라
컬럼이 없으면 startup 실패 → **새 api 이미지 배포 시 1회만** 스키마 업데이트:

```bash
# .env 에 임시로 추가 후 api 재기동 → 컬럼 생성 확인 → 줄 제거하고 재기동
echo "SPRING_JPA_HIBERNATE_DDL_AUTO=update" >> /opt/algoj/.env
docker compose -f docker-compose.prod.yml --env-file .env up -d
# 확인:
docker exec algoj-mysql mysql -ualgoj -p"$(grep '^DB_PASSWORD=' .env | cut -d= -f2)" algoj \
  -e "SHOW COLUMNS FROM users LIKE 'discord_user_id';"
# 확인되면 .env 에서 SPRING_JPA_HIBERNATE_DDL_AUTO 줄 제거 후 다시 up -d
```

### 4단계 — 봇 실행

봇 이미지는 CD가 `ghcr.io/<owner>/oj-bot:latest`로 빌드/푸시한다(패키지 public 또는 GHCR 로그인 필요).

```bash
cd /opt/algoj
export BOT_IMAGE=ghcr.io/sjh1108/oj-bot
docker compose -f docker-compose.bot.yml --env-file .env pull
docker compose -f docker-compose.bot.yml --env-file .env up -d
docker logs algoj-bot --tail 30      # "Logged in as ..." + 슬래시 명령 등록 로그
```

컨테이너 시작 시 슬래시 명령(`/연동`, `/비밀번호분실`)을 길드에 자동 등록한다.

> **봇 → 백엔드 연결**: prod compose는 API를 `127.0.0.1:8080`(루프백 전용)에만 바인딩하므로
> 봇은 `docker-compose.bot.yml`의 `network_mode: host` + `OJ_API_BASE_URL=http://127.0.0.1:8080`
> 으로 호스트 네트워크를 공유해 접근한다. (브릿지 `host.docker.internal`로는
> `ECONNREFUSED`가 난다.) `/opt/algoj`에 `docker-compose.bot.yml`이 없으면 repo의
> `deploy/docker-compose.bot.yml`을 그대로 올려두면 된다(CD는 prod compose만 자동 복사).

### 사용 흐름 (회원)

1. OJ 로그인 → 우상단 본인 이름(`/account`) → **디스코드 연동 → 연동 코드 발급**
2. 디스코드에서 `/연동 <코드>` 입력 → "○○ 계정과 연동되었습니다"
3. 비밀번호를 잊으면 `/비밀번호분실` → **본인만 보이는** 임시 비밀번호 수신
4. 그 비밀번호로 로그인 → `/account`에서 새 비밀번호로 변경

> 연동은 "비번을 잊기 전"에 해둬야 한다(잊은 뒤 미연동자는 로그인 불가 → 연동도 불가).
> 그런 경우엔 관리자가 **회원 관리** 페이지에서 직접 재설정하면 된다.
