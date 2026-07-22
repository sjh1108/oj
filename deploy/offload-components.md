# 컴포넌트 분리 (단일 박스 부하 오프로드)

이 저장소의 기본 배포는 **한 박스(Lightsail ≈2GB)에 nginx·API·MySQL·RabbitMQ·Judge0·봇을 전부**
올린다. 상태가 있거나 무거운 컴포넌트를 별도 인스턴스/관리형 서비스로 빼면 **박스 메모리가 확보되고
(→ 부팅↑·배포 다운타임↓)**, 백업·격리 같은 운영 이점도 생긴다.

분리하기 좋은 순서:

1. **MySQL → AWS RDS** — 상태 저장소라 관리형(백업·패치·장애조치) 이점이 크고, 이미 `DB_HOST`로 접근.
2. **Judge0 → 별도 EC2(JJ)** — 임의 코드 실행 샌드박스라 **격리** 이점, 이미 `JUDGE0_URL`로 접근.
3. **RabbitMQ → JJ** — 채점 큐를 Judge0 옆으로 모아 **채점 인프라를 한 박스로 통합**하고, API 이중화
   (OJ+EOJ)가 공통 브로커를 바라볼 수 있게 한다(`deploy/redundancy.md` 선행 조건). 이미 `RABBITMQ_HOST`로 접근.

> **왜 이게 쉬운가**: 세 컴포넌트 모두 API가 **네트워크 주소(설정)로만** 접근한다. 그래서 이전은
> "데이터/서비스를 새 위치에 세우고 → 주소만 바꾸는" 작업이 핵심이고, 애플리케이션 코드는 안 건드린다.
>
> ⚠️ 단, `deploy/deploy-api.sh`가 과거 API 컨테이너에 **`DB_HOST=mysql`, `JUDGE0_URL=http://host.docker.internal:2358`,
> `RABBITMQ_HOST=rabbitmq`를 하드코딩 주입**해 `.env` 값을 덮어썼다(run_args). 이전 때마다 그 하드코딩을
> **제거하는 PR**이 함께 필요했고, 셋 다 제거되어 지금은 전부 `.env`에서 온다.

---

## A. MySQL → AWS RDS

### 왜
- **관리형 백업/스냅샷·자동 패치·장애조치** → 스터디 데이터 안정성↑.
- 박스에서 MySQL이 사라져 **~수백 MB 확보** + 배포 시 DB를 안 건드림(현재 CD는 매 배포마다 mysql 컨테이너를 재생성).
- 신규 계정은 **RDS 프리티어**(`db.t3.micro` 12개월, 750h/월)로 무료 구간 부담이 적음(이후 ~$15/월+스토리지).

### AWS에서 할 일 (1회)

1. **RDS 인스턴스 생성**: 엔진 **MySQL 8.0**, 클래스 `db.t3.micro`, 스토리지 20GB, **Lightsail과 같은 리전**.
   - **"초기 데이터베이스 이름"을 `algoj`로 반드시 지정**한다(생성 화면 "추가 구성"에 있음). 비우면 서버만
     생기고 DB(스키마)가 없어 `Unknown database 'algoj'`로 import·연결이 실패한다(놓쳤으면 이관 전에
     `CREATE DATABASE`로 만든다 — 아래 2번에 포함).
   - **DB 인스턴스 식별자**(예: `algoj-db`)는 서버 이름표일 뿐, DB 스키마 `algoj`와는 다른 층위다.
   - 문자셋은 앱과 맞춘다(`utf8mb4` / `utf8mb4_unicode_ci`).
   - **네트워크/보안**: RDS는 **공개(Public) 금지**. Lightsail ↔ RDS(EC2 VPC)를 **VPC 피어링**으로 연결하고,
     RDS 보안그룹의 3306을 **Lightsail 박스에서 오는 트래픽만** 허용한다.
2. **데이터 이관** (박스에서 — 로컬 mysql 컨테이너의 클라이언트를 재사용):
   ```bash
   cd /opt/algoj
   PW="$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)"   # RDS 마스터 암호를 이 값과 동일하게 만든 경우
   # (필요 시) '초기 데이터베이스 이름'을 안 넣었으면 RDS에 algoj DB 먼저 생성
   docker exec -e MYSQL_PWD="$PW" algoj-mysql \
     mysql -h <rds-endpoint> -ualgoj \
     -e "CREATE DATABASE IF NOT EXISTS algoj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   # 덤프 (--no-tablespaces: PROCESS 권한 없는 앱 유저용. flyway_schema_history 포함 → RDS서 재적용 안 함)
   docker exec algoj-mysql sh -c 'mysqldump -ualgoj -p"$MYSQL_PASSWORD" \
     --no-tablespaces --single-transaction --routines --triggers algoj' > algoj-dump.sql
   # 덤프 온전성 확인: 끝에 "-- Dump completed", CREATE TABLE 7개면 정상
   tail -3 algoj-dump.sql; grep -c "CREATE TABLE" algoj-dump.sql
   # RDS로 적재
   docker exec -i -e MYSQL_PWD="$PW" algoj-mysql \
     mysql -h <rds-endpoint> -ualgoj algoj < algoj-dump.sql
   ```
