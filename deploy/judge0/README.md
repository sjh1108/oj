# Judge0에 PyPy 3 추가하기

스톡 Judge0 CE(≤1.13.1)에는 PyPy가 없다 (Python은 CPython 2.7/3.8뿐).
PyPy 3를 채점 언어로 쓰려면 **Judge0 박스에서 한 번의 수동 작업**이 필요하다.
앱 쪽(백엔드 enum `PYPY3(200)`, `V4` 마이그레이션, 프론트 언어 선택지)은
PR 머지 → CI/CD 배포로 자동 반영된다.

> **순서 중요**: 아래 박스 작업을 먼저 끝낸 뒤 앱을 배포하는 것을 권장.
> 반대로 하면 박스 작업이 끝날 때까지 PyPy 3 제출이 채점 서버 오류(S002)로 실패한다
> (죽지는 않고, 해당 제출만 SYSTEM_ERROR 처리).

## 박스에서 할 일 (수동, 1회)

### 1. 포터블 PyPy 내려받기 (호스트)

```bash
sudo mkdir -p /opt/judge0-extra
cd /opt/judge0-extra
# 최신 버전은 https://downloads.python.org/pypy/ 에서 확인
curl -LO https://downloads.python.org/pypy/pypy3.10-v7.3.17-linux64.tar.bz2
tar xf pypy3.10-v7.3.17-linux64.tar.bz2
mv pypy3.10-v7.3.17-linux64 pypy3
# 호스트에서 동작 확인 (glibc 배포판이면 그대로 돎)
./pypy3/bin/pypy3 --version
```

### 2. Judge0 컨테이너에 볼륨 마운트

Judge0의 `docker-compose.yml`(Judge0 설치 폴더, 이 저장소 아님)에서
**server와 workers 두 서비스 모두**에 볼륨을 추가한다.
`/usr/local` 하위여야 isolate 샌드박스 안에서 보인다.

```yaml
services:
  server:
    volumes:
      - /opt/judge0-extra/pypy3:/usr/local/pypy3:ro   # 추가
      # ...기존 볼륨 유지...
  workers:
    volumes:
      - /opt/judge0-extra/pypy3:/usr/local/pypy3:ro   # 추가
      # ...기존 볼륨 유지...
```

```bash
cd <judge0 설치 폴더>
docker compose up -d          # server/workers 재생성
docker compose exec workers /usr/local/pypy3/bin/pypy3 --version   # 동작 확인
```

### 3. languages 테이블에 PyPy 등록

```bash
# 이 저장소의 deploy/judge0/add-pypy.sql 을 박스로 복사한 뒤
docker compose exec -T db psql -U judge0 -d judge0 < add-pypy.sql
# (POSTGRES_USER/POSTGRES_DB를 judge0.conf에서 바꿨다면 그 값 사용)
```

### 4. 확인

```bash
# 언어 목록에 나오는지
curl -s http://127.0.0.1:2358/languages | grep -i pypy
# 실제 실행되는지 (print(1+1) 제출)
curl -s -X POST 'http://127.0.0.1:2358/submissions?wait=true' \
  -H 'Content-Type: application/json' \
  -d '{"language_id":200,"source_code":"print(1+1)"}'
# → "stdout": "2\n", status Accepted 면 끝
```

## PR 머지로 자동 반영되는 것

| 항목 | 내용 |
|---|---|
| 백엔드 | `Submission.Language`에 `PYPY3(200)` — 채점/실행/생성 API 전부에서 사용 가능 |
| DB | Flyway `V4` — `submissions.language` ENUM에 `PYPY3` 추가 (부팅 시 자동 실행) |
| 프론트 | 문제 페이지·TC 관리 언어 선택지에 "PyPy 3", 에디터 하이라이팅, 다운로드 확장자(.py), 문제 `.md`의 `~~~generator pypy3` 별칭 |

## 참고

- id **200**은 백엔드 enum과 SQL 양쪽에 하드코딩되어 있다. 바꾸려면 둘 다 바꿔야 한다.
- 시간 제한 보너스(백준의 ×3+2s 같은 것)는 없다. PyPy는 보통 보너스 없이 CPython보다
  훨씬 빠르므로, 필요해지면 그때 별도로 논의.
- Judge0를 통째로 업그레이드해도 `languages`의 커스텀 행(200)은 유지된다.
  단, 새 박스로 이전할 때는 1~3을 다시 해야 한다.
