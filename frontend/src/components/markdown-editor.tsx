"use client";

import * as React from "react";
import { ImagePlus, Loader2 } from "lucide-react";
import { toast } from "sonner";

import { ApiError } from "@/lib/api";
import { problemsApi } from "@/lib/problems-api";
import { Button } from "@/components/ui/button";
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
  // Show an "이미지 첨부" button that uploads to S3 (admin-only API) and
  // inserts ![alt](url) markdown at the cursor.
  imageUpload?: boolean;
};

const ACCEPTED_IMAGE_TYPES =
  "image/png,image/jpeg,image/gif,image/webp,image/svg+xml";

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
      imageUpload = false,
    },
    ref,
  ) {
    const innerRef = React.useRef<HTMLTextAreaElement | null>(null);
    const fileRef = React.useRef<HTMLInputElement | null>(null);
    const [uploading, setUploading] = React.useState(false);

    const setRefs = (el: HTMLTextAreaElement | null) => {
      innerRef.current = el;
      if (typeof ref === "function") ref(el);
      else if (ref) ref.current = el;
    };

    const handleFile = async (file: File) => {
      // Pre-check before upload: base64 of anything much over 700KB would blow
      // past nginx's 1MB body limit and die as an opaque network error there,
      // never reaching the server's friendly validation message.
      if (file.size > 700 * 1024) {
        toast.error("이미지가 너무 큽니다 (700KB 이하). 압축하거나 WebP 변환을 권장합니다.");
        if (fileRef.current) fileRef.current.value = "";
        return;
      }
      setUploading(true);
      try {
        const dataUrl = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result as string);
          reader.onerror = () => reject(reader.error);
          reader.readAsDataURL(file);
        });
        // data:image/png;base64,XXXX → contentType + raw base64
        const comma = dataUrl.indexOf(",");
        const contentType = dataUrl.slice(5, dataUrl.indexOf(";"));
        const base64Data = dataUrl.slice(comma + 1);

        const { url } = await problemsApi.uploadImage({ contentType, base64Data });

        const alt = file.name.replace(/\.[^.]+$/, "") || "그림";
        const snippet = `![${alt}](${url})`;
        const ta = innerRef.current;
        const start = ta?.selectionStart ?? value.length;
        const end = ta?.selectionEnd ?? start;
        onChange(value.slice(0, start) + snippet + value.slice(end));
        toast.success("이미지 업로드 완료 — 커서 위치에 삽입됨");
      } catch (err) {
        if (err instanceof ApiError) toast.error(err.message);
        else toast.error("이미지 업로드 실패");
      } finally {
        setUploading(false);
        if (fileRef.current) fileRef.current.value = "";
      }
    };

    return (
      <div className="space-y-2">
        {imageUpload && (
          <div className="flex justify-end">
            <input
              ref={fileRef}
              type="file"
              accept={ACCEPTED_IMAGE_TYPES}
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) handleFile(f);
              }}
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={uploading}
              onClick={() => fileRef.current?.click()}
            >
              {uploading ? (
                <Loader2 className="size-4 mr-1 animate-spin" />
              ) : (
                <ImagePlus className="size-4 mr-1" />
              )}
              {uploading ? "업로드 중..." : "이미지 첨부"}
            </Button>
          </div>
        )}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="md:col-span-2">
            <Textarea
              ref={setRefs}
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
      </div>
    );
  },
);
