# judge-runner — arm64 채점기 (Judge0 대체)

Judge0 공식 이미지는 **모든 태그가 amd64 단일 아키텍처**라(1.11~1.13 확인) Oracle Ampere(arm64)
박스에서 돌지 않는다. 그런데 앱이 Judge0에서 실제로 쓰는 건 엔드포인트 **두 개**뿐이라
(`Judge0Client` 참고), 그 부분만 그대로 구현해 대체한다.

```
POST /submissions?base64_encoded=true&wait=true
GET  /languages          ← 앱은 헬스체크로만 쓴다 (Judge0Client.isUp)
```

`.env`의 `JUDGE0_URL`만 이 서비스로 돌리면 되고 **앱 코드는 건드리지 않는다.**

## 구조

```
  API 컨테이너 ──HTTP──▶ judge-runner ──docker run──▶ judge-sandbox (제출마다 새로)
   (algoj-net)            채점 판정             격리 실행: 네트워크 없음·권한 없음
                          코드 실행 안 함                 ·읽기전용·메모리/PID 상한
```

- **judge-runner** — 파이썬 표준 라이브러리만 쓰는 HTTP 서비스(`server.py`). 제출 소스와 입력을
  작업 디렉터리에 쓰고, 호스트 docker 데몬에 샌드박스 컨테이너를 띄워달라고 요청한 뒤,
  남은 파일을 읽어 Judge0 상태 코드로 변환한다. **이 프로세스는 제출 코드를 실행하지 않는다.**
- **judge-sandbox** — 툴체인만 담은 이미지(`sandbox/`). 실제 컴파일·실행은 전부 여기서,
  제출 하나당 컨테이너 하나로 일어나고 끝나면 버려진다.

판정은 `server.py`의 `classify()` 한 곳에서만 내린다. 샌드박스는 사실(종료 코드·CPU 시간·
최대 RSS)만 적어두고 해석하지 않는다.

## 언어

`Submission.Language`의 id와 **반드시 같아야 한다** — 하나라도 어긋나면 그 언어 제출이 전부 실패한다.

| id | 언어 | 컴파일/검사 | 실행 |
|---|---|---|---|
| 50 | C | `gcc -O2 -std=gnu17` | `./prog` |
| 54 | C++ | `g++ -O2 -std=gnu++17` | `./prog` |
| 62 | Java | `javac -encoding UTF-8` | `java ... Main` |
| 63 | JavaScript | `node --check` | `node main.js` |
| 71 | Python 3 | `python3 -m py_compile` | `python3 main.py` |
| 200 | PyPy 3 | `pypy3 -m py_compile` | `pypy3 main.py` |

인터프리터 언어도 컴파일 단계에서 문법 검사를 돌린다 — 문법 오류가 런타임 에러가 아니라
**컴파일 에러(6)** 로 잡혀야 앱의 상태 매핑과 맞는다.

> **PyPy는 이제 박스 수동 작업이 필요 없다.** 예전 Judge0에서는 포터블 PyPy를 내려받아
> 볼륨으로 물리고 `languages` 테이블에 직접 INSERT 해야 했다(`deploy/judge0/README.md`).
> 여기서는 샌드박스 이미지에 들어 있다.

## 상태 코드

`Judge0StatusMapper`가 읽는 값이라 계약이다.

| 반환 | 조건 | 앱에서 |
|---|---|---|
| 3 Accepted | 정상 종료 + 출력 일치, 또는 `expected_output`이 없음 | ACCEPTED |
| 4 Wrong Answer | 정상 종료 + 출력 불일치 | WRONG_ANSWER |
| 5 Time Limit | `timeout`/`ulimit -t`에 걸림, 또는 CPU 예산 소진 | TIME_LIMIT |
| 6 Compilation Error | 컴파일·문법 검사 실패 | COMPILE_ERROR |
| 7 SIGSEGV | 세그폴트, **그리고 메모리 초과(OOM kill)** | RUNTIME_ERROR |
| 8 SIGXFSZ | 출력 크기 초과 | RUNTIME_ERROR |
| 11 NZEC | 0이 아닌 종료 코드 | RUNTIME_ERROR |
| 13 Internal Error | 샌드박스가 결과를 못 남김 | SYSTEM_ERROR |

**출력 비교**는 Judge0와 같게 끝쪽 공백을 무시한다 — 줄 끝 공백·마지막 개행·CRLF는 정답을
가르지 않는다. 기존 문제의 정답 데이터가 전부 그 전제로 저장돼 있어서, 여기서 더 엄격하게
비교하면 예전엔 맞던 제출이 틀리게 된다. 줄 사이의 빈 줄과 줄 앞 공백은 유효하다.

**`expected_output`이 없으면 3(Accepted)** — 실행(run) 기능과 테스트케이스 생성기가
이 동작에 기대고 있다(`Judge0Results.ranSuccessfully`).

## 자원 제한

- **시간**: `cpu_time_limit`(초). `ulimit -t`로 CPU를, 벽시계 `timeout`으로 대기까지 막는다.
  응답의 `time`은 **CPU 시간**(user+sys)이다 — Judge0와 같다.
