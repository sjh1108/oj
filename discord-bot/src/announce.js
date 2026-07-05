import http from "node:http";
import { EmbedBuilder } from "discord.js";
import { config } from "./config.js";

// Deploy-announcement listener.
//
// CD deploys over SSH on the same box, so after a successful blue-green swap
// it POSTs the merged PR's info here. The bot (network_mode: host) listens on
// the loopback interface only — nothing is exposed publicly — and the request
// must carry the same X-Bot-Api-Key the backend uses for /api/internal/**.
//
// POST /announce  { number, title, body, url }
//   → posts an update embed to DISCORD_ANNOUNCE_CHANNEL_ID.

const MAX_DESCRIPTION = 3500; // embed limit is 4096; leave headroom

export function startAnnounceServer(client) {
  if (!config.announceChannelId) {
    console.log("DISCORD_ANNOUNCE_CHANNEL_ID not set — announce listener disabled.");
    return;
  }

  const server = http.createServer(async (req, res) => {
    const respond = (status, message) => {
      res.writeHead(status, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ message }));
    };

    if (req.method !== "POST" || req.url !== "/announce") {
      return respond(404, "not found");
    }
    if (req.headers["x-bot-api-key"] !== config.botApiKey) {
      return respond(401, "invalid api key");
    }

    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 64 * 1024) req.destroy();
    });
    req.on("end", async () => {
      try {
        const { number, title, body, url } = JSON.parse(raw);
        if (!title) return respond(400, "title is required");

        const channel = await client.channels.fetch(config.announceChannelId);
        if (!channel?.isTextBased()) {
          return respond(500, "announce channel not found or not a text channel");
        }

        let description = (body || "").trim();
        if (description.length > MAX_DESCRIPTION) {
          description = `${description.slice(0, MAX_DESCRIPTION)}\n\n… (전체 내용은 PR에서)`;
        }

        const embed = new EmbedBuilder()
          .setColor(0x57f287)
          .setTitle(`📦 업데이트 배포 — ${title}`.slice(0, 256))
          .setDescription(description || "(설명 없음)")
          .setTimestamp(new Date());
        if (url) embed.setURL(url);
        if (number) embed.setFooter({ text: `PR #${number} · master 머지 자동 공지` });

        await channel.send({ embeds: [embed] });
        console.log(`Announced deploy of PR #${number ?? "?"} to #${channel.name}`);
        respond(200, "announced");
      } catch (err) {
        console.error("Announce failed:", err);
        respond(500, err.message || "announce failed");
      }
    });
  });

  server.listen(config.announcePort, "127.0.0.1", () => {
    console.log(`Announce listener on 127.0.0.1:${config.announcePort}`);
  });
  server.on("error", (err) => {
    console.error("Announce listener error:", err);
  });
}
