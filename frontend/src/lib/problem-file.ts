import type {
  CreateProblemRequest,
  Difficulty,
  Language,
  ProblemDetailResponse,
} from "@/types/api";

// ── Single-file problem format ──────────────────────────────────────────────
//
// One `.md` file holds the whole problem so admins can author offline and
// upload it to auto-fill the "문제 출제" form. Layout:
//
//   ---
//   title: 두 수의 합
//   difficulty: BRONZE        # BRONZE | SILVER | GOLD | PLATINUM | DIAMOND
//   tags: 수학, 구현           # optional, comma-separated
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
//   <!-- @generator -->  (optional; large test data without large uploads)
//   ~~~generator python3 # runs server-side; its stdout becomes the case input
//   ...generator source...
//   ~~~
//   ~~~solution cpp      # runs on that input; its stdout becomes the answer
//   ...model solution source...
//   ~~~
//   ~~~case sample       # one block per case; body is the generator's stdin
//   1 5
//   ~~~
//   ~~~case
//   42 100000
//   ~~~
//
// Sections are delimited by HTML comments (invisible when rendered, so they
// never collide with body text). Test data uses ~~~ fences so the description
// can freely use ``` code blocks.
//
// @generator cases are produced server-side via the generate API — only the
// (small) code travels over HTTP, so multi-MB test data clears the proxy's
// body-size limit. Not allowed together with @subtasks. Note: generator code
// is not stored server-side; downloading the problem later serializes the
// materialized test cases, not this section.

export interface ParsedTestCase {
  input: string;
  expectedOutput: string;
  isSample: boolean;
}

export interface ParsedSubtask {
  label: string;
  points: number;
  testCases: ParsedTestCase[];
}

export interface ParsedGeneratorCase {
  stdin: string;
  isSample: boolean;
}

export interface ParsedGeneratorSpec {
  generatorLanguage: Language;
  generatorCode: string;
  solutionLanguage: Language;
  solutionCode: string;
  cases: ParsedGeneratorCase[];
}

