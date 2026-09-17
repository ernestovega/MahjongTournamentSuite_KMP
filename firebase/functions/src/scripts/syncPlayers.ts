import { runEmaPlayerRegistrySync } from "../services/playerSyncService";

if (process.env.EMA_ALLOW_INSECURE_TLS === "1") {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  console.warn("WARNING: TLS certificate validation is disabled for this local run only.");
}

runEmaPlayerRegistrySync("incremental")
  .then((report) => {
    console.log(`Sync completed: ${report.additions.length} additions, ${report.updates.length} updates.`);
  })
  .catch((error: unknown) => {
    console.error("EMA player sync failed.", error);
    process.exitCode = 1;
  });
