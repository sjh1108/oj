"use client";

import type { CSSProperties } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeRaw from "rehype-raw";
import rehypeKatex from "rehype-katex";

import { cn } from "@/lib/utils";

type Props = {
  children: string;
  className?: string;
  // Inline font-size (px) — prose sizes everything in em, so this scales the
  // whole document (지문 글씨 크기 설정).
  style?: CSSProperties;
};

export function Markdown({ children, className, style }: Props) {
  return (
    <div
      style={style}
      className={cn(
        "prose prose-sm dark:prose-invert max-w-none break-words",
        "prose-pre:bg-muted prose-pre:text-foreground",
        "prose-code:before:content-none prose-code:after:content-none",
        className,
      )}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeRaw, rehypeKatex]}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}