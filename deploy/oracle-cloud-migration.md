# AWS → Oracle Cloud 이전 런북

AWS 무료 크레딧이 소진되어 서비스가 멈췄다. 네 조각(OJ Lightsail · EOJ EC2 · JJ EC2 · RDS)에
흩어져 있던 구성을 **Oracle Cloud Always Free 인스턴스 한 대(Ampere, 2 OCPU / 12GB)** 로 합친다.
12GB는 예전 2GB 박스가 감당 못 해 밖으로 뺐던 MySQL·RabbitMQ를 도로 안으로 들여도 남는다.

```
        Vercel (Next.js)                     그대로 (무료, 도메인만 재지정)
              │ HTTPS  algoj.duckdns.org     DuckDNS → 새 박스 공인 IP
              ▼
  ┌──────── OCI Ampere (2 OCPU / 12GB) ────────┐
  │ nginx  TLS 종단 + blue-green 업스트림       │
  │ API    algoj-api-blue|green (8081/8082)    │
  │ MySQL 8·RabbitMQ 4  (algoj-net, 포트 비공개)│
  │ Discord 봇 (host network)                  │
  └────────────────────┬───────────────────────┘
                       ▼  JUDGE0_URL
              Judge0 (아키텍처 이슈 — 아래 1단계)
```

> **이 문서를 읽는 순서**: 1단계(아키텍처 확인)와 2단계(AWS 데이터 구출)를 먼저 끝내야
> 나머지가 의미 있다. 특히 2단계는 **AWS 쪽 리소스가 살아 있는 동안에만** 가능하다.

## PR로 자동 반영되는 것 / 박스에서 손으로 할 일

| 자동 (이 PR + CD) | 수동 (박스·콘솔) |
|---|---|
| API·봇 이미지를 **arm64로도** 빌드해 GHCR에 푸시 | OCI 인스턴스 정리(마크 서버 잔재 제거)·docker 설치 |
| CD가 **단일 박스 blue-green**으로 배포 (`DEPLOY_TOPOLOGY` 미설정 시 기본) | `.env` 작성, `docker-compose.oci.yml` 기동 |
| API 컨테이너가 `algoj-net`에 자동 합류 (DB_HOST=mysql 해석) | MySQL 데이터 적재(덤프 import) |
| 12GB 박스 감지 시 힙 프로필 자동 상향 | nginx·certbot(TLS)·DuckDNS IP 재지정 |
| S3 호환 스토리지 설정 지원(`S3_ENDPOINT` 등) | 이미지 버킷 생성·업로드, GitHub Secrets(SSH_HOST 등) 교체 |
| — | **Judge0 박스(1단계 결정)** |

---

## 1단계 — 박스 아키텍처 확인 (가장 먼저)

```bash
uname -m        # aarch64 = Ampere(ARM) / x86_64 = AMD
free -m; df -h  # 12GB인지, 부트 볼륨 여유가 있는지
```

**Ampere는 aarch64(ARM)다.** 여기서 갈린다.

- **API·MySQL·RabbitMQ·봇·nginx** → 전부 arm64 이미지가 있다. 문제 없다.
  (이 PR이 API·봇 이미지를 `linux/amd64,linux/arm64` 멀티아치로 푸시하도록 바꿔 놨다.
  이게 없으면 ARM 박스에서 컨테이너가 `exec format error`로 아예 안 뜬다.)
- **Judge0** → 공식 이미지 `judge0/judge0:1.13.1`은 **단일 아키텍처(amd64)** 다.
  멀티아치 인덱스가 아니라 ARM 박스에서 그냥 안 돈다. 선택지:

