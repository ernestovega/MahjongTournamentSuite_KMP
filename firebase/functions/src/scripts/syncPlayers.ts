import { runEmaPlayerRegistrySync } from "../services/playerSyncService";
import { runScript } from "./scriptRun";

const localSourceDir = process.env.EMA_LOCAL_SOURCE_DIR?.trim();
if (localSourceDir) {
  console.log(`Using local EMA mirror: ${localSourceDir}`);
} else if (process.env.EMA_ALLOW_INSECURE_TLS === "1") {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  console.warn("WARNING: TLS certificate validation is disabled for this local run only.");
}

runScript("syncPlayers", async () => {
  const report = await runEmaPlayerRegistrySync("incremental");
  console.log(`Sync completed: ${report.additions.length} additions, ${report.updates.length} updates.`);
  return { additions: report.additions.length, updates: report.updates.length };
}, true);
