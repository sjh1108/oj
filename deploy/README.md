# Deployment — Lightsail (Ubuntu 22.04)

같은 박스에 이미 Judge0가 떠 있다는 전제. 이 가이드는 백엔드(Spring Boot) + MySQL을 추가로 올린다.

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