3. **검증** (로컬과 테이블·행 수가 같은지):
   ```bash
   docker exec -e MYSQL_PWD="$PW" algoj-mysql \
     mysql -h <rds-endpoint> -ualgoj algoj -e "SHOW TABLES; SELECT COUNT(*) FROM submissions;"
   ```

### PR로 반영 (데이터 이관·검증 후에만)

- **`deploy/deploy-api.sh`**: run_args에서 **`-e DB_HOST=mysql -e DB_PORT=3306` 줄 제거** → `--env-file .env`의
  `DB_HOST`/`DB_PORT`가 그대로 적용된다. 그리고 **`waiting for $MYSQL_CONTAINER healthy` 대기 블록을 가드**
  한다(로컬 mysql 컨테이너가 없으면 60초 헛대기 → RDS 사용 시 이 단계 건너뛰도록).
- **`deploy/docker-compose.prod.yml`**: **`mysql` 서비스 삭제**(RDS가 DB를 소유), `mysql`의
  `mem_limit`/볼륨/헬스체크도 함께 제거. (이후 RabbitMQ도 JJ로 이전하며 이 파일에 남는 서비스가 없어져
  파일 자체가 제거되고, RabbitMQ는 `docker-compose.jj.yml`로 옮겨졌다 — 아래 C 참고.)
- (그대로) Flyway는 부팅 시 **RDS**를 대상으로 마이그레이션한다 — 코드 변경 없음.

### 박스 `.env` 갱신 (1회)
```
DB_HOST=<rds-endpoint>      # 예: algoj-db.abcxyz.ap-northeast-2.rds.amazonaws.com
DB_PORT=3306
# DB_NAME/DB_USER/DB_PASSWORD 는 RDS에 만든 값으로
```

### 정리 후
- 박스의 `algoj-mysql` 컨테이너·`mysql-data` 볼륨 제거 → 메모리·디스크 확보.

---

## B. Judge0 → 별도 EC2

### 왜
- Judge0는 **임의 코드를 실행**한다 → 웹/DB와 **격리**하면 보안·안정성↑.
- 채점에 **CPU를 독립적으로** 줄 수 있고, 박스에서 judge0 컨테이너 4개(server/workers/postgres/redis)가
  사라져 **~300~400MB 확보**.

### EC2에서 할 일 (1회)

1. **EC2 인스턴스**: Ubuntu, **4GB+ RAM**, Lightsail과 **같은 리전**. Judge0는 isolate 샌드박스가 cgroup을
   요구하는데 표준 EC2는 커널 제어가 되어 문제없다(지금 박스와 동일 요건).
2. **Judge0 설치**: 기존 박스의 Judge0 `docker-compose.yml` 구성을 그대로 복제해 기동
   (server·workers·db(postgres)·redis). **PyPy 3는 새 박스에서 다시 설정**해야 한다 —
   `deploy/judge0/README.md`의 1~3단계(포터블 PyPy 마운트 + `add-pypy.sql`)를 EC2에서 반복.
3. **보안(중요)**: `2358`은 **API 박스에서만** 접근 허용. **절대 0.0.0.0/0로 공개 금지**.
   - Lightsail ↔ EC2 **VPC 피어링** 후 **사설 IP**로 접근 + EC2 보안그룹 2358을 Lightsail 박스로 제한.
   - (피어링이 어려우면 EC2 공인 IP + 보안그룹을 Lightsail **공인 IP/32**로 잠금 — 트래픽이 공용망을 타므로 차선.)
4. **검증**(EC2 내부): `curl -s http://127.0.0.1:2358/languages | grep -i pypy`, 샘플 제출로 실행 확인
   (`deploy/judge0/README.md` 4단계).

### PR로 반영 (EC2 Judge0 검증 후에만)

- **`deploy/deploy-api.sh`**: run_args에서 **`-e JUDGE0_URL=http://host.docker.internal:2358`과
  `--add-host host.docker.internal:host-gateway` 제거** → `.env`의 `JUDGE0_URL`이 적용된다.

### 박스 `.env` 갱신 (1회)
```
JUDGE0_URL=http://<ec2-사설IP>:2358      # 피어링 사용 시 사설 IP
```

### 정리 후
- 기존 박스에서 judge0 컨테이너 4개 내림(`docker rm -f`) → 메모리 확보.
- `/서버상태`(monitor)의 Judge0 헬스체크는 새 URL로 자동 동작(코드 변경 없음). read-timeout 등 기존 설정 유지.

---

## C. RabbitMQ → JJ (Judge0와 같은 EC2)

### 왜
- **채점 인프라 통합**: 큐(RabbitMQ)와 채점기(Judge0)를 한 박스(JJ)에 모아 운영 지점을 단순화.
- **이중화 선행 조건**: API를 OJ+EOJ 두 박스로 이중화하려면(`deploy/redundancy.md`) 두 API가 **공통으로
  바라볼 브로커**가 필요하다. OJ 도커 네트워크 안의 `rabbitmq`는 EOJ에서 닿을 수 없으므로 JJ로 옮긴다.
