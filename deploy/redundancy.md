# API 이중화(redundancy) + 교차 롤링 무중단 배포 설계

OJ(Lightsail) 한 박스에서 blue-green(8081/8082)으로 컨테이너 2개를 겹쳐 띄우는
방식은 2GB 박스에서 JVM 2개가 스왑을 갈아 무중단이 사실상 불가능했다. 그래서
**박스당 JVM 1개**를 유지하면서 무중단을 얻기 위해, API 서버를 **OJ + EOJ 두
박스**로 이중화하고 배포를 두 박스에 **번갈아(롤링)** 굴린다.

- 한쪽을 배포하는 동안 다른 쪽이 100% 트래픽을 받으므로 사용자 체감 다운타임 0.
- 박스 하나가 죽어도 다른 박스가 서빙 → **장애 이중화**까지 덤으로 얻는다.
- 각 박스는 항상 JVM 1개만 → 스왑 쓰레싱 없음(현재 no-overlap 배포와 동일한
  메모리 프로파일).

> 전제: **RabbitMQ→JJ 이전이 선행되어야 한다.** EOJ의 API는 OJ의 도커 네트워크
> 안 `rabbitmq`에 닿을 수 없으므로, 두 API 박스가 공통으로 바라볼 브로커가
> JJ(EC2)에 있어야 한다. (`deploy/offload-components.md`의 RabbitMQ 항목 참고)

---

## 1. 목표 아키텍처

```
                         Vercel (Next.js 프론트)
                                │ HTTPS (공개 도메인)
                                ▼
                ┌───────────────────────────────┐
                │ OJ (Lightsail 2GB)             │
                │   nginx  ── TLS + LB (항상 살아있음)
                │   API #1  127.0.0.1:8080       │
                └───────────────────────────────┘
                    │ upstream algoj_api {
                    │     server 127.0.0.1:8080;      # OJ  (local)
                    │     server <EOJ_PRIV_IP>:8080;  # EOJ (VPC peering)
                    │ }
                    │ VPC peering (Lightsail ↔ 기본 VPC)
                    ▼
                ┌───────────────────────────────┐
                │ EOJ (EC2 t3.small 2GB)         │
                │   API #2  :8080                │
                └───────────────────────────────┘

  두 API 박스가 공통으로 바라보는 백엔드 (모두 무상태 공유):
    • RDS(MySQL)      ← DB_HOST
    • JJ: RabbitMQ    ← RABBITMQ_HOST   (경쟁 소비 = 작업 분산)
    • JJ: Judge0      ← JUDGE0_URL
```

- **LB = OJ의 nginx.** 배포 중에도 nginx는 절대 내리지 않는다(재시작되는 건 API
  컨테이너뿐). upstream에 두 백엔드를 등록해 트래픽을 분산한다.
- 프론트/DNS/TLS 변경 없음. 공개 도메인은 그대로 OJ nginx를 가리킨다.

### 왜 이 코드가 이중화에 안전한가 (active-active 검증 완료)

| 항목 | 상태 | 근거 |
|---|---|---|
| 세션 | ✅ 무상태 | JWT 기반 → 스티키 세션 불필요, 어느 박스든 아무 요청 처리 가능 |
| 채점 큐 소비 | ✅ 안전 | `@RabbitListener`는 **경쟁 소비**: 한 메시지는 한 박스만 받음 → 이중 채점 없음, 오히려 부하 분산 |
| 스케줄러 | ⚠️ 멱등 | `PendingSubmissionSweeper`(@Scheduled)가 두 박스에서 동시에 돌 수 있음. javadoc대로 재큐잉은 멱등(같은 verdict) → **무해**. 다만 낭비를 없애려면 한 박스만 켜는 걸 권장(아래 §4) |
| 캐시/상태 | ✅ 없음 | 요청 간 공유 인메모리 상태 없음 |

---

## 2. 트래픽 모델 — active-active + 배포 시 결정적 드레이닝

