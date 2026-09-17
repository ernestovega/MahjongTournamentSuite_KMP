import { runEmaPlayerRegistrySync } from "../services/playerSyncService";
async function configureLocalExecution(): Promise<void> {
  console.log("Using live EMA pages from this local machine.");
  if (process.env.EMA_ALLOW_INSECURE_TLS === "1") {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    console.warn("WARNING: TLS certificate validation is disabled for this local run only.");
  }
}

configureLocalExecution()
  .then(() => runEmaPlayerRegistrySync("seed"))
  .then((report) => console.log(`Seed completed: ${report.additions.length} additions, ${report.updates.length} updates.`))
  .catch((error: unknown) => {
    console.error("Player seed failed.", error);
    process.exitCode = 1;
  });