| 방안 | 비용 | 작업량 | 메모 |
|---|---|---|---|
| **A. OCI Always Free AMD micro**(1 OCPU/1GB) 1대에 Judge0 | 0 | 소 | x86이라 그대로 뜬다. 다만 Judge0 권장 사양(2GB)보다 작아 워커 1개로 줄이고 메모리 한도를 낮춰야 한다. **먼저 이걸 시도** |
| **B. 자체 arm64 채점 러너**(isolate + 언어 툴체인, Judge0 호환 API) | 0 | 대 | 앱은 `JUDGE0_URL`만 바꾸면 된다 — 실제로 쓰는 API는 `POST /submissions?wait=true`와 `GET /languages` 둘뿐이다. A가 메모리로 실패하면 이쪽 |
| C. x86 저가 VPS(Hetzner·Vultr) | 월 $4~5 | 소 | 가장 확실하지만 "무료" 목표에서 벗어난다 |
| D. Ampere에서 QEMU 에뮬레이션 | 0 | 소 | **비추천** — 실행 속도가 왜곡돼 시간 제한 채점이 무의미해진다 |

A/C를 고르면 `.env`의 `JUDGE0_URL`만 그 박스를 가리키면 되고, 앱 코드는 안 건드린다.
PyPy 3는 새 Judge0 박스에서 `deploy/judge0/README.md` 절차를 한 번 더 밟아야 한다
(안 하면 PyPy 제출만 S002로 실패한다).

---

## 2단계 — AWS에 남은 데이터 구출 (시간이 걸린 일)

크레딧 소진으로 **인스턴스가 정지**된 것과 **리소스가 삭제**된 것은 다르다. 콘솔에서 확인한다.

```bash
# RDS: 인스턴스 또는 스냅샷이 남아 있는지
aws rds describe-db-instances --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]' --output table
aws rds describe-db-snapshots  --query 'DBSnapshots[].[DBSnapshotIdentifier,SnapshotCreateTime]' --output table

# S3: 지문 이미지 버킷이 남아 있는지
aws s3 ls s3://algoj-images/problems/ --summarize | tail -3
```

- **RDS가 살아 있으면** 덤프부터 뜬다 (새 박스든 로컬이든 3306에 닿는 곳에서):
  ```bash
  mysqldump -h <rds-endpoint> -u algoj -p \
    --single-transaction --routines --default-character-set=utf8mb4 \
    algoj > algoj-$(date +%F).sql
  ```
- **인스턴스는 지워졌는데 스냅샷이 있으면** 스냅샷으로 `db.t3.micro`를 잠깐 복원해 덤프만 뜨고
  바로 지운다 (몇 시간치 비용만 든다).
- **S3 이미지**는 통째로 내린다. 지문 본문에 절대 URL이 박혀 있으므로 **키 경로를 유지**해야 한다:
  ```bash
  aws s3 sync s3://algoj-images/problems/ ./s3-images/problems/
  ```
- **둘 다 없으면**: 빈 DB로 시작한다. Flyway가 스키마를 만들어 주고, 문제는 `.md` 파일로 다시
  업로드하면 복구된다. **제출 기록·유저 계정은 복구 불가** — 스터디원에게 재가입 안내가 필요하다.

> 이미지 URL은 지문에 `https://algoj-images.s3.ap-northeast-2.amazonaws.com/...` 형태로 남는다.
> 버킷을 새 스토리지로 옮기면 이 URL들도 새 주소로 일괄 치환해야 한다(6단계).

---

## 3단계 — 박스 준비

마크 서버로 쓰던 인스턴스를 재활용한다면 **기존 컨테이너·서비스부터 정리**한다.

```bash
docker ps -a                  # 예전 마크 서버 컨테이너가 있으면 제거
sudo systemctl list-units --type=service | grep -i -E 'minecraft|paper|spigot'
sudo apt update && sudo apt install -y docker.io docker-compose-v2 nginx
sudo usermod -aG docker ubuntu && newgrp docker
sudo mkdir -p /opt/algoj && sudo chown ubuntu:ubuntu /opt/algoj
```

**OCI에서 흔히 막히는 지점 — 방화벽이 두 겹이다.**

1. 콘솔의 **보안 목록(Security List)/NSG** 인그레스에 TCP 80·443 추가.
2. 인스턴스 안의 iptables도 따로 막고 있다 (OCI Ubuntu 이미지 기본값):
   ```bash
   sudo iptables -I INPUT 6 -p tcp --dport 80  -j ACCEPT
   sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save     # Oracle Linux면 firewall-cmd --add-service=http --permanent
   ```
   둘 중 하나만 열면 증상이 "연결이 그냥 멈춤"이라 원인 찾기가 오래 걸린다.

