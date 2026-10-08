import type { Language } from "@/types/api";

/** 문제 화면 에디터가 쓰는 문제·언어별 작성 중 코드(localStorage) 키. */
export const draftKey = (problemId: number, lang: Language) =>
  `algoj-draft:${problemId}:${lang}`;
