import { runEmaPlayerRegistrySync } from "../services/playerSyncService";
import { runScript } from "./scriptRun";

/**
 * Imports the EMA player registry from the EMA website (docs/ema-player-registry.md).
 * Usage: npm run sync:players [-- --full]. Without --full it reads only the result pages that are not indexed yet;
 * --full reads every known result page (the first import, or a repair).
 */
const full = process.argv.includes("--full");
const localSourceDir = process.env.EMA_LOCAL_SOURCE_DIR?.trim();
if (localSourceDir) {
  console.log(`Using local EMA mirror: ${localSourceDir}`);
} else {
  console.log("Using live EMA pages from this local machine.");
  if (process.env.EMA_ALLOW_INSECURE_TLS === "1") {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    console.warn("WARNING: TLS certificate validation is disabled for this local run only.");
  }
}

runScript("syncPlayers", async () => {
  const report = await runEmaPlayerRegistrySync(full ? "seed" : "incremental");
  console.log(`${full ? "Full" : "Incremental"} sync completed: ${report.additions.length} additions, ${report.updates.length} updates.`);
  return { mode: full ? "full" : "incremental", additions: report.additions.length, updates: report.updates.length };
}, true);
