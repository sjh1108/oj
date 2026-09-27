# DB 백업

AWS 시절에는 RDS가 자동 백업을 해 줬다. 지금 MySQL은 박스 안 컨테이너(`algoj-mysql`)라
**아무것도 하지 않으면 백업이 없다.** 박스 디스크가 망가지거나 인스턴스가 회수되면
문제·제출·계정이 그대로 사라진다.

`deploy/backup-db.sh`가 매일 덤프를 떠서 박스에 7개를 보관하고, 설정돼 있으면 박스 밖
S3 호환 스토리지로도 올린다.

## PR로 자동 반영되는 것 / 박스에서 할 일

| 자동 (저장소) | 수동 (박스·콘솔) |
|---|---|
| 백업 스크립트 `deploy/backup-db.sh` | `repo` 갱신(`git pull`) 후 수동 1회 실행으로 검증 |
| 업로드 설정 양식 `deploy/backup.env.example` | cron 등록 |
| 복구·검증 절차 (이 문서) | (권장) 박스 밖 스토리지 버킷·키 생성, `/opt/algoj/backup.env` 작성 |

CD는 `deploy-api.sh`만 박스로 복사한다. 백업 스크립트는 저장소 클론(`/opt/algoj/repo`)에서
바로 실행하므로, 스크립트가 바뀌면 박스에서 `git pull`만 하면 된다.

## 1. 로컬 백업 켜기

```bash
cd /opt/algoj/repo && git pull
bash /opt/algoj/repo/deploy/backup-db.sh
ls -lh /opt/algoj/backups/
```

`완료`로 끝나고 `backups/`에 `algoj-<날짜>T<시각>.sql.gz`가 생기면 된다. 지금 데이터로
수백 MB가 나온다. 1MB보다 작거나, 덤프가 끝까지 써지지 않았으면 스크립트가 실패로 끝나고
반쪽짜리 파일을 남기지 않는다.

cron에 등록한다 (`ubuntu` 사용자로, docker 그룹이어야 한다):

```bash
timedatectl | grep 'Time zone'     # 박스 시간대 확인
crontab -e
```

```cron
# 매일 새벽 4시 17분(KST) — 박스가 UTC면 17 19 * * * 로 쓴다
17 4 * * * /opt/algoj/repo/deploy/backup-db.sh >> /opt/algoj/backup.log 2>&1
```

다음 날 `tail /opt/algoj/backup.log`로 돌았는지 확인한다.

> 로컬 백업은 실수로 지운 데이터나 깨진 마이그레이션은 되돌려 주지만, **박스가 통째로
> 사라지면 같이 사라진다.** 2번까지 해 두는 게 목표다.

## 2. 박스 밖으로 올리기 (권장)

S3 API를 지원하는 스토리지면 어디든 된다. 스크립트는 박스에 aws CLI를 깔지 않고
공식 `amazon/aws-cli` 이미지로 한 번 돌려 올린다.

| 후보 | 비용 | 비고 |
|---|---|---|
| OCI Object Storage | Always Free 구간 안 | 같은 Oracle 계정이라 따로 가입할 게 없다. 대신 계정이 막히면 박스와 백업이 같이 막힌다 |
| Cloudflare R2 | 무료 구간 있음 | 다른 회사라 계정 문제로부터도 분리된다 |
| AWS S3 | 소액 과금 | 방금 정리한 계정을 다시 쓰게 된다 |

무료 한도는 바뀔 수 있으니 콘솔에서 확인한다. 백업 하나가 수백 MB라, 30일 보관이면 수 GB다.

공통 절차:

1. **비공개 버킷**을 만든다(예: `algoj-backups`). 공개 읽기를 켜지 않는다 — 덤프에는
   계정 정보(비밀번호 해시·이메일)가 들어 있다.
2. S3 호환 **액세스 키**를 만든다.
   - OCI: 사용자 설정 → **Customer secret keys**에서 만든다. 엔드포인트는
     `https://<네임스페이스>.compat.objectstorage.<리전>.oraclecloud.com`이고,
     네임스페이스는 Object Storage 버킷 상세 화면에 나온다.
   - R2: **R2 API 토큰**을 해당 버킷의 쓰기 권한으로 만든다.
3. 버킷에 **수명 주기 규칙**을 걸어 `db/` 아래 객체를 30일 뒤 지우게 한다.
   스크립트는 박스의 로컬 백업만 정리하고 원격은 건드리지 않는다.
4. 박스에 설정 파일을 만든다.

```bash
cp /opt/algoj/repo/deploy/backup.env.example /opt/algoj/backup.env
chmod 600 /opt/algoj/backup.env
nano /opt/algoj/backup.env          # 버킷·엔드포인트·리전·키 채우기
bash /opt/algoj/repo/deploy/backup-db.sh   # 마지막에 "업로드: s3://..." 후 "완료"
```

콘솔에서 버킷에 파일이 올라왔는지 본다. 이후로는 cron이 매일 같이 올린다.

> 설정은 `.env`가 아니라 `backup.env`에 둔다. `.env`는 `deploy-api.sh`가 API 컨테이너에
> 통째로 넣으므로, 거기 두면 API도 백업용 키를 보게 된다.

업로드가 실패해도 로컬 백업은 남고, 스크립트는 실패로 끝나 `backup.log`에 이유가 찍힌다.

## 3. 복구

**먼저 지금 상태를 한 번 더 백업한다.** 복구가 잘못돼도 되돌아올 곳이 있어야 한다.

```bash
bash /opt/algoj/repo/deploy/backup-db.sh
```

### 백업이 멀쩡한지만 확인 (운영 DB는 건드리지 않음)

임시 DB에 올려 운영 DB와 건수를 비교한다. 이전 때 쓴 방법과 같다.

```bash
cd /opt/algoj
export MYSQL_PWD="$(grep -m1 '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
q() { docker exec -i -e MYSQL_PWD algoj-mysql mysql -uroot -N "$@"; }
B=/opt/algoj/backups/algoj-<날짜>T<시각>.sql.gz

q -e "DROP DATABASE IF EXISTS algoj_check; CREATE DATABASE algoj_check"
zcat "$B" | q algoj_check && echo "import 끝"
for t in users problems submissions test_cases; do
  echo "$t  백업=$(q algoj_check -e "SELECT COUNT(*) FROM $t")  운영=$(q algoj -e "SELECT COUNT(*) FROM $t")"
done
q -e "DROP DATABASE algoj_check"
```

### 실제로 되돌리기

덤프는 테이블마다 `DROP TABLE IF EXISTS` → 생성 → 데이터 순서라, 운영 DB에 그대로 부으면
백업 시점으로 돌아간다. **백업 이후에 생긴 데이터는 사라진다.**

```bash
zcat "$B" | q algoj && echo "복구 끝"
curl -s http://127.0.0.1:8080/api/health
```

복구 중에 들어온 요청은 실패할 수 있다. 사용자가 적은 시간에 한다. 스키마는 Flyway가
관리하므로, 백업이 코드보다 옛 버전이면 API를 재시작할 때 부족한 마이그레이션이 이어서
적용된다(`docker restart algoj-api-blue` 또는 `-green` — 활성 색은
`/etc/nginx/conf.d/algoj-upstream.conf`의 포트로 확인).

박스 밖에 있는 백업을 쓸 때는 먼저 박스로 내려받는다(OCI·R2 콘솔에서 다운로드하거나
`amazon/aws-cli`로 `s3 cp s3://<버킷>/db/<파일> /opt/algoj/backups/`).
