# AWS 데이터 회수 절차 (유료 전환 후)

무료 크레딧이 끊겨 멈춘 AWS 계정을 **일시적으로 유료 전환해** 남은 데이터를 꺼내오고,
새 박스(Oracle Ampere)로 옮긴 뒤 AWS를 정리한다. 이전 자체는 이미 끝났고
([`oracle-cloud-migration.md`](oracle-cloud-migration.md)) 서비스는 빈 DB로 돌고 있다.

**한 번에 끝내는 작업이다.** 켜놓는 시간이 곧 비용이므로, 시작 전에 아래를 전부 읽고
회수할 것과 버릴 것을 먼저 정한다.

## 회수 대상과 우선순위

| 대상 | 어디에 | 없으면 | 우선순위 |
|---|---|---|---|
| `.env` (JWT 비밀·**디스코드 봇 토큰**·BOT_API_KEY) | Lightsail 박스 `/opt/algoj/.env` | 봇을 못 살린다(토큰 재발급 필요) | **1** |
| DB 덤프 (문제·제출·계정) | RDS 인스턴스 또는 스냅샷 | 문제는 `.md` 재업로드, 제출·계정은 영구 소실 | **2** |
| 지문 이미지 | S3 `algoj-images` | 지문의 이미지가 깨진다 | 3 |

## 0단계 — 비용 가드부터

1. **Billing → Budgets**에 월 $5 예산과 알림을 건다. 회수 작업만 하면 실제 청구는
   커피값 수준이지만, 켜둔 걸 잊는 사고를 막는 건 알림뿐이다.
2. 작업 끝나는 시점을 정해둔다. RDS `db.t3.micro`는 시간당 $0.02 남짓, S3는 GB당 월 $0.023,
   데이터 전송은 GB당 $0.09 수준이다. **몇 시간 안에 끝내면 $1도 안 나온다.**

## 1단계 — 뭐가 남아 있는지 확인

크레딧 소진으로 **정지**된 것과 **삭제**된 것은 다르다. 콘솔이나 CLI로 먼저 본다.

```bash
aws rds describe-db-instances --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]' --output table
aws rds describe-db-snapshots  --query 'DBSnapshots[].[DBSnapshotIdentifier,SnapshotCreateTime,Status]' --output table
aws s3 ls s3://algoj-images/problems/ --summarize | tail -3
aws lightsail get-instances --query 'instances[].[name,state.name]' --output table
aws lightsail get-instance-snapshots --query 'instanceSnapshots[].[name,createdAt]' --output table
```

여기서 갈린다:

- **인스턴스가 살아 있다(stopped 포함)** → 켜서 꺼내온다. 가장 쉽다.
- **스냅샷만 있다** → 스냅샷에서 잠깐 복원해 꺼내고 바로 지운다.
- **둘 다 없다** → 그 항목은 포기한다. 아래 "없을 때" 절을 본다.

## 2단계 — `.env` 회수 (가장 급함)

```bash
aws lightsail start-instance --instance-name <OJ 인스턴스>
# 몇 분 뒤 공인 IP 확인
aws lightsail get-instance --instance-name <OJ 인스턴스> --query 'instance.publicIpAddress'

scp ubuntu@<그 IP>:/opt/algoj/.env ./algoj-aws.env
```

SSH 키는 **그 박스의 것**이다(새 Oracle 박스 키와 다르다). Lightsail 콘솔의
"Connect using SSH" 브라우저 터미널로 들어가 `cat /opt/algoj/.env` 한 뒤 복사해도 된다.

꺼낸 값 중 새 박스로 **가져올 것과 버릴 것**:

| 값 | 처리 |
|---|---|
| `DISCORD_*` 토큰, `BOT_API_KEY` | **가져온다** — 봇을 이 값으로 되살린다 |
| `JWT_SECRET` | 가져와도 되지만, 이미 새 값으로 운영 중이라 지금 바꾸면 **현재 로그인 세션이 전부 끊긴다**. 그대로 두는 걸 권한다 |
| `DB_*`, `RABBITMQ_*` | 버린다 — 새 박스의 값이 맞다 |
| `S3_*`, AWS 키 | 3단계 결정에 따라 |

## 3단계 — DB 덤프

**인스턴스가 살아 있으면**, 새 박스에서 바로 뜬다(RDS를 공개로 열 필요 없이,
보안그룹 3306에 새 박스 공인 IP를 임시로 허용):

```bash
mysqldump -h <rds-endpoint> -u algoj -p \
  --single-transaction --routines --default-character-set=utf8mb4 \
  algoj > algoj-$(date +%F).sql
```

**스냅샷만 있으면** `db.t3.micro`로 복원해 위를 그대로 하고, 덤프를 확보한 즉시 지운다:

```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier algoj-recover \
  --db-snapshot-identifier <스냅샷 이름> \
  --db-instance-class db.t3.micro
# ... 덤프 후
aws rds delete-db-instance --db-instance-identifier algoj-recover --skip-final-snapshot
```

덤프가 비었거나 몇 KB밖에 안 되면 잘못된 것이다 — 5단계로 넘어가기 전에 확인한다:

```bash
grep -c 'INSERT INTO' algoj-*.sql
grep -o 'INSERT INTO `[a-z_]*`' algoj-*.sql | sort | uniq -c
```

## 4단계 — 지문 이미지: 버킷을 유지할지 결정

지문 본문에 `https://algoj-images.s3.ap-northeast-2.amazonaws.com/...` 형태의 **절대 URL이
박혀 있다.** 선택지는 둘이다.

