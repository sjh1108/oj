import type { Difficulty, ProblemDetailResponse } from "@/types/api";

// ── Single-file problem format ──────────────────────────────────────────────
//
// One `.md` file holds the whole problem so admins can author offline and
// upload it to auto-fill the "문제 출제" form. Layout:
//
//   ---
//   title: 두 수의 합
//   difficulty: BRONZE        # BRONZE | SILVER | GOLD | PLATINUM | DIAMOND
//   timeLimit: 1              # seconds
//   memoryLimit: 256          # MB
//   isPublic: true
//   ---
//
//   <!-- @description -->
//   ...markdown body (``` code blocks / $math$ allowed)...
//
//   <!-- @input -->      (optional)
//   ...
//   <!-- @output -->     (optional)
//   ...
//
//   <!-- @testcases -->
//   ~~~input sample      # "sample" marks an exposed example; omit for hidden
//   1 2
//   ~~~
//   ~~~output
//   3
//   ~~~
//
// Sections are delimited by HTML comments (invisible when rendered, so they
// never collide with body text). Test data uses ~~~ fences so the description
// can freely use ``` code blocks.

export interface ParsedTestCase {
  input: string;
  expectedOutput: string;
  isSample: boolean;
}

export interface ParsedProblem {
  title: string;
  description: string;
  inputDescription: string;
  outputDescription: string;
  timeLimit: number; // seconds (form unit)
  memoryLimit: number; // MB (form unit)
  difficulty: Difficulty;
  isPublic: boolean;
  testCases: ParsedTestCase[];
}

const DIFFICULTIES: Difficulty[] = [
  "BRONZE",
  "SILVER",
  "GOLD",
  "PLATINUM",
  "DIAMOND",
];

export const PROBLEM_TEMPLATE = `---
title: 두 수의 합
difficulty: BRONZE
timeLimit: 1
memoryLimit: 256
isPublic: true
---

<!-- @description -->
A와 B를 입력받아 두 수의 합을 출력하세요.

마크다운, 수식($O(n)$), \`\`\`코드블록\`\`\` 을 자유롭게 쓸 수 있습니다.

<!-- @input -->
첫째 줄에 두 정수 A, B가 공백으로 구분되어 주어진다. (1 ≤ A, B ≤ 1000)

<!-- @output -->
A + B 를 한 줄에 출력한다.

<!-- @testcases -->
~~~input sample
1 2
~~~
~~~output
3
~~~

~~~input
5 7
~~~
~~~output
12
~~~
`;

function clampNumber(
  value: number,
  min: number,
  max: number,
  fallback: number,
): number {
  if (!Number.isFinite(value)) return fallback;
  return Math.min(max, Math.max(min, value));
}

function parseTestCases(region: string): ParsedTestCase[] {
  // Match ``` or ~~~ fenced blocks with an info string of `input...` / `output...`.
  const fenceRe = /(`{3,}|~{3,})[ \t]*([^\n]*)\n([\s\S]*?)\n?\1[ \t]*(?=\n|$)/g;
  const cases: ParsedTestCase[] = [];
  let pendingInput: string | null = null;
  let pendingSample = false;

  let m: RegExpExecArray | null;
  while ((m = fenceRe.exec(region)) !== null) {
    const info = m[2].trim().toLowerCase();
    const content = m[3];
    if (info.startsWith("input")) {
      pendingInput = content;
      pendingSample = /\bsample\b/.test(info);
    } else if (info.startsWith("output")) {
      if (pendingInput !== null) {
        cases.push({
          input: pendingInput,
          expectedOutput: content,
          isSample: pendingSample,
        });
        pendingInput = null;
        pendingSample = false;
      }
    }
  }
  return cases;
}

/** Parse a single-file problem (.md). Throws Error with a Korean message on bad input. */
export function parseProblemFile(raw: string): ParsedProblem {
  const text = raw.replace(/^﻿/, "").replace(/\r\n/g, "\n");

  // Front-matter (flat key: value between leading --- fences).
  const meta: Record<string, string> = {};
  let body = text;
  const fm = text.match(/^---\n([\s\S]*?)\n---[ \t]*\n?/);
  if (fm) {
    body = text.slice(fm[0].length);
    for (const line of fm[1].split("\n")) {
      const kv = line.match(/^([A-Za-z_]+)\s*:\s*(.*)$/);
      if (kv) meta[kv[1].toLowerCase()] = kv[2].trim();
    }
  }

  // Sections delimited by <!-- @name ... -->.
  const markerRe = /^<!--\s*@([a-zA-Z]+)[^\n]*-->[ \t]*$/gm;
  const marks: { name: string; start: number; contentStart: number }[] = [];
  let mm: RegExpExecArray | null;
  while ((mm = markerRe.exec(body)) !== null) {
    marks.push({
      name: mm[1].toLowerCase(),
      start: mm.index,
      contentStart: mm.index + mm[0].length,
    });
  }
  const sections: Record<string, string> = {};
  for (let i = 0; i < marks.length; i++) {
    const end = i + 1 < marks.length ? marks[i + 1].start : body.length;
    sections[marks[i].name] = body.slice(marks[i].contentStart, end).trim();
  }

  const title = (meta.title ?? "").trim();
  const description = sections.description ?? "";
  if (!title) {
    throw new Error("front-matter에 title이 없습니다.");
  }
  if (!description) {
    throw new Error("<!-- @description --> 섹션을 찾을 수 없거나 비어 있습니다.");
  }

  const diff = (meta.difficulty ?? "BRONZE").toUpperCase();
  const difficulty = (
    DIFFICULTIES.includes(diff as Difficulty) ? diff : "BRONZE"
  ) as Difficulty;

  return {
    title,
    description,
    inputDescription: sections.input ?? "",
    outputDescription: sections.output ?? "",
    timeLimit: clampNumber(parseFloat(meta.timelimit ?? "1"), 0.1, 60, 1),
    memoryLimit: clampNumber(parseFloat(meta.memorylimit ?? "256"), 1, 1024, 256),
    difficulty,
    isPublic: !/^(false|no|0)$/i.test(meta.ispublic ?? "true"),
    testCases: parseTestCases(sections.testcases ?? ""),
  };
}

/** Serialize a problem to the same single-file format (used by "문제 다운로드"). */
export function buildProblemMarkdown(p: ProblemDetailResponse): string {
  const out: string[] = [
    "---",
    `title: ${p.title}`,
    `difficulty: ${p.difficulty}`,
    `timeLimit: ${p.timeLimit / 1000}`,
    `memoryLimit: ${Math.round(p.memoryLimit / 1024)}`,
    `isPublic: ${p.isPublic}`,
    "---",
    "",
    "<!-- @description -->",
    p.description.trim(),
    "",
  ];

  if (p.inputDescription) {
    out.push("<!-- @input -->", p.inputDescription.trim(), "");
  }
  if (p.outputDescription) {
    out.push("<!-- @output -->", p.outputDescription.trim(), "");
  }
  if (p.sampleTestCases.length > 0) {
    out.push("<!-- @testcases -->");
    for (const tc of p.sampleTestCases) {
      out.push(`~~~input${tc.isSample ? " sample" : ""}`);
      out.push(tc.input.replace(/\n+$/, ""));
      out.push("~~~");
      out.push("~~~output");
      out.push(tc.expectedOutput.replace(/\n+$/, ""));
      out.push("~~~");
      out.push("");
    }
  }
  return out.join("\n") + "\n";
}