- **메모리**: `memory_limit`(KB)을 컨테이너 cgroup 상한으로 건다. 스왑은 없다(초과하면 기어가는
  대신 죽는다). 초과 시 OOM kill → **상태 7**. 앱에 `MEMORY_LIMIT` 상태가 있긴 하지만
  `Judge0StatusMapper`가 만들어내지 않으므로 Judge0와 동일하게 RUNTIME_ERROR로 보인다.
  64MB 미만 요청은 인터프리터 자체가 못 뜨므로 64MB로 올린다.
- **자바 메모리**: JVM은 힙 밖에도 메타스페이스·코드캐시·스레드 스택이 필요해서,
  `-Xmx`를 `memory_limit - 96MB`로 준다. 자바 제출이 있는 문제는 메모리 제한을 넉넉히 잡는 게 좋다.
- **출력 크기**: `max_file_size`(KB)를 `ulimit -f`로. 초과하면 SIGXFSZ(8).
- **동시 실행**: `JUDGE_MAX_PARALLEL`(기본 2). 박스가 2코어이고 앱 리스너 동시성도 2다.

## 보안 모델

제출 코드는 매번 **새 컨테이너**에서 다음 제약으로 돈다:

- `--network none` — 외부 통신 불가
- `--cap-drop ALL`, `--security-opt no-new-privileges`, `--user 65534`(nobody)
- `--read-only` 루트 파일시스템 + 크기 제한된 tmpfs `/tmp`
- `--memory`/`--memory-swap` 동일값, `--pids-limit`, `--cpus 1`
- 작업 디렉터리만 쓰기 가능, 실행 후 삭제

**알고 쓸 것 두 가지.**

1. **runner 컨테이너는 docker 소켓을 들고 있다** — 사실상 박스 root 권한이다. 그래서
   (a) 이 서비스는 호스트 포트를 열지 않고 `algoj-net` 안에서만 닿으며,
   (b) 제출 코드는 runner 프로세스가 아니라 별도 샌드박스 컨테이너에서만 돈다.
   runner에 외부 트래픽이 닿게 만들면 안 된다.
2. **컨테이너는 호스트 커널을 공유한다** — VM 경계가 아니다. 커널 취약점을 쓰는 탈출은 막지
   못한다. 이건 Judge0의 isolate도 마찬가지이고, 스터디원만 가입하는 저지라는 전제에서
   받아들인 위험이다. 불특정 다수에게 열 계획이 생기면 gVisor(`--runtime=runsc`)나
   전용 VM으로 한 겹 더 둘러야 한다.

## 박스에 올리기

이미지는 `judge-runner/**`가 바뀔 때 CI가 arm64·amd64로 빌드해 GHCR에 올린다
(`.github/workflows/judge-images.yml`).

```bash
cd /opt/algoj
docker compose -f docker-compose.judge.yml --env-file .env pull
docker compose -f docker-compose.judge.yml --env-file .env up -d
docker logs -f algoj-judge-runner        # "sandbox image ready" 를 기다린다
```

**첫 기동은 오래 걸린다.** compose가 받는 건 러너 이미지뿐이고, 툴체인이 든 샌드박스
이미지(~1GB)는 러너가 시작할 때 직접 받는다. 그동안은 HTTP 서비스가 아직 안 뜨므로
컨테이너가 잠시 unhealthy로 보인다(healthcheck의 `start_period`가 그만큼 잡혀 있다).
로그에 `sandbox image ready`가 찍힌 뒤에 검증으로 넘어간다.

`.env`에서 API가 이 서비스를 보게 한다:

```ini
JUDGE0_URL=http://judge-runner:2358
```

바꿨으면 API를 다시 배포한다(`bash deploy-api.sh`). 그동안 밀려 있던 PENDING 제출은
스위퍼가 1분 주기로 재적재하므로 따로 손댈 게 없다.

## 검증

```bash
python3 /opt/algoj/repo/judge-runner/selftest.py
```

6개 언어가 전부 실행되는지, 오답·시간초과·컴파일에러·런타임에러·메모리초과가 각각
기대한 상태로 오는지 끝까지 확인한다. 판정 로직 자체의 단위 테스트는 CI에서 돈다
(`python3 -m unittest discover -s judge-runner`).

실제 문제로 확인하려면 기존 문제의 모범답안을 제출해 ACCEPTED가 나오는지 보면 된다.

## 문제가 생기면

```bash
docker logs algoj-judge-runner --tail 50        # 러너 로그 (docker 오류가 여기 찍힌다)
docker images | grep judge                      # 샌드박스 이미지가 있는지
ls /opt/algoj/judge-work                         # 실행 후엔 비어 있어야 정상
```

- **전부 SYSTEM_ERROR(13)** — 샌드박스 이미지를 못 받았거나 docker 소켓이 안 물렸다.
  러너 로그의 `docker:` 줄을 본다. 이미지가 없으면 `--pull never` 때문에 즉시 실패하는데,
  느린 pull이 제출 타임아웃 안에서 조용히 도는 것보다 낫다고 보고 그렇게 뒀다.
  손으로 받으려면 `docker pull ghcr.io/sjh1108/oj-judge-sandbox:latest`.
- **자바만 실패** — 메모리 제한이 빡빡한 경우가 대부분이다. 문제의 메모리 제한을 올린다.
- **작업 디렉터리에 찌꺼기가 쌓임** — 러너가 비정상 종료한 흔적이다. `judge-work` 아래
  오래된 디렉터리는 지워도 안전하다.
