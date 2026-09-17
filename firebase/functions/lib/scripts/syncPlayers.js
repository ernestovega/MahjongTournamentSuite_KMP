"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const playerSyncService_1 = require("../services/playerSyncService");
if (process.env.EMA_ALLOW_INSECURE_TLS === "1") {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    console.warn("WARNING: TLS certificate validation is disabled for this local run only.");
}
(0, playerSyncService_1.runEmaPlayerRegistrySync)("incremental")
    .then((report) => {
    console.log(`Sync completed: ${report.additions.length} additions, ${report.updates.length} updates.`);
})
    .catch((error) => {
    console.error("EMA player sync failed.", error);
    process.exitCode = 1;
});
//# sourceMappingURL=syncPlayers.js.map