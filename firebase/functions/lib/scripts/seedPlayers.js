"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const playerSyncService_1 = require("../services/playerSyncService");
async function configureLocalExecution() {
    console.log("Using live EMA pages from this local machine.");
    if (process.env.EMA_ALLOW_INSECURE_TLS === "1") {
        process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
        console.warn("WARNING: TLS certificate validation is disabled for this local run only.");
    }
}
configureLocalExecution()
    .then(() => (0, playerSyncService_1.runEmaPlayerRegistrySync)("seed"))
    .then((report) => console.log(`Seed completed: ${report.additions.length} additions, ${report.updates.length} updates.`))
    .catch((error) => {
    console.error("Player seed failed.", error);
    process.exitCode = 1;
});
//# sourceMappingURL=seedPlayers.js.map