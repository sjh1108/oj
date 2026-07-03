import { SlashCommandBuilder } from "discord.js";

// Slash command definitions, shared by the registration script and the runtime handler.
export const commands = [
  new SlashCommandBuilder()
    .setName("연동")
    .setDescription("OJ 계정을 디스코드와 연동합니다. (OJ 내 계정 페이지에서 코드 발급)")
    .addStringOption((opt) =>
      opt
        .setName("코드")
        .setDescription("OJ 계정 페이지에서 발급한 6자리 연동 코드")
        .setRequired(true),
    ),
  new SlashCommandBuilder()
    .setName("비밀번호분실")
    .setDescription("연동된 OJ 계정의 임시 비밀번호를 발급받습니다. (본인만 볼 수 있음)"),
  new SlashCommandBuilder()
    .setName("서버상태")
    .setDescription("OJ 서버 상태를 확인합니다. (DB · 채점 큐 · Judge0 · 제출 현황)"),
].map((c) => c.toJSON());