nginx upstream에 **두 서버를 상시 등록**하고 평상시 `least_conn`으로 분산한다.
박스 장애는 `max_fails`/`fail_timeout`으로 자동 축출(passive health check).

```nginx
# /etc/nginx/conf.d/algoj-upstream.conf  (render-upstream.sh가 재작성)
upstream algoj_api {
    least_conn;
    server 127.0.0.1:8080        max_fails=2 fail_timeout=10s;   # OJ
    server <EOJ_PRIV_IP>:8080    max_fails=2 fail_timeout=10s;   # EOJ
}
```

**배포는 passive health check에 의존하지 않고 결정적으로 드레이닝한다.** 배포할
박스를 upstream에서 `down`으로 명시 → reload → 그 박스로는 트래픽 0이 된 상태에서
안전하게 교체한다. 예) EOJ 배포 중:

```nginx
    server <EOJ_PRIV_IP>:8080    down;   # EOJ (배포 중 — 트래픽 차단)
```

내부 고정 진입점(`algoj-internal.conf`, 127.0.0.1:8080 → Discord 봇 등)은 그대로
`algoj_api` upstream을 프록시하므로 자동으로 살아있는 박스로 라우팅된다.

---

## 3. 배포 오케스트레이션 (교차 롤링)

박스당 배포는 단순 **no-overlap**(구 컨테이너 stop → 신 컨테이너 run → 로컬
헬스체크). 무중단은 "다른 박스가 받는 동안 이 박스를 교체"하는 **바깥 롤링 계층**이
책임진다. CD가 지휘자(conductor)가 되어 순서대로 SSH를 친다:

```
1) OJ  : render-upstream.sh  eoj-down   → nginx reload   # EOJ 드레이닝(OJ가 100%)
2) EOJ : deploy-api-single.sh           → 로컬 /api/health OK
3) OJ  : render-upstream.sh  none       → nginx reload   # EOJ 복귀
4) OJ  : render-upstream.sh  oj-down    → nginx reload   # OJ 드레이닝(EOJ가 100%)
5) OJ  : deploy-api-single.sh           → 로컬 /api/health OK
6) OJ  : render-upstream.sh  none       → nginx reload   # OJ 복귀
```

- 어느 순간에도 **살아있는 박스 최소 1개**가 100% 트래픽을 받는다 → 무중단.
- 실패 시 롤백: 2)나 5)에서 헬스체크 실패하면 그 단계에서 멈추고(non-zero exit),
  방금 배포한 박스는 여전히 `down`이므로 다른 박스가 계속 서빙. 이전 이미지로
  복구 후 재시도. **한 박스 실패가 서비스 중단으로 이어지지 않는다.**
- CD가 두 박스에 SSH하려면 EOJ용 접속 시크릿이 추가로 필요(§5). OJ→EOJ SSH 키를
  두는 대신 CD를 지휘자로 두어 키 관리 지점을 늘리지 않는다.

### PR로 자동 반영되는 파일 (구현 단계)

- `deploy/deploy-api-single.sh` (신규) — 박스당 no-overlap 배포. 고정 포트 8080,
  `--env-file .env`, `/api/health` 로컬 헬스체크. (현 blue-green 스크립트에서 포트
  플립/nginx 조작을 제거한 축약판. RabbitMQ 하드코딩 `-e RABBITMQ_HOST=rabbitmq`
  삭제 — .env의 `RABBITMQ_HOST`=JJ 사용.)
- `deploy/nginx/render-upstream.sh` (신규) — 인자 `none|oj-down|eoj-down`을 받아
  upstream conf를 재생성 + `nginx -t` + `nginx -s reload`. EOJ 주소는 env로 주입.
- `deploy/nginx/algoj-upstream.conf` — 두 서버 등록 형태로 변경(위 §2).
- `.github/workflows/cd.yml` — deploy 잡을 위 6단계 롤링으로 교체. `SSH_HOST_EOJ`
  등 EOJ 시크릿 사용. 공지(디스코드) 스텝은 그대로 마지막에 1회.

---

## 4. 스케줄러 중복 제거 (선택, 권장)

