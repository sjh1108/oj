// Central config loaded from environment. Fail fast if required values are missing.

function required(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`Missing required env var: ${name}`);
    process.exit(1);
  }
  return value;
}

export const config = {
  token: required("DISCORD_TOKEN"),
  clientId: required("DISCORD_CLIENT_ID"),
  guildId: required("DISCORD_GUILD_ID"),
  // Base URL of the OJ backend API, e.g. http://localhost:8080 or http://127.0.0.1:8080.
  apiBaseUrl: (process.env.OJ_API_BASE_URL || "http://localhost:8080").replace(/\/$/, ""),
  botApiKey: required("BOT_API_KEY"),
  // Base URL of the FRONTEND web app (Vercel) where members log in / change
  // their password — the site that serves /account. NOT the API domain.
  webBaseUrl: (process.env.OJ_WEB_BASE_URL || "http://localhost:3000").replace(/\/$/, ""),

  // ── Deploy announcements (optional) ──
  // Channel that receives the "새 업데이트" embed after a successful deploy.
  // Leave unset to disable the announce listener entirely.
  announceChannelId: process.env.DISCORD_ANNOUNCE_CHANNEL_ID || "",
  // Loopback-only HTTP port the CD deploy script POSTs the PR info to.
  announcePort: parseInt(process.env.ANNOUNCE_PORT || "3910", 10),
};
