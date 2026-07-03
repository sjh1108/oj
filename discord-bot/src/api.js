import { config } from "./config.js";

// Thin client for the OJ backend's bot-only endpoints (/api/internal/**).
// Sends the shared bot API key; surfaces the backend's error message when present.

async function request(method, path, body) {
  const res = await fetch(`${config.apiBaseUrl}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Bot-Api-Key": config.botApiKey,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await res.text();
  let data;
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { message: text };
  }

  if (!res.ok) {
    const message = data?.message || `요청 실패 (HTTP ${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    err.code = data?.code;
    throw err;
  }
  return data;
}

const post = (path, body) => request("POST", path, body);
const get = (path) => request("GET", path);

export const ojApi = {
  link: (discordUserId, code) =>
    post("/api/internal/discord/link", { discordUserId, code }),
  resetPassword: (discordUserId) =>
    post("/api/internal/discord/reset-password", { discordUserId }),
  monitor: () => get("/api/internal/monitor"),
};
