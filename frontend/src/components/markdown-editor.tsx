"use client";

import * as React from "react";

import { Textarea } from "@/components/ui/textarea";
import { Markdown } from "@/components/markdown";
import { cn } from "@/lib/utils";

type Props = {
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  name?: string;
  id?: string;
  placeholder?: string;
  minHeight?: number;
  className?: string;
  previewEmptyText?: string;
};

export const MarkdownEditor = React.forwardRef<HTMLTextAreaElement, Props>(
  function MarkdownEditor(
    {
      value,
      onChange,
      onBlur,
      name,
      id,
      placeholder,
      minHeight = 240,
      className,
      previewEmptyText = "미리보기",
    },
    ref,
  ) {
    return (
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div className="md:col-span-2">
          <Textarea
            ref={ref}
            id={id}
            name={name}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onBlur={onBlur}
            placeholder={placeholder}
            className={cn("font-mono text-sm", className)}
            style={{ minHeight }}
          />
        </div>
        <div
          className="md:col-span-1 rounded-lg border border-input bg-background px-3 py-2 overflow-auto"
          style={{ minHeight, maxHeight: "70vh" }}
        >
          {value.trim() ? (
            <Markdown>{value}</Markdown>
          ) : (
            <p className="text-xs text-muted-foreground">{previewEmptyText}</p>
          )}
        </div>
      </div>
    );
  },
);