`PendingSubmissionSweeper`는 두 박스에서 동시에 돌아도 멱등이라 정합성 문제는
없지만, stale 제출이 2배로 재큐잉되는 낭비가 있다. 한 박스(예: OJ)만 켜도록
env 게이트를 두는 걸 권장:

- `application.yml`에 `judge.sweeper-enabled: ${SWEEPER_ENABLED:true}` 추가,
  `PendingSubmissionSweeper`에 `@ConditionalOnProperty` 또는 메서드 초입 가드.
- OJ의 .env: `SWEEPER_ENABLED=true`, EOJ의 .env: `SWEEPER_ENABLED=false`.

> 이건 이중화의 필수 조건이 아니라 낭비 최적화다. 급하지 않으면 나중에 별도 PR로.

---

## 5. 박스에서 할 일 (수동 — PR로 자동화되지 않음)

1. **EOJ EC2 생성** — JJ와 **같은 리전/VPC**의 t3.small(2GB). 같은 VPC라
   EOJ↔JJ(RabbitMQ/Judge0)↔RDS가 사설망으로 통신.
2. **Lightsail VPC peering 확인** — OJ가 EOJ 사설 IP에 닿아야 한다(JJ/RDS용으로
   이미 켜져 있으면 그대로 사용).
3. **보안 그룹(SG) 규칙**
   - EOJ SG: inbound **8080** ← OJ 사설 IP (nginx→EOJ API)
   - JJ SG: inbound **5672**(RabbitMQ), **2358**(Judge0) ← EOJ 사설 IP
   - RDS SG: inbound **3306** ← EOJ 사설 IP
4. **EOJ 부트스트랩** — docker 설치, `/opt/algoj/.env` 배치
   (DB_HOST=RDS, RABBITMQ_HOST=JJ, JUDGE0_URL=JJ, `SWEEPER_ENABLED=false`),
   GHCR 로그인, `deploy-api-single.sh`로 1회 수동 기동해 헬스 확인.
5. **OJ nginx** — `render-upstream.sh`로 두 서버 등록된 upstream 반영, `EOJ_PRIV_IP`
   env 배치.
6. **CD 시크릿** — GitHub Actions에 `SSH_HOST_EOJ`(+필요시 키/포트) 등록.
7. **RabbitMQ→JJ 이전 완료 확인** — 이중화의 선행 조건(§ 전제).

---

## 6. 비용 / 한계

- **비용**: EOJ t3.small(2GB)는 EC2 프리티어(t3.micro 1대 750h/월) 밖 → 크레딧
  차감(≈$15/월). 크레딧 소진 후 OJ(Lightsail)+JJ+EOJ+RDS 합산이 4GB 단일 박스
  blue-green(≈$24/월)보다 비싸다. **순수 무중단만 목표면 4GB 단일 박스가 더 싸고
  단순**하다. 이중화의 값어치는 **장애 이중화(HA)** 를 함께 얻는 데 있다.
- **nginx SPOF**: LB인 OJ nginx가 죽으면 EOJ API가 살아있어도 사이트는 다운.
  진짜 HA를 원하면 (a) EOJ에도 nginx + DNS/플로팅 IP 페일오버, 또는 (b) 관리형 LB.
  스터디 OJ 수준에선 이 SPOF는 감수(문서화)하고 앱 레벨 이중화만 취한다.
- **솔로 부하**: 배포/장애 시 한 박스가 100%를 받아야 함. 현재도 단일 박스가
  100%를 처리하고 있었으므로 문제없음.

---

## 구현 순서 요약

1. (선행) RabbitMQ→JJ 이전 + `deploy-api.sh`의 `RABBITMQ_HOST` 하드코딩 제거.
2. EOJ 프로비저닝 + SG/peering (§5, 박스에서 할 일).
3. `deploy-api-single.sh` + `render-upstream.sh` + upstream conf 2-서버화 (PR).
4. `cd.yml` 6단계 롤링 오케스트레이션 (PR).
5. (선택) 스위퍼 env 게이트 (§4, 별도 PR).
