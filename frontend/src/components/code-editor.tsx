"use client";

import dynamic from "next/dynamic";

const Monaco = dynamic(() => import("@monaco-editor/react"), { ssr: false });

const LANG_TO_MONACO: Record<string, string> = {
  JAVA: "java",
  PYTHON3: "python",
  PYPY3: "python",
  CPP: "cpp",
  C: "c",
  JAVASCRIPT: "javascript",
};

export function CodeEditor({
  language,
  value,
  onChange,
  readOnly = false,
  height = "500px",
  fontSize = 14,
  fontFamily = "",
  wordWrap = false,
}: {
  language: string;
  value: string;
  onChange?: (v: string) => void;
  readOnly?: boolean;
  height?: string;
  fontSize?: number;
  fontFamily?: string; // "" = Monaco default
  wordWrap?: boolean;
}) {
  return (
    <div className="border rounded-lg overflow-hidden">
      <Monaco
        height={height}
        language={LANG_TO_MONACO[language] ?? "plaintext"}
        theme="vs-dark"
        value={value}
        onChange={(v) => onChange?.(v ?? "")}
        options={{
          readOnly,
          minimap: { enabled: false },
          fontSize,
          ...(fontFamily ? { fontFamily } : {}),
          wordWrap: wordWrap ? "on" : "off",
          tabSize: 2,
          scrollBeyondLastLine: false,
          automaticLayout: true,
        }}
      />
    </div>
  );
}
