import { api } from "@/lib/api";
import type { RunRequest, RunResponse } from "@/types/api";

export const runApi = {
  run: (body: RunRequest) =>
    api<RunResponse>("/api/run", { method: "POST", body }),
};
