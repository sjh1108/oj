# 메모리 제한 상한 (MAX_MEMORY_LIMIT)

Judge0는 제출의 `memory_limit`이 자기 상한(`MAX_MEMORY_LIMIT`)을 넘으면
**제출 자체를 거절**한다.

```
422 Unprocessable Entity: {"memory_limit":["must be less than or equal to 512000"]}
```

앱에서는 채점 서버 오류(S002)로 잡혀 해당 제출이 `SYSTEM_ERROR`가 된다.

## 현재 상한 — 512000KB (500MB), **미상향**

스톡 `judge0.conf`는 `MAX_MEMORY_LIMIT=`이 비어 있어 기본값 512000KB(=500MB)가 적용된다.
JJ 박스는 **아직 이 스톡 기본값 상태**다(2026-08 확인).

```bash
# JJ 박스
curl -s http://127.0.0.1:2358/config_info | python3 -m json.tool | grep max_memory
#   "max_memory_limit": 512000     ← 현재
```

PR #37 이후 **422로 죽지는 않는다** — 앱이 상한을 넘는 요청을 잘라서 보내기 때문이다.
대신 백준에서 흔한 **512MB 문제가 조용히 500MB로 채점**된다(지문 표기와 실제 채점이 어긋남).
액면 그대로 채점하려면 아래 *상한을 올릴 때* 절차를 밟는다.

> ⚠️ **앱 설정이 박스보다 크면 다시 422가 난다.** 앱은 자기 설정값
> (`JUDGE0_MAX_MEMORY_LIMIT_KB`, 기본 512000)으로 자를 뿐 박스 상한을 모른다.
> 박스를 올리기 전에 앱에만 524288을 넣으면 그 값이 그대로 Judge0로 나가 거절된다.
> **두 값은 항상 같이 움직여야 하고, 순서는 박스 → 앱이다.**
>
> ```bash
> # OJ·EOJ 박스 — 앱이 클램프에 쓰는 값. 박스 상한보다 크면 안 된다.
> docker exec algoj-api env | grep JUDGE0_MAX_MEMORY_LIMIT_KB
> ```

## 진단

Judge0 박스에서:

```bash
curl -s http://127.0.0.1:2358/config_info | python3 -m json.tool | grep -i memory
# "max_memory_limit": 512000  ← 현재 상한

# 재현 (상한을 넘는 값으로 제출)
curl -s -X POST 'http://127.0.0.1:2358/submissions?wait=true' \
  -H 'Content-Type: application/json' \
  -d '{"language_id":71,"source_code":"print(1)","memory_limit":524288}'
```

서버 로그에는 `Language Load` 직후 `ROLLBACK` + `Completed 422 Unprocessable Entity`로 남는다:

```bash
cd <judge0 설치 폴더>
docker compose logs --tail 300 server | grep -i -B3 -A3 '422\|memory_limit'
```

## PR로 자동 반영되는 것

`Judge0Client`가 요청 직전에 `min(문제 메모리 제한, judge0.max-memory-limit-kb)`으로 **클램프**한다.
상한을 넘는 문제도 422로 죽지 않고 상한값으로 채점되며, 클램프가 일어나면 앱 로그에 `WARN`이 남는다.

| 항목 | 값 |
|---|---|
| 설정 키 | `judge0.max-memory-limit-kb` |
| 환경변수 | `JUDGE0_MAX_MEMORY_LIMIT_KB` |
| 기본값 | `512000` (Judge0 스톡 기본값과 동일) |

## 상한을 올릴 때 (수동)

**박스 상한과 앱 설정을 같이** 올려야 한다. 앱 설정만 올리면 다시 422가 나고,
박스만 올리면 앱이 계속 예전 값으로 클램프한다. **순서는 박스 → 앱.**

```bash
# 1) JJ 박스
cd <judge0 설치 폴더>
vim judge0.conf                                  # MAX_MEMORY_LIMIT=524288
docker compose up -d --force-recreate server workers
curl -s http://127.0.0.1:2358/config_info | python3 -m json.tool | grep max_memory
#   반드시 새 값이 찍히는 것까지 확인하고 다음으로 넘어간다
```

> ⚠️ **`--force-recreate`가 없으면 반영되지 않는다.** compose는 `judge0.conf` 같은
> `env_file`의 **내용 변경을 감지하지 못해** 기존 컨테이너를 그대로 둔다. 값을 고치고
> `up -d`만 치면 아무 일도 안 일어나고 `config_info`는 예전 값을 계속 보여준다 —
> "올렸는데 안 올라간" 경우 대부분 이것이다.

```bash
# 2) OJ·EOJ 두 박스 — 위에서 확인된 값과 같게
vim /opt/algoj/.env                              # JUDGE0_MAX_MEMORY_LIMIT_KB=524288
# 반영은 롤링 배포 때(OJ에서 bash rolling-deploy.sh) 또는 컨테이너 재기동 시
docker exec algoj-api env | grep JUDGE0_MAX_MEMORY_LIMIT_KB
```

한 박스만 고치면 어느 박스가 제출을 받느냐에 따라 채점 메모리가 달라지므로 **둘 다** 고친다.

> **박스 메모리를 먼저 볼 것.** JJ 박스는 ≈2GB에 Judge0 + RabbitMQ가 이미 1.2GB 남짓을 쓴다.
> 현재의 512MB(524288)까지는 여유가 있지만 **1GB(1048576)는 권장하지 않는다** —
> 제출 하나가 박스 전체를 스왑으로 밀어버린다. 1GB 문제가 필요해지면 박스 증설이 먼저다.

## 참고

- 문제 등록은 여전히 최대 1024MB까지 허용한다(`CreateProblemRequest`). 상한을 넘는 값은
  지문에 표시된 대로 저장되되 채점만 상한으로 내려간다 — 표기와 실제가 어긋나므로,
  상한을 넘는 문제는 출제 시 박스 상한 이하로 잡는 것이 좋다.
- 생성기/모범답안 실행은 `judge0.generate.memory-limit-kb`(기본 512000)를 쓰므로
  상한과 같아 문제되지 않는다.
