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
  // Base URL of the OJ backend, e.g. http://localhost:8080 or https://algoj.duckdns.org
  apiBaseUrl: (process.env.OJ_API_BASE_URL || "http://localhost:8080").replace(/\/$/, ""),
  botApiKey: required("BOT_API_KEY"),
  // Where members log in to change their password after a reset.
  webBaseUrl: (process.env.OJ_WEB_BASE_URL || "https://algoj.duckdns.org").replace(/\/$/, ""),
};
