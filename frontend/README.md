# AlgoJ 프론트엔드

Next.js 14(App Router) + React 18 + TypeScript + Tailwind CSS 4. 운영은 Vercel
(https://oj-oui2.vercel.app)에 배포되고, API는 `algoj.duckdns.org`의 백엔드를 부른다.

## 실행

```bash
npm ci
npm run dev        # http://localhost:3000
npm run lint
npm run build
```

API 주소는 `NEXT_PUBLIC_API_BASE_URL`(기본 `http://localhost:8080`). 로컬 백엔드 띄우는 법은
[저장소 README](../README.md#로컬-개발).

## 구조

```
src/
├── app/
│   ├── (auth)/        # 로그인 · 회원가입
│   └── (main)/        # 문제 · 제출 · 내 계정 · 관리자(admin/)
├── components/        # 화면 컴포넌트 (ui/ 는 shadcn 기반 공용 컴포넌트)
├── lib/               # API 클라이언트(*-api.ts), 인증 상태, 문제 .md 파서 등
└── types/
```

## 저장 위치 규칙

- 계정에 따라와야 하는 설정은 서버(`/api/users/me/preferences`)에 둔다.
- 기기에 매인 값만 localStorage에 둔다 — 에디터 글꼴·크기·줄바꿈(`editor-settings.ts`),
  작성 중인 코드, 관리자 선정 문제 알림 기록(`problem-announcement-history.ts`).
