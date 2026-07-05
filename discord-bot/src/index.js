import { Client, EmbedBuilder, GatewayIntentBits, MessageFlags } from "discord.js";
import { config } from "./config.js";
import { ojApi } from "./api.js";
import { startAnnounceServer } from "./announce.js";

// Only the guild intent is needed — all interactions are slash commands.
const client = new Client({ intents: [GatewayIntentBits.Guilds] });

client.once("clientReady", (c) => {
  console.log(`Logged in as ${c.user.tag}`);
  startAnnounceServer(client);
});

client.on("interactionCreate", async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  try {
    if (interaction.commandName === "연동") {
      await handleLink(interaction);
    } else if (interaction.commandName === "비밀번호분실") {
      await handleResetPassword(interaction);
    } else if (interaction.commandName === "서버상태") {
      await handleStatus(interaction);
    }
  } catch (err) {
    console.error(`Command ${interaction.commandName} failed:`, err);
    const message = `⚠️ ${err.message || "처리 중 오류가 발생했습니다."}`;
    if (interaction.deferred || interaction.replied) {
      await interaction.editReply({ content: message });
    } else {
      await interaction.reply({ content: message, flags: MessageFlags.Ephemeral });
    }
  }
});

async function handleLink(interaction) {
  const code = interaction.options.getString("코드", true);
  // Ephemeral so the code/result is visible only to the requester.
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  const result = await ojApi.link(interaction.user.id, code);
  await interaction.editReply({
    content: `✅ **${result.username}** 계정과 연동되었습니다.`,
  });
}

async function handleResetPassword(interaction) {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  const result = await ojApi.resetPassword(interaction.user.id);
  await interaction.editReply({
    content:
      `🔑 **${result.username}** 임시 비밀번호가 발급되었습니다.\n\n` +
      `\`\`\`${result.temporaryPassword}\`\`\`\n` +
      `이 비밀번호로 로그인한 뒤 **${config.webBaseUrl}/account** 에서 비밀번호를 바꿔주세요.\n` +
      `이 메시지는 본인에게만 보입니다.`,
  });
}

async function handleStatus(interaction) {
  await interaction.deferReply();
  const m = await ojApi.monitor();

  const ok = (up) => (up ? "🟢 정상" : "🔴 응답 없음");
  const q = m.judgeQueue;
  const queueLine = q.brokerUp
    ? `🟢 대기 ${q.messages ?? "?"}건 · 워커 ${q.consumers ?? "?"}개 · DLQ ${q.deadLettered ?? "?"}건`
    : "🔴 브로커 응답 없음";
  const dlqWarning = q.brokerUp && (q.deadLettered ?? 0) > 0;
  const healthy = m.dbUp && m.judge0Up && q.brokerUp && (q.consumers ?? 0) > 0;

  const embed = new EmbedBuilder()
    .setColor(healthy ? 0x57f287 : 0xed4245)
    .setTitle(`${healthy ? "✅" : "⚠️"} OJ 서버 상태`)
    .addFields(
      { name: "데이터베이스", value: ok(m.dbUp), inline: true },
      { name: "Judge0 (채점 엔진)", value: ok(m.judge0Up), inline: true },
      { name: "채점 큐", value: queueLine, inline: false },
      {
        name: "제출 현황",
        value: `대기 ${m.submissions.pending}건 · 채점 중 ${m.submissions.judging}건 · 오늘 ${m.submissions.submittedToday}건`,
        inline: false,
      },
      {
        name: "API 서버",
        value: `힙 ${m.jvm.heapUsedMb}/${m.jvm.heapMaxMb}MB · 가동 ${formatUptime(m.jvm.uptimeSeconds)}`,
        inline: false,
      },
    )
    .setTimestamp(new Date());
  if (dlqWarning) {
    embed.setFooter({ text: "⚠️ DLQ에 실패 메시지가 쌓여 있습니다 — 관리자 확인 필요" });
  }

  await interaction.editReply({ embeds: [embed] });
}

function formatUptime(totalSeconds) {
  const d = Math.floor(totalSeconds / 86400);
  const h = Math.floor((totalSeconds % 86400) / 3600);
  const min = Math.floor((totalSeconds % 3600) / 60);
  if (d > 0) return `${d}일 ${h}시간`;
  if (h > 0) return `${h}시간 ${min}분`;
  return `${min}분`;
}

client.login(config.token);