- **(권장) S3 버킷만 남긴다** — 이미지 몇 MB면 월 몇 센트다. AWS 계정을 유료로 유지하되
  RDS·Lightsail만 지우면 지문이 그대로 동작하고, DB 치환도 필요 없다.
- **OCI Object Storage로 옮긴다** — 완전히 AWS를 떠난다. 대신 URL 일괄 치환이 필요하다:

```bash
aws s3 sync s3://algoj-images/problems/ ./s3-images/problems/   # 키 경로 유지가 중요하다
```

옮긴 뒤 `.env`에 `S3_ENDPOINT`/`S3_PUBLIC_BASE_URL`을 채우고(앱이 S3 호환 스토리지를
지원한다), **DB 백업을 먼저 뜬 다음** 치환한다:

```sql
UPDATE problems
   SET description = REPLACE(description,
       'https://algoj-images.s3.ap-northeast-2.amazonaws.com/',
       'https://<새 공개 주소>/algoj-images/');
```

## 5단계 — 새 박스에 적재

> ⚠️ **덤프를 넣으면 지금 새 박스의 데이터가 사라진다.** mysqldump는 테이블마다
> `DROP TABLE` + `CREATE TABLE`을 포함하므로, 이전 후 만든 계정·문제·제출이 옛 데이터로
> **통째로 교체**된다. 관리자 계정도 옛 DB의 것으로 돌아간다.
> 새로 만든 것 중 지킬 게 있으면 먼저 따로 백업하거나, 적재 후 다시 만든다.

리허설로 절차부터 검증한다(운영 DB는 읽기만 한다):

```bash
bash /opt/algoj/repo/deploy/sql/rehearse-restore.sh
```

`PASS`가 나오면 실제 적재로 간다. **API를 먼저 세운다** — 적재 중에 앱이 읽고 쓰면
어중간한 상태가 된다.

```bash
cd /opt/algoj
docker stop algoj-api-blue algoj-api-green 2>/dev/null   # 살아 있는 쪽만 멈춘다

PW="$(grep -m1 '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$PW" algoj-mysql \
    mysql -uroot -e "DROP DATABASE IF EXISTS algoj;
                     CREATE DATABASE algoj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i -e MYSQL_PWD="$PW" algoj-mysql \
    mysql -uroot --default-character-set=utf8mb4 algoj < algoj-YYYY-MM-DD.sql

bash deploy-api.sh      # 기동하면서 Flyway가 부족한 마이그레이션을 이어서 적용한다
```

**스키마 버전 차이는 걱정하지 않아도 된다.** 옛 DB가 V5에서 멈춰 있어도 부팅 때 Flyway가
V6을 적용한다(`baseline-on-migrate`). 반대 방향은 생길 수 없다 — 코드가 항상 DB보다 앞선다.

확인:

```bash
docker exec -e MYSQL_PWD="$PW" algoj-mysql mysql -uroot algoj -e \
  "SELECT COUNT(*) users FROM users;
   SELECT COUNT(*) problems FROM problems;
   SELECT COUNT(*) submissions FROM submissions;
   SELECT MAX(version) schema_version FROM flyway_schema_history;"
curl -s http://127.0.0.1:8080/api/health
```

웹에서 옛 계정으로 로그인해 문제·제출 기록이 보이는지, 지문 이미지가 뜨는지 본다.
`JWT_SECRET`을 바꾸지 않았다면 비밀번호는 옛것 그대로 동작한다.

## 6단계 — 봇 되살리기

`.env`에 옛 `DISCORD_*`·`BOT_API_KEY`를 넣고:

```bash
cd /opt/algoj
docker compose -f docker-compose.bot.yml --env-file .env pull
docker compose -f docker-compose.bot.yml --env-file .env up -d
docker logs algoj-bot --tail 30
```

디스코드에서 `/서버상태`가 응답하면 된 것이다. 토큰을 못 건졌으면 디스코드 개발자
포털에서 재발급받아 넣는다(봇 재초대는 필요 없다).

## 7단계 — AWS 정리

회수가 끝나면 **그날 안에** 지운다. 이게 비용의 전부다.

```bash
aws rds delete-db-instance --db-instance-identifier <이름> --skip-final-snapshot
aws lightsail delete-instance --instance-name <이름>
```

- **S3 버킷은 4단계 결정에 따라** 남기거나 비우고 지운다.
- 스냅샷도 저장 비용이 있다. 덤프 파일을 확보했으면 지워도 된다 —
  다만 덤프가 정상인지 5단계까지 확인한 뒤에 지운다.
- 며칠 뒤 **Billing → Cost Explorer**에서 청구가 멈췄는지 확인한다.

## 데이터가 없을 때

- **DB를 못 건졌다** — 문제는 `.md` 파일로 다시 업로드하면 복구된다(생성기 양식이면
  시드가 같아 테스트데이터까지 바이트 단위로 재현된다). 제출 기록과 계정은 복구 불가이므로
  스터디원에게 재가입을 안내한다.
- **이미지를 못 건졌다** — 지문의 이미지 URL이 깨진다. 간단한 도형·다이어그램은 인라인
  SVG로 바꾸는 편이 낫다(가볍고 확대해도 선명하다).
- **`.env`를 못 건졌다** — 디스코드 개발자 포털에서 봇 토큰을 재발급한다. `BOT_API_KEY`는
  새로 만들어 `.env`와 봇 양쪽에 같은 값을 넣으면 된다.
