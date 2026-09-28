# DB 백업

AWS 시절에는 RDS가 자동 백업을 해 줬다. 지금 MySQL은 박스 안 컨테이너(`algoj-mysql`)라
**아무것도 하지 않으면 백업이 없다.** 박스 디스크가 망가지거나 인스턴스가 회수되면
문제·제출·계정이 그대로 사라진다.

`deploy/backup-db.sh`가 매일 덤프를 떠서 박스에 7개를 보관하고, 설정돼 있으면 박스 밖
(Google Drive, 또는 S3 호환 스토리지)으로도 올린다. Google Drive로 올릴 때는 박스 디스크에
있는 **지문 이미지**(`/opt/algoj/images`)도 `<원격>/images`로 같이 복사한다 — 덤프에는 이미지
URL만 들어 있다.

## PR로 자동 반영되는 것 / 박스에서 할 일

| 자동 (저장소) | 수동 (박스·콘솔) |
|---|---|
| 백업 스크립트 `deploy/backup-db.sh` | `repo` 갱신(`git pull`) 후 수동 1회 실행으로 검증 |
| 업로드 설정 양식 `deploy/backup.env.example` | cron 등록 |
| 복구·검증 절차 (이 문서) | (권장) Google OAuth 클라이언트 생성, Google Drive 연결(`rclone.conf`)·`/opt/algoj/backup.env` 작성 |

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

## 2. 박스 밖으로 올리기 (권장) — Google Drive

