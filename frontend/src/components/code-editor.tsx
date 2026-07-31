"use client";

import dynamic from "next/dynamic";
import { useCallback, useMemo } from "react";

const Monaco = dynamic(() => import("@monaco-editor/react"), { ssr: false });

const LANG_TO_MONACO: Record<string, string> = {
  JAVA: "java",
  PYTHON3: "python",
  PYPY3: "python",
  CPP: "cpp",
  C: "c",
  JAVASCRIPT: "javascript",
};

/**
 * Monaco wrapper.
 *
 * Editable editors are **uncontrolled**: `value` seeds the initial content and
 * every later edit is reported through `onChange`, but the parent's copy is
 * never pushed back in. Feeding it back raced with typing — @monaco-editor/react
 * syncs a changed `value` from a passive effect, so a keystroke landing between
 * React's commit and that effect made it see a stale value and replace the whole
 * document (`executeEdits` over the full model range), which parks the caret at
 * the end of the file. To replace the content on purpose (language switch, draft
 * restore) change the element's `key` so the editor remounts with the new text.
 *
 * Read-only editors stay controlled — they have no caret to disturb and their
 * content does arrive after mount (e.g. a submission loaded from the API).
 */
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
  // Stable identities: a fresh object/closure per render made the library
  // re-run updateOptions and re-subscribe to the change event on every keystroke.
  const options = useMemo(
    () => ({
      readOnly,
      minimap: { enabled: false },
      fontSize,
      ...(fontFamily ? { fontFamily } : {}),
      wordWrap: wordWrap ? ("on" as const) : ("off" as const),
      tabSize: 2,
      scrollBeyondLastLine: false,
      automaticLayout: true,
    }),
    [readOnly, fontSize, fontFamily, wordWrap],
  );

  const handleChange = useCallback(
    (v: string | undefined) => onChange?.(v ?? ""),
    [onChange],
  );

  return (
    <div className="border rounded-lg overflow-hidden">
      <Monaco
        height={height}
        language={LANG_TO_MONACO[language] ?? "plaintext"}
        theme="vs-dark"
        {...(readOnly ? { value } : { defaultValue: value })}
        onChange={handleChange}
        options={options}
      />
    </div>
  );
}
