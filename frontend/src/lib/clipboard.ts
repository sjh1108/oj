import { toast } from "sonner";

/** 클립보드에 복사하고 결과를 토스트로 알린다. label은 "코드", "메모" 등. */
export async function copyToClipboard(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text);
    toast.success(`${label} 복사됨`);
  } catch {
    toast.error("복사 실패");
  }
}