export interface ParsedProblem {
  title: string;
  description: string;
  inputDescription: string;
  outputDescription: string;
  timeLimit: number; // seconds (form unit)
  memoryLimit: number; // MB (form unit)
  difficulty: Difficulty;
  tags: string[];
  isPublic: boolean;
  testCases: ParsedTestCase[];
  // Present when the file uses an `<!-- @subtasks -->` section.
  subtasks?: ParsedSubtask[];
  // Present when the file uses an `<!-- @generator -->` section.
  generator?: ParsedGeneratorSpec;
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
tags: 수학, 구현
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

// Template for generator-based problems: big test data is produced server-side
// (generator stdout → input, solution stdout → answer), so only code is uploaded.
// Generation runs sequentially per case and can take ~10-20s each.
export const GENERATOR_TEMPLATE = `---
title: 큰 수의 합
difficulty: SILVER
tags: 수학, 구현
timeLimit: 1
memoryLimit: 256
isPublic: true
---

<!-- @description -->
N개의 수가 주어질 때 그 합을 출력하세요.

<!-- @input -->
첫째 줄에 N, 둘째 줄에 N개의 정수가 주어진다.

<!-- @output -->
합을 한 줄에 출력한다.

<!-- @testcases -->
~~~input sample
3
1 2 3
~~~
~~~output
6
~~~

<!-- @generator -->
~~~generator python3
# stdin: "<seed> <n>" — 케이스마다 아래 ~~~case 블록의 내용이 stdin으로 들어옵니다.
# stdout이 그대로 테스트케이스 입력이 됩니다.
import sys, random
seed, n = map(int, sys.stdin.read().split())
random.seed(seed)
print(n)
print(*[random.randint(1, 10**9) for _ in range(n)])
~~~
~~~solution python3
# 위 생성기의 출력이 stdin으로 들어오고, stdout이 기대 출력이 됩니다.
import sys
data = sys.stdin.read().split()
print(sum(map(int, data[1:])))
~~~
~~~case sample
1 5
~~~
~~~case
42 100000
~~~
~~~case
43 200000
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

// Match ``` or ~~~ fenced blocks; `info` is the fence's info string
// (e.g. "input sample", "generator python3").
function parseFences(region: string): { info: string; content: string }[] {
  const fenceRe = /(`{3,}|~{3,})[ \t]*([^\n]*)\n([\s\S]*?)\n?\1[ \t]*(?=\n|$)/g;
  const fences: { info: string; content: string }[] = [];
  let m: RegExpExecArray | null;
  while ((m = fenceRe.exec(region)) !== null) {
    fences.push({ info: m[2].trim(), content: m[3] });
  }
  return fences;
}

function parseTestCases(region: string): ParsedTestCase[] {
  const cases: ParsedTestCase[] = [];
  let pendingInput: string | null = null;
  let pendingSample = false;

  for (const fence of parseFences(region)) {
    const info = fence.info.toLowerCase();
    const content = fence.content;
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

const LANGUAGE_ALIASES: Record<string, Language> = {
  pypy3: "PYPY3",
  pypy: "PYPY3",
  python3: "PYTHON3",
  python: "PYTHON3",
  py: "PYTHON3",
  cpp: "CPP",
  "c++": "CPP",
  java: "JAVA",
  c: "C",
  javascript: "JAVASCRIPT",
  js: "JAVASCRIPT",
  node: "JAVASCRIPT",
};

function parseLanguage(raw: string, fenceName: string): Language {
  const lang = LANGUAGE_ALIASES[raw.toLowerCase()];
  if (!lang) {
    throw new Error(
      `~~~${fenceName} 블록의 언어 '${raw}'를 지원하지 않습니다. (python3, pypy3, cpp, java, c, javascript)`,
    );
  }
  return lang;
}

// Parse the `<!-- @generator -->` region: one ~~~generator <lang> fence, one
// ~~~solution <lang> fence, and one ~~~case fence per generated test case
// (body = the generator's stdin; `sample` in the info string marks a sample).
function parseGenerator(region: string): ParsedGeneratorSpec {
  let generator: { language: Language; code: string } | null = null;
  let solution: { language: Language; code: string } | null = null;
  const cases: ParsedGeneratorCase[] = [];

  for (const fence of parseFences(region)) {
    const [name, ...rest] = fence.info.split(/\s+/);
    const kind = (name ?? "").toLowerCase();
    if (kind === "generator" || kind === "solution") {
      const langRaw = rest[0];
      if (!langRaw) {
        throw new Error(`~~~${kind} 블록에 언어를 지정하세요. (예: ~~~${kind} python3)`);
      }
      const parsed = { language: parseLanguage(langRaw, kind), code: fence.content };
      if (kind === "generator") {
        if (generator) throw new Error("~~~generator 블록은 하나만 쓸 수 있습니다.");
        generator = parsed;
      } else {
        if (solution) throw new Error("~~~solution 블록은 하나만 쓸 수 있습니다.");
        solution = parsed;
      }
    } else if (kind === "case") {
      cases.push({
        stdin: fence.content,
        isSample: rest.some((t) => t.toLowerCase() === "sample"),
      });
    }
  }

  if (!generator) {
    throw new Error("<!-- @generator --> 섹션에 ~~~generator <언어> 블록이 필요합니다.");
  }
  if (!solution) {
    throw new Error("<!-- @generator --> 섹션에 ~~~solution <언어> 블록이 필요합니다.");
  }
  if (cases.length === 0) {
    throw new Error("<!-- @generator --> 섹션에 ~~~case 블록이 하나 이상 필요합니다.");
  }

  return {
    generatorLanguage: generator.language,
    generatorCode: generator.code,
    solutionLanguage: solution.language,
    solutionCode: solution.code,
    cases,
  };
}

// Parse the `<!-- @subtasks -->` region: each subtask starts at a `## ... points=NN`
// heading, followed by ~~~input/~~~output test-case fences (same as @testcases).
function parseSubtasks(region: string): ParsedSubtask[] {
  if (!region.trim()) return [];
  const subtasks: ParsedSubtask[] = [];
  let header: string | null = null;
  let body: string[] = [];

  const flush = () => {
    if (header === null) return;
    const pm = header.match(/points\s*=\s*(\d+)/i) ?? header.match(/(\d+)/);
    const points = pm ? parseInt(pm[1], 10) : 0;
    let label = header
      .replace(/points\s*=\s*\d+/i, "")
      .replace(/[|[\]]/g, " ")
      .trim();
    if (!label) label = `서브태스크 ${subtasks.length + 1}`;
    const testCases = parseTestCases(body.join("\n"));
    if (testCases.length > 0) subtasks.push({ label, points, testCases });
    header = null;
    body = [];
  };

  for (const line of region.split("\n")) {
    const h = line.match(/^##\s+(.*)$/);
    if (h) {
      flush();
      header = h[1].trim();
    } else if (header !== null) {
      body.push(line);
    }
  }
  flush();
  return subtasks;
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

  const subtasks = parseSubtasks(sections.subtasks ?? "");
  const generator =
    sections.generator !== undefined ? parseGenerator(sections.generator) : undefined;
  if (generator && subtasks.length > 0) {
    throw new Error("@generator 섹션은 서브태스크 문제와 함께 사용할 수 없습니다.");
  }

  return {
    title,
    description,
    inputDescription: sections.input ?? "",
    outputDescription: sections.output ?? "",
    timeLimit: clampNumber(parseFloat(meta.timelimit ?? "1"), 0.1, 60, 1),
    memoryLimit: clampNumber(parseFloat(meta.memorylimit ?? "256"), 1, 1024, 256),
    difficulty,
    tags: (meta.tags ?? "")
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean)
      .slice(0, 10),
    isPublic: !/^(false|no|0)$/i.test(meta.ispublic ?? "true"),
    testCases: parseTestCases(sections.testcases ?? ""),
    subtasks: subtasks.length > 0 ? subtasks : undefined,
    generator,
  };
}

/** Convert a parsed file straight into the create-problem API payload
 *  (form units → API units: seconds→ms, MB→KB; running orderIndex). */
export function toCreateProblemRequest(p: ParsedProblem): CreateProblemRequest {
  const base = {
    title: p.title,
    description: p.description,
    inputDescription: p.inputDescription,
    outputDescription: p.outputDescription,
    timeLimit: Math.round(p.timeLimit * 1000),
    memoryLimit: Math.round(p.memoryLimit * 1024),
    difficulty: p.difficulty,
    tags: p.tags,
    isPublic: p.isPublic,
  };
  if (p.subtasks && p.subtasks.length > 0) {
    let order = 0;
    return {
      ...base,
      subtasks: p.subtasks.map((st) => ({
        label: st.label,
        points: st.points,
        testCases: st.testCases.map((tc) => ({ ...tc, orderIndex: order++ })),
      })),
    };
  }
  return {
    ...base,
    testCases: p.testCases.map((tc, i) => ({ ...tc, orderIndex: i })),
  };
}

/** Serialize a problem to the same single-file format (used by "문제 다운로드"). */
export function buildProblemMarkdown(p: ProblemDetailResponse): string {
  const out: string[] = [
    "---",
    `title: ${p.title}`,
    `difficulty: ${p.difficulty}`,
    ...(p.tags.length > 0 ? [`tags: ${p.tags.join(", ")}`] : []),
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
