import type { Language } from "@/types/api";

// Source-file extension per submission language.
export const LANGUAGE_EXTENSION: Record<Language, string> = {
  JAVA: "java",
  PYTHON3: "py",
  PYPY3: "py",
  CPP: "cpp",
  C: "c",
  JAVASCRIPT: "js",
};

// Make a string safe to use as a download filename (strip path/reserved chars).
export function sanitizeFilename(name: string): string {
  const cleaned = name
    .replace(/[\\/:*?"<>|]+/g, "_")
    .replace(/\s+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "")
    .slice(0, 80);
  return cleaned || "file";
}

// Trigger a client-side download of text content as a file.
export function downloadTextFile(
  filename: string,
  content: string,
  mime = "text/plain;charset=utf-8",
): void {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