개인 Google Drive에 [rclone](https://rclone.org/drive/)으로 올린다. 박스에 rclone을 깔지 않고
공식 `rclone/rclone` 이미지로 돌린다. 원격에는 30일치를 두고(`BACKUP_REMOTE_KEEP_DAYS`),
그보다 오래된 백업은 스크립트가 휴지통을 거치지 않고 바로 지운다 — 휴지통에 두면 용량을 계속
차지한다. 백업 하나가 수백 MB라 30일이면 수 GB다.

> 학교·회사 계정의 드라이브는 쓰지 않는다. 졸업·퇴사하면 백업이 계정과 함께 사라지고,
> 덤프에 들어 있는 유저 정보(이메일·비밀번호 해시)를 기관 저장소에 두게 된다.

### 2-1. Google OAuth 클라이언트 만들기 (1회)

지금 rclone은 Google Drive에 **본인의 OAuth 클라이언트 ID가 필수**다(`client_id` 칸을 비우면
"This value is required"로 막힌다). 무료이고 결제 계정도 필요 없다. 백업을 둘 개인 Google
계정으로 [Google Cloud 콘솔](https://console.cloud.google.com)에서 한다.

1. **프로젝트**를 하나 고른다. 새로 만들어도 되고, 안 쓰는 기존 프로젝트를 써도 된다.
   다른 앱이 쓰는 프로젝트는 피한다 — 아래 3번에서 게시 상태를 바꾸면 그 앱에도 적용된다.
2. **API 및 서비스 → 라이브러리**에서 `Google Drive API`를 **사용**으로 켠다.
3. **OAuth 동의 화면**(Google 인증 플랫폼)을 구성한다.
   - 처음이면 **시작하기** → 앱 이름·지원 이메일 → 대상 **외부** → 연락처 이메일 → 만들기.
   - **브랜딩**에 홈페이지와 개인정보처리방침 링크, 승인된 도메인을 넣는다. OJ에는 그런
     페이지가 없으므로 공개 저장소 주소를 쓴다: 둘 다 `https://github.com/sjh1108/oj`,
     승인된 도메인 `github.com`. 앱 로고는 올리지 않는다(올리면 Google 검토가 필요해진다).
   - **대상** → **앱 게시**로 게시 상태를 **프로덕션**으로 바꾼다. 브랜딩이 비어 있으면 이
     버튼이 비활성이다.

   > ⚠️ **테스트** 상태로 두면 안 된다. 테스트 사용자가 아니면 로그인이
   > `403 access_denied`로 막히고, 테스트 사용자로 넣어도 **토큰이 7일마다 만료**돼 매일
   > 백업이 일주일 뒤부터 실패한다. 인증(verification)을 받으라는 안내는 무시해도 된다 —
   > 본인 계정만 쓰는 앱이다.

4. **클라이언트**(사용자 인증 정보) → **OAuth 클라이언트 ID 만들기** → 유형 **데스크톱 앱** →
   만들기. 나오는 **클라이언트 ID**와 **보안 비밀번호**를 복사해 둔다.

### 2-2. Google 계정 연결 (1회)

박스에는 브라우저가 없어서 **로그인만 PC에서** 한다. PC에도 rclone이 필요하다
(윈도: `winget install Rclone.Rclone`, 설치 후 새 터미널을 연다).

박스에서 설정 마법사를 연다(명령을 손으로 옮기면 경로 오타가 나기 쉬우니 복사해 붙인다):

```bash
mkdir -p /opt/algoj/rclone && chmod 700 /opt/algoj/rclone
docker run --rm -it --user "$(id -u):$(id -g)" \
  -v /opt/algoj/rclone:/config/rclone \
  rclone/rclone --config /config/rclone/rclone.conf config
```

질문에는 이렇게 답한다 (나머지는 Enter로 기본값):

| 질문 | 답 |
|---|---|
| New remote | `n` |
| name | `gdrive` |
| Storage | `drive` (Google Drive) |
| client_id / client_secret | 2-1에서 만든 값 |
| scope | **`drive.file`** — rclone이 만든 파일만 볼 수 있다. 드라이브의 다른 파일에는 손대지 못한다 |
| service_account_file | 비워 둔다 |
| Edit advanced config | `n` |
| Use web browser to automatically authenticate | **`n`** — 기본값이 `y`라 Enter를 치면 안 된다. 박스에는 브라우저가 없다 |

`y`로 넘어가 `Waiting for code...`에서 멈췄다면 `Ctrl+C`로 끊고 마법사를 다시 열어, 남은
`gdrive`를 `d`로 지운 뒤 처음부터 한다.

`n`을 고르면 마법사가 `rclone authorize "drive" "eyJ..."` 형태의 명령을 보여 준다. 화면에서는
여러 줄로 보여도 한 줄이다 — 마지막 `"`까지 복사해 **PC PowerShell**에 붙여 넣는다.
브라우저가 열리면 백업을 둘 Google 계정으로 로그인한다. "Google에서 확인하지 않은 앱"
경고가 나오면 **고급 → (앱 이름)(으)로 이동**을 눌러 허용한다. PowerShell에
`Paste the following into your remote machine --->`와 `<---End paste` 사이에 찍힌 토큰을
박스 마법사의 `config_token>`에 붙여 넣는다. 이어지는 질문(Shared Drive 등)은 `n`,
마지막에 `y`로 저장하고 `q`로 나온다.

연결 확인:

```bash
docker run --rm --user "$(id -u):$(id -g)" -v /opt/algoj/rclone:/config/rclone \
  rclone/rclone --config /config/rclone/rclone.conf lsd gdrive:
```

에러 없이 끝나면 된다 (처음엔 아무것도 안 나오는 게 정상 — `drive.file`이라 rclone이 만든
폴더만 보인다).

### 2-3. 백업 설정

```bash
cp /opt/algoj/repo/deploy/backup.env.example /opt/algoj/backup.env
chmod 600 /opt/algoj/backup.env
bash /opt/algoj/repo/deploy/backup-db.sh   # "업로드: gdrive:algoj-backups/..." 후 "완료"
```

양식의 기본값(`BACKUP_RCLONE_REMOTE=gdrive:algoj-backups`)이 위 설정과 맞으므로 고칠 게 없다.
Google Drive 웹에서 `algoj-backups` 폴더에 파일이 생겼는지 본다. 이후로는 cron이 매일 올린다.

> 설정은 `.env`가 아니라 `backup.env`와 `rclone/rclone.conf`에 둔다. `.env`는
> `deploy-api.sh`가 API 컨테이너에 통째로 넣으므로, 거기 두면 API도 백업용 로그인 정보를 보게 된다.
> `rclone.conf`에는 Google 로그인 토큰이 들어 있으니 다른 곳에 복사하지 않는다.

업로드가 실패해도 로컬 백업은 남고, 스크립트는 실패로 끝나 `backup.log`에 이유가 찍힌다.
토큰은 rclone이 매일 쓰면서 자동으로 갱신한다. Google 계정 비밀번호를 바꾸거나 앱 접근 권한을
해제했다면 2-2를 다시 한다.

### 다른 저장소를 쓰고 싶을 때

rclone이 지원하는 곳(OneDrive 등)은 2-2에서 Storage만 바꾸고 `BACKUP_RCLONE_REMOTE`를 그 이름에
맞추면 된다. S3 호환 스토리지(AWS S3·OCI Object Storage·Cloudflare R2)는 `backup.env`의
`BACKUP_S3_*`를 채우면 공식 `amazon/aws-cli` 이미지로 올린다. 이때는 버킷을 비공개로 만들고,
오래된 백업은 버킷의 수명 주기 규칙으로 지운다(스크립트는 S3 쪽을 정리하지 않는다).
둘 다 채우면 둘 다 올린다.

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

박스 밖에 있는 백업을 쓸 때는 먼저 박스로 내려받는다.

```bash
# Google Drive → 박스
docker run --rm --user "$(id -u):$(id -g)" -v /opt/algoj/rclone:/config/rclone \
  -v /opt/algoj/backups:/backups rclone/rclone --config /config/rclone/rclone.conf \
  copy gdrive:algoj-backups/<파일> /backups/
```

박스가 통째로 사라진 경우에는 Google Drive 웹에서 파일을 내려받아 새 박스로 올리면 된다.

지문 이미지는 파일 이름(UUID)이 지문의 URL에 그대로 박혀 있으므로, 폴더 구조를 유지한 채
`/opt/algoj/images`로 되돌리면 된다.

```bash
docker run --rm --user "$(id -u):$(id -g)" -v /opt/algoj/rclone:/config/rclone \
  -v /opt/algoj/images:/images rclone/rclone --config /config/rclone/rclone.conf \
  copy gdrive:algoj-backups/images /images
```
