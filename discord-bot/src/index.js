import { Client, GatewayIntentBits, MessageFlags } from "discord.js";
import { config } from "./config.js";
import { ojApi } from "./api.js";

// Only the guild intent is needed — all interactions are slash commands.
const client = new Client({ intents: [GatewayIntentBits.Guilds] });

client.once("clientReady", (c) => {
  console.log(`Logged in as ${c.user.tag}`);
});

client.on("interactionCreate", async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  try {
    if (interaction.commandName === "연동") {
      await handleLink(interaction);
    } else if (interaction.commandName === "비밀번호분실") {
      await handleResetPassword(interaction);
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

client.login(config.token);
