import { REST, Routes } from "discord.js";
import { config } from "./config.js";
import { commands } from "./commands.js";

// Registers the slash commands to a single guild (instant, unlike global commands).
const rest = new REST({ version: "10" }).setToken(config.token);

try {
  console.log(`Registering ${commands.length} commands to guild ${config.guildId}...`);
  await rest.put(
    Routes.applicationGuildCommands(config.clientId, config.guildId),
    { body: commands },
  );
  console.log("Slash commands registered.");
} catch (err) {
  console.error("Failed to register commands:", err);
  process.exit(1);
}