- OJ 박스에서 RabbitMQ(~100MB)가 빠져 메모리도 소폭 확보.

> RabbitMQ는 **우리 API의 작업 큐**(producer=API, consumer=API의 `@RabbitListener`)다. Judge0가
> 내부적으로 쓰는 redis/postgres와는 별개이므로, JJ에서 Judge0 옆에 **독립 컨테이너**로 띄운다.
>
> **안전망**: 이전 중 구 브로커에 쌓여 있던 큐 메시지가 유실돼도, `PendingSubmissionSweeper`가
> 300초 이상 PENDING인 제출을 자동 재큐잉하므로 채점이 누락되지 않는다. 그래서 `rabbitmq-data`
> 볼륨을 굳이 옮기지 않아도 되고, 새 브로커를 빈 상태로 시작해도 된다.

### JJ에서 할 일 (1회)

1. **브로커 기동**: `deploy/docker-compose.jj.yml`을 JJ의 `/opt/algoj`(또는 judge0 디렉터리)로 복사하고,
   `.env`에 `RABBITMQ_USER`/`RABBITMQ_PASSWORD`를 둔 뒤:
   ```bash
   docker compose -f docker-compose.jj.yml --env-file .env up -d
   docker logs algoj-rabbitmq --tail 20      # "Server startup complete" 확인
   ```
2. **보안(중요)**: JJ 보안그룹 inbound **5672**를 **OJ(그리고 이후 EOJ)의 사설 IP에서만** 허용.
   **절대 0.0.0.0/0로 공개 금지**(브로커 자격증명만으로는 부족 — 네트워크로 잠근다).
3. **검증**(JJ 내부): `docker exec algoj-rabbitmq rabbitmqctl list_queues name messages consumers`.

### PR로 반영 (JJ 브로커 검증 후에만)

- **`deploy/deploy-api.sh`**: run_args에서 **`-e RABBITMQ_HOST=rabbitmq` 제거** → `.env`의 `RABBITMQ_HOST`가
  적용된다. 더불어 이제 아무도 안 쓰는 **`--network algoj_default` 도 제거**(nginx는 published 포트, 봇은
  host loopback으로 API에 접근 → 공유 도커 네트워크 불필요).
- **`deploy/docker-compose.prod.yml` 삭제** + **`deploy/docker-compose.jj.yml` 추가**(RabbitMQ 정의 이전).
- **`.github/workflows/cd.yml`**: OJ에서 더 이상 compose 서비스를 안 띄우므로 `docker-compose.prod.yml`
  scp·`docker compose up` 스텝 제거.

### 박스 `.env` 갱신 (1회, OJ)
```
RABBITMQ_HOST=<JJ-사설IP>      # 피어링 사용 시 사설 IP
RABBITMQ_PORT=5672
# RABBITMQ_USER/RABBITMQ_PASSWORD 는 JJ 브로커에 설정한 값과 동일하게
```

### 정리 후
- OJ의 `algoj-rabbitmq` 컨테이너·`rabbitmq-data` 볼륨 제거 → 메모리·디스크 확보.
  (JJ 브로커로 전환·검증이 끝난 뒤에 내린다.)

---

## 공통 원칙 · 순서

1. **새 위치에 세우고 → 검증 → `.env` 주소 갱신 → PR(deploy-api.sh/compose) 머지 → 구 컴포넌트 제거**.
   순서를 지켜야 다운타임·데이터 손실이 없다.
2. **분리해도 남는 안전망**: 스왑·힙 캡·SerialGC·무겹침(`NO_OVERLAP`)은 그대로 둬도 무방.
   MySQL·Judge0를 둘 다 빼면 박스가 크게 여유로워져(nginx+API+RabbitMQ+봇 ≈ 수백 MB) 부팅이 빨라지고,
   그때는 `NO_OVERLAP`을 빼 무중단 겹침 배포로 복귀할 수도 있다(`deploy/README.md`의 RAM 증설 절 참고).
3. **비용/복잡도**: 박스가 늘면 관리 지점·요금도 는다. "메모리·배포 다운타임"만 목적이면 Lightsail 4GB 증설이
   더 단순·저렴하고, RDS/Judge0 분리는 **백업·격리·독립 스케일**이 필요할 때 값어치가 있다.

## 분리를 권장하지 않는 것

- **관리형 RabbitMQ(Amazon MQ) / SQS 전환**: 브로커를 JJ의 자체 컨테이너로 옮기는 건 값싸지만, 관리형
  Amazon MQ는 비싸고 SQS로 바꾸면 AMQP→다른 API라 코드 변경이 크다 → 굳이 관리형으로 갈 이유는 약함.
  (자체 호스팅 RabbitMQ를 JJ로 모으는 것과는 별개 — 그건 위 C처럼 권장.)
- **nginx / Spring API / Discord 봇**: 앞단 붙박이(nginx는 블루-그린 스위치·LB 겸함)거나 6MB짜리라 분리 이득이 없다.