DB·브로커 포트(3306·5672)는 **열지 않는다** — 같은 docker 네트워크 안에서만 쓴다.

---

## 4단계 — `.env`와 스택 기동

`/opt/algoj/.env` (기존 AWS 박스 값에서 아래 항목만 바뀐다):

```ini
DB_HOST=mysql                 # RDS 엔드포인트 → 같은 네트워크의 컨테이너 이름
DB_PORT=3306
DB_NAME=algoj
DB_USER=algoj
DB_PASSWORD=<기존 값 유지 가능>
MYSQL_ROOT_PASSWORD=<새로 생성>
RABBITMQ_HOST=rabbitmq        # JJ 사설 IP → 컨테이너 이름
RABBITMQ_USER=algoj
RABBITMQ_PASSWORD=<기존 값>
JUDGE0_URL=http://<1단계에서 정한 채점 박스>:2358
JWT_SECRET=<기존 값 그대로>    # 바꾸면 기존 로그인 토큰이 전부 무효화된다
CORS_ALLOWED_ORIGINS=https://oj-oui2.vercel.app
SWEEPER_ENABLED=true          # 박스가 하나이므로 여기서만 켠다
```

```bash
cd /opt/algoj
chmod 600 .env
docker compose -f docker-compose.oci.yml --env-file .env up -d
docker compose -f docker-compose.oci.yml ps     # mysql·rabbitmq healthy 확인
```

`docker-compose.oci.yml`은 두 서비스를 **`algoj-net`** 네트워크에 올리고 호스트 포트를 아예
공개하지 않는다. API 컨테이너는 `deploy-api.sh`가 이 네트워크를 찾아 자동으로 합류시킨다.

---

## 5단계 — 데이터 적재

```bash
# 2단계 덤프가 있으면
docker exec -i algoj-mysql mysql -ualgoj -p"$DB_PASSWORD" algoj < algoj-YYYY-MM-DD.sql
docker exec -i algoj-mysql mysql -ualgoj -p"$DB_PASSWORD" -e \
  "SELECT COUNT(*) FROM problem; SELECT COUNT(*) FROM submission;" algoj
```

스키마는 **Flyway**가 API 부팅 시 맞춘다. 덤프에 `flyway_schema_history`가 같이 들어오므로
버전이 이어지고, 빈 DB로 시작하면 V1부터 새로 적용된다. `ddl-auto`는 계속 `validate`다.

---

## 6단계 — 이미지 스토리지 (S3 탈출)

AWS에 코드로 묶여 있던 유일한 지점이다. 이 PR에서 **S3 호환 스토리지**를 쓸 수 있게 열어 놨다:

```ini
S3_IMAGE_BUCKET=algoj-images
AWS_REGION=<리전>                      # R2는 auto
S3_ENDPOINT=https://<네임스페이스>.compat.objectstorage.<리전>.oraclecloud.com
S3_PUBLIC_BASE_URL=https://<공개 읽기 주소>/algoj-images
AWS_ACCESS_KEY_ID=<Customer Secret Key>
AWS_SECRET_ACCESS_KEY=<...>
```

- OCI Object Storage(S3 호환) 또는 Cloudflare R2 둘 다 무료 구간이 있다.
  둘 다 path-style 주소를 쓰므로 `S3_ENDPOINT`를 채우면 자동으로 path-style로 동작한다.
- **네 항목을 모두 비우면 예전처럼 AWS S3로 붙는다** — 기존 동작은 그대로다.
- 옮긴 뒤 기존 지문에 박힌 URL 치환 (백업 먼저):
  ```sql
  UPDATE problem
     SET description = REPLACE(description,
           'https://algoj-images.s3.ap-northeast-2.amazonaws.com/',
           'https://<새 공개 주소>/algoj-images/');
  ```
- 이미지가 소수면 SVG·재업로드로 대체해도 된다.

---

