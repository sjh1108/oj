# 메모리 제한 상한 (MAX_MEMORY_LIMIT)

Judge0는 제출의 `memory_limit`이 자기 상한(`MAX_MEMORY_LIMIT`)을 넘으면
**제출 자체를 거절**한다.

```
422 Unprocessable Entity: {"memory_limit":["must be less than or equal to 512000"]}
```

앱에서는 채점 서버 오류(S002)로 잡혀 해당 제출이 `SYSTEM_ERROR`가 된다.

## 현재 상한 — 524288KB (512MB), 적용 완료

스톡 `judge0.conf`는 `MAX_MEMORY_LIMIT=`이 비어 있어 기본값 512000KB(=500MB)가 적용되고,
이 때문에 백준에서 흔한 512MB 문제가 전부 422로 죽었다(PR #37). **박스 상한과 앱 설정을 함께
524288로 올려 해결된 상태다** — 추가로 할 일은 없다.

확인:

```bash
# JJ 박스 — Judge0 실제 상한
curl -s http://127.0.0.1:2358/config_info | python3 -m json.tool | grep max_memory
#   "max_memory_limit": 524288

# OJ·EOJ 박스 — 앱이 클램프에 쓰는 값 (둘이 다르면 낮은 쪽으로 잘린다)
docker exec algoj-api env | grep JUDGE0_MAX_MEMORY_LIMIT_KB
#   JUDGE0_MAX_MEMORY_LIMIT_KB=524288
```

> 앱 쪽 기본값은 여전히 `512000`(스톡과 동일)이라 `.env`에 값이 없으면 512MB 문제가 조용히
> 500MB로 잘린다 — 422로 죽지는 않지만 표기와 실제가 어긋나므로 두 박스 모두 값이 있어야 한다.

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

## 상한을 더 올릴 때 (수동 — 지금은 필요 없음)

**박스 상한과 앱 설정을 같이** 올려야 한다. 앱 설정만 올리면 다시 422가 나고,
박스만 올리면 앱이 계속 예전 값으로 클램프한다.

```bash
cd <judge0 설치 폴더>
vim judge0.conf        # MAX_MEMORY_LIMIT=<새 값>
docker compose up -d server workers
curl -s http://127.0.0.1:2358/config_info | python3 -m json.tool | grep max_memory
```

그 다음 **OJ·EOJ 두 박스** `/opt/algoj/.env`의 `JUDGE0_MAX_MEMORY_LIMIT_KB`를 같은 값으로
고치고 재배포한다(한 박스만 고치면 어느 박스가 받느냐에 따라 채점 메모리가 달라진다).

> **박스 메모리를 먼저 볼 것.** JJ 박스는 ≈2GB에 Judge0 + RabbitMQ가 이미 1.2GB 남짓을 쓴다.
> 현재의 512MB(524288)까지는 여유가 있지만 **1GB(1048576)는 권장하지 않는다** —
> 제출 하나가 박스 전체를 스왑으로 밀어버린다. 1GB 문제가 필요해지면 박스 증설이 먼저다.

## 참고

- 문제 등록은 여전히 최대 1024MB까지 허용한다(`CreateProblemRequest`). 상한을 넘는 값은
  지문에 표시된 대로 저장되되 채점만 상한으로 내려간다 — 표기와 실제가 어긋나므로,
  상한을 넘는 문제는 출제 시 박스 상한 이하로 잡는 것이 좋다.
- 생성기/모범답안 실행은 `judge0.generate.memory-limit-kb`(기본 512000)를 쓰므로
  상한과 같아 문제되지 않는다.
