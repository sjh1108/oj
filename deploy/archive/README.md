# AWS 시절 기록 (보관용)

여기 있는 문서는 **지금 구성에 해당하지 않는다.** AWS에서 네 조각(OJ Lightsail · EOJ EC2 ·
JJ EC2 · RDS)으로 돌던 시절의 설계·이전 기록이다. 현재 구성과 운영 절차는
[`../README.md`](../README.md), 이전 경위는 [`../oracle-cloud-migration.md`](../oracle-cloud-migration.md).

| 문서 | 내용 |
|---|---|
| [`redundancy.md`](redundancy.md) | API 이중화(OJ + EOJ 교차 롤링) 설계 |
| [`offload-components.md`](offload-components.md) | MySQL → RDS, Judge0 → JJ, RabbitMQ → JJ 분리 |
| [`rabbitmq-to-jj-runbook.md`](rabbitmq-to-jj-runbook.md) | RabbitMQ를 JJ 박스로 옮긴 절차 |
| [`judge0/`](judge0/README.md) | Judge0 박스 세팅 (PyPy 수동 등록, 메모리 제한) — 자체 채점기로 대체됨 |
| [`docker-compose.jj.yml`](docker-compose.jj.yml) | JJ 박스의 RabbitMQ compose |
| [`aws-s3-images.md`](aws-s3-images.md) | 지문 이미지 S3 버킷·IAM 세팅 — 버킷 삭제됨, 지금은 인라인 SVG |
| [`aws-data-recovery.md`](aws-data-recovery.md) | 유료 전환 후 AWS 데이터 회수 — 2026-09 완료, AWS 리소스 정리까지 끝남 |

두 박스 배포 스크립트(`rolling-deploy.sh`, `deploy-api-single.sh`, `nginx/render-upstream.sh`)와
jar 직접 실행용 `algoj-api.service`는 쓸 일이 없어 삭제했다. 이중화를 다시 해야 하면
git 히스토리에서 되살린다 — 삭제 직전 커밋은 `b13d6af`.