## 7단계 — nginx + TLS + DNS

DuckDNS(`algoj.duckdns.org`)는 무료라 **IP만 새 박스로 바꾸면 끝**이다.

```bash
curl "https://www.duckdns.org/update?domains=algoj&token=<토큰>&ip=<새 공인 IP>"
```

박스에서:

```bash
# 업스트림 시드 + 내부 고정 진입점 (deploy/nginx/ 의 두 파일)
sudo cp nginx/algoj-upstream.conf nginx/algoj-internal.conf /etc/nginx/conf.d/
# 공개 TLS 사이트 블록의 proxy_pass 는 http://algoj_api (업스트림)로
sudo certbot --nginx -d algoj.duckdns.org
sudo nginx -t && sudo systemctl reload nginx
```

`deploy-api.sh`가 배포 때마다 `algoj-upstream.conf`를 blue/green 포트로 다시 쓰고 reload 하므로
**손으로 고치지 않는다**. `render-upstream.sh`/`rolling-deploy.sh`는 두 박스 전용이라 여기선 안 쓴다.

배포 스크립트가 `sudo nginx -t`·`sudo tee`를 무암호로 실행할 수 있어야 한다
(`deploy/README.md`의 sudoers 항목 참고).

---

## 8단계 — CD 재연결

GitHub 저장소 설정에서:

- **Secrets**: `SSH_HOST`(새 공인 IP), `SSH_USER`(보통 `ubuntu`), `SSH_KEY`(OCI 인스턴스 키),
  `SSH_PORT`(기본 22).
- **Variables**: `DEPLOY_ENABLED=true`. `DEPLOY_TOPOLOGY`는 **비워 둔다** — 기본이 단일 박스
  blue-green이다. (두 박스 구성이 다시 생기면 그때 `rolling`으로 설정한다.)

첫 배포는 `workflow_dispatch`로 수동 실행해 arm64 이미지가 제대로 뜨는지 본다:

```bash
docker inspect --format '{{.Architecture}}' ghcr.io/sjh1108/oj-api:latest   # arm64
docker logs algoj-api-blue --tail 50
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/api/health   # 200
```

봇도 새 박스에서 올린다:

```bash
docker compose -f docker-compose.bot.yml --env-file .env pull
docker compose -f docker-compose.bot.yml --env-file .env up -d
```

---

## 9단계 — 프론트(Vercel)

Vercel 프로젝트 환경변수의 API 주소가 `https://algoj.duckdns.org`를 그대로 가리키면
**바꿀 것이 없다**(도메인이 유지되므로). 도메인을 바꾼 경우에만 환경변수 수정 후 재배포하고,
백엔드 `.env`의 `CORS_ALLOWED_ORIGINS`도 같이 맞춘다.

---

## 컷오버 체크리스트

- [ ] `uname -m` 확인, 채점기 경로 결정(1단계)
- [ ] RDS 덤프·S3 이미지 확보(2단계) — **AWS 리소스가 살아 있는 동안**
- [ ] 보안 목록 + iptables 80/443
- [ ] `docker-compose.oci.yml` 기동, mysql·rabbitmq healthy
- [ ] 덤프 import 후 문제·제출 건수 확인
- [ ] Judge0 기동 + PyPy 등록, `curl $JUDGE0_URL/languages`
- [ ] DuckDNS IP 재지정, certbot 인증서 발급
- [ ] CD Secrets 교체 후 수동 배포 1회 → `/api/health` 200
- [ ] 웹에서 로그인 → 문제 열람 → **제출이 실제로 채점되는지**
- [ ] 봇 `/서버상태` 정상 응답

## 되돌리기

이전 중에는 AWS 쪽을 지우지 않는다. 새 박스가 안 뜨면 DuckDNS IP를 예전 박스로 되돌리는 것만으로
복구된다 — 단, 크레딧이 없으면 예전 박스 자체가 안 켜지므로 실질적인 롤백 창은 **DNS TTL이 아니라
AWS 리소스가 남아 있는 기간**이다. 그래서 2단계(덤프)를 가장 먼저 한다.
