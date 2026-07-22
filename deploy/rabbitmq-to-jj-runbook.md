# RabbitMQ → JJ 이전 실행 체크리스트 (박스에서 할 일)

이 PR은 **코드/설정만** 바꾼다(브로커 주소를 `.env`에서 읽도록). 실제 브로커를 옮기는
**수동 작업은 아래 순서대로** 직접 해야 한다. 배경·이유는 `deploy/offload-components.md`의
**C. RabbitMQ → JJ**, 이중화 큰그림은 `deploy/redundancy.md` 참고.

> **핵심 원칙 — 순서 엄수**: CD는 `master` 머지 즉시 새 API를 `.env`의 `RABBITMQ_HOST`로
> 붙인다. 그러므로 **머지 전에** JJ 브로커 기동 + OJ `.env` 갱신이 끝나 있어야 한다.
> (Step 1~3 → 머지 → Step 5 검증 → Step 6 정리)

> **안전망**: 이전 중 구 브로커에 쌓여 있던 큐 메시지가 유실돼도, `PendingSubmissionSweeper`가
> 300초 이상 PENDING인 제출을 자동 재큐잉한다 → 채점 누락 없음. 그래서 `rabbitmq-data`
> 볼륨을 옮길 필요 없이 JJ 브로커를 **빈 상태로 시작**해도 된다.

---

## 준비물 확인
- [ ] JJ(EC2) 박스에 SSH 접속 가능(닉네임 `JJ`), docker/docker compose 동작.
- [ ] OJ `.env`의 `RABBITMQ_USER` / `RABBITMQ_PASSWORD` 값을 알고 있음(JJ에도 **동일하게** 넣는다).
- [ ] OJ↔JJ 사설 통신 경로(VPC 피어링)가 이미 있음(Judge0용으로 쓰던 것 재사용).
- [ ] JJ의 사설 IP를 알고 있음: `ip -4 addr show | grep inet`(JJ에서) 또는 콘솔에서 확인.

---

## Step 1 — JJ에 RabbitMQ 기동

```bash
# (로컬 PC에서) 이 PR의 compose 파일을 JJ로 복사
scp deploy/docker-compose.jj.yml JJ:/opt/algoj/     # 경로는 JJ의 작업 디렉터리에 맞게

# (JJ에서)
cd /opt/algoj
# .env 에 브로커 계정 — OJ .env 값과 반드시 동일하게
#   RABBITMQ_USER=algoj
#   RABBITMQ_PASSWORD=<OJ와 같은 값>
docker compose -f docker-compose.jj.yml --env-file .env up -d
docker logs algoj-rabbitmq --tail 20        # "Server startup complete" 확인
```
- [ ] `algoj-rabbitmq` 컨테이너가 `Up (healthy)`.

## Step 2 — JJ 보안그룹: 5672 개방(제한적으로)

AWS 콘솔 → EC2 → JJ 인스턴스 → 보안그룹 → 인바운드 규칙 추가:
- **유형** Custom TCP, **포트** `5672`, **소스** = **OJ의 사설 IP/32** (이후 EOJ 추가 시 EOJ도).
- [ ] **0.0.0.0/0 로 열지 않았다** (자격증명만으론 부족 — 네트워크로 잠근다).

빠른 확인(OJ에서):
```bash
nc -zv <JJ-사설IP> 5672        # succeeded 나오면 통신 OK
```
- [ ] OJ에서 JJ:5672 로 TCP 연결 성공.

## Step 3 — OJ `.env` 갱신

```bash
# (OJ에서)
cd /opt/algoj
# 아래 값으로 수정/추가:
#   RABBITMQ_HOST=<JJ-사설IP>
#   RABBITMQ_PORT=5672
#   RABBITMQ_USER / RABBITMQ_PASSWORD  ← JJ 브로커와 동일
grep '^RABBITMQ_' .env        # 확인
```
- [ ] OJ `.env`의 `RABBITMQ_HOST`가 JJ 사설 IP를 가리킨다.

> 아직 OJ의 기존 `algoj-rabbitmq`는 **끄지 않는다**(롤백 여지). 새 API가 JJ 브로커로
> 정상 붙는 걸 확인한 뒤 Step 6에서 내린다.

## Step 4 — PR 머지 (CD 자동 배포)

- [ ] 이 PR을 `master`로 머지 → CD가 새 API 이미지를 배포(`deploy-api.sh`, 무겹침).
- [ ] Actions의 CD 잡이 초록불.

> CD는 이제 OJ에서 compose up 을 하지 않는다(prod.yml 삭제됨). 새 API는 `.env`의
> `RABBITMQ_HOST`(=JJ)로 붙는다.

## Step 5 — 검증

```bash
# (OJ에서) API가 JJ 브로커에 컨슈머로 붙었는지 — JJ에서 확인
# (JJ에서)
docker exec algoj-rabbitmq rabbitmqctl list_queues name messages consumers
#   judge.queue 의 consumers >= 1 이면 API가 정상 연결됨
```
- [ ] `judge.queue`에 consumer가 붙어 있다.
- [ ] 사이트에서 **실제 제출 1건** → 채점이 끝까지 진행(ACCEPTED/틀림 판정)된다.
- [ ] API 로그에 AMQP 연결 에러 없음: `docker logs algoj-api-blue --tail 50`
      (또는 `-green`, 현재 활성 색).

## Step 6 — 구 브로커 정리 (검증 통과 후에만)

```bash
# (OJ에서) 기존 로컬 RabbitMQ 내리고 데이터 볼륨 제거 → 메모리·디스크 확보
docker rm -f algoj-rabbitmq
docker volume ls | grep rabbitmq          # 남은 볼륨 확인
sudo rm -rf /opt/algoj/rabbitmq-data      # 바인드 볼륨이면 디렉터리 제거
docker ps                                 # 이제 OJ엔 nginx(host)·api·bot 만
free -h                                   # 여유 메모리 소폭 증가 확인
```
- [ ] OJ에 `algoj-rabbitmq`가 더 이상 없다.

---

## 롤백 (Step 5에서 문제 발생 시)

새 API가 JJ 브로커에 못 붙거나 채점이 안 되면:
1. OJ `.env`의 `RABBITMQ_HOST`를 다시 로컬(`localhost` 또는 구 설정)로 되돌린다.
2. 구 `algoj-rabbitmq`가 아직 살아있으므로(Step 6 전) API만 재배포하면 원상복귀:
   ```bash
   cd /opt/algoj && IMAGE=ghcr.io/sjh1108/oj-api:latest bash deploy-api.sh
   ```
3. JJ 브로커/보안그룹 문제를 고친 뒤 다시 Step 3부터.

> 구 브로커를 **Step 6에서 내리기 전까지**는 언제든 이 경로로 되돌릴 수 있다.

---

## 완료 후 상태

| 컴포넌트 | 위치 |
|---|---|
| nginx (TLS·LB) | OJ |
| API (blue-green) | OJ |
| Discord 봇 | OJ |
| **RabbitMQ** | **JJ** ← 이번 이전 |
| Judge0 | JJ |
| MySQL | RDS |

다음 단계(선택): API 이중화(EOJ 추가) — `deploy/redundancy.md`.
