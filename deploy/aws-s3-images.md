# 지문 이미지 S3 세팅 가이드

문제 지문 이미지는 AWS S3에 저장하고 브라우저가 S3에서 직접 로드한다.
백엔드는 업로드(`POST /api/images`, ADMIN 전용)만 담당하고 조회 API는 없다.

- **AWS 콘솔에서 할 일 (1회, 수동)**: 아래 1~4단계 — 버킷·정책·IAM 키 생성
- **PR로 자동 반영되는 것**: 업로드 API, 출제 화면 "이미지 첨부" 버튼,
  `.md` 일괄 업로드의 `<!-- @assets -->` 섹션

예상 비용: 이미지 수백 장 + 스터디 규모 트래픽 기준 **월 0원에 수렴**
(프리 티어 이후에도 수 원 수준 — 스토리지 $0.025/GB·월, 요청 과금은 사실상 무시 가능).

---

## 1. S3 버킷 생성

AWS 콘솔 → S3 → **버킷 만들기**

| 항목 | 값 |
|---|---|
| 버킷 이름 | `algoj-images` (전 세계에서 유일해야 함 — 겹치면 `algoj-images-<식별자>`) |
| 리전 | `ap-northeast-2` (서울) |
| 객체 소유권 | ACL 비활성화됨 (기본값) |
| 퍼블릭 액세스 차단 | **"새 퍼블릭 버킷 정책을 통해 부여된 액세스 차단" 2개만 해제** (아래 정책을 넣기 위함) |
| 버전 관리 / 암호화 | 기본값 그대로 |

> 퍼블릭 액세스 차단 4개 중 ACL 관련 2개는 켠 채로 두고,
> "버킷 정책" 관련 2개만 끄면 된다. 정책으로만 공개 범위를 통제한다.

## 2. 버킷 정책 — `problems/*` 읽기만 익명 허용

버킷 → 권한 → **버킷 정책** 편집:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadProblemImages",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::algoj-images/problems/*"
    }
  ]
}
```

- `s3:GetObject`만 허용 → **URL을 아는 사람만** 객체를 읽을 수 있다.
  버킷 목록 조회(`s3:ListBucket`)는 막혀 있어 전체 이미지 열거는 불가능.
- `problems/*` 프리픽스로 한정 → 나중에 다른 용도의 비공개 프리픽스를
  같은 버킷에 추가해도 공개되지 않는다.
- 버킷 이름을 바꿨다면 `Resource`의 이름도 함께 바꾼다.

## 3. IAM 사용자 — 업로드 전용 최소 권한 키

AWS 콘솔 → IAM → 사용자 → **사용자 생성**

1. 이름: `algoj-image-uploader`, 콘솔 액세스 **없음** (프로그램 방식 전용)
2. 권한: "정책 직접 연결" 대신 생성 후 **인라인 정책** 추가:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PutProblemImagesOnly",
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::algoj-images/problems/*"
    }
  ]
}
```

3. 사용자 → 보안 자격 증명 → **액세스 키 만들기** (사용 사례: "AWS 외부에서
   실행되는 애플리케이션") → 키 2개를 복사해 둔다 (시크릿은 이때만 볼 수 있음).

이 키로는 `problems/` 밑에 객체를 넣는 것 **말고는 아무것도 못 한다**
(읽기·삭제·목록·다른 버킷 접근 전부 불가). 키가 유출돼도 피해가
"이미지 덮어쓰기/추가"로 한정된다.

## 4. 박스 `.env` 설정 + API 재시작

`/opt/algoj/.env`에 추가 (`.env.prod.example`의 S3 블록 참고):

```bash
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
S3_IMAGE_BUCKET=algoj-images
AWS_REGION=ap-northeast-2
```

```bash
sudo systemctl restart algoj-api
```

동작 확인: 관리자로 로그인 → 문제 출제 → 지문의 **이미지 첨부** 버튼으로 업로드
→ `https://algoj-images.s3.ap-northeast-2.amazonaws.com/problems/<uuid>.png`
형태의 URL이 지문에 삽입되고 미리보기에 렌더링되면 성공.

미설정 상태에서는 앱이 정상 부팅하며 이미지 업로드만 503(`I003`)을 반환한다.

---

## 동작 방식 요약

- 업로드: 프론트가 파일을 base64로 인코딩해 `POST /api/images`
  (`{contentType, base64Data}`) → 백엔드가 검증(타입 화이트리스트, 700KB 이하)
  후 `problems/{uuid}.{확장자}` 키로 `putObject` → 공개 URL 반환
- 객체 메타데이터: `Content-Type` + `Cache-Control: public, max-age=31536000,
  immutable` — 키가 UUID라 내용이 바뀔 일이 없으므로 브라우저/CDN이 1년 캐시
- `.md` 일괄 업로드: `<!-- @assets -->` 섹션의 `~~~image 파일명.png` 펜스(base64)를
  먼저 S3에 올리고, 지문의 `![설명](asset:파일명.png)` 참조를 실제 URL로 치환한 뒤
  문제를 생성한다

## 학습 포인트

- **버킷 정책 vs IAM 정책**: 버킷 정책은 *리소스 기반*(버킷에 붙어 "누가 이
  리소스에 접근 가능한가"), IAM 정책은 *자격 기반*(사용자에 붙어 "이 사용자가
  무엇을 할 수 있는가"). 여기서는 읽기는 버킷 정책(익명 포함), 쓰기는 IAM
  정책으로 분리했다.
- **최소 권한 원칙**: 업로더 키는 `PutObject` × `problems/*` 딱 한 가지 조합만
  허용. 권한을 넓게 주고 좁히는 것보다, 좁게 시작해 필요할 때 넓히는 편이 안전.
- **객체 메타데이터로 캐시 제어**: S3는 업로드 시 지정한 헤더를 응답에 그대로
  실어준다. 불변 키(UUID) + `immutable` 조합은 CDN 없이도 재방문 로딩을 없앤다.
- **(확장) presigned URL**: 지금은 base64가 백엔드를 경유하지만, 백엔드가
  서명된 업로드 URL만 발급하고 브라우저가 S3에 직접 PUT하는 방식으로 바꾸면
  서버 대역폭이 빠진다. 파일이 커지면 고려할 것.
