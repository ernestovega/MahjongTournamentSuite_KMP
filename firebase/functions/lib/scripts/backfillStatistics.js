"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const firebase_1 = require("../firebase");
const statisticsStore_1 = require("../services/statisticsStore");
const applyChanges = process.argv.includes("--apply");
/**
 * Builds the statistics store for tournaments that exist already. It works one table at a time,
 * so the memory use does not depend on the number of tournaments. It is safe to run again:
 * a table that did not change adds nothing a second time.
 * It also deletes the old digest cache `tournaments/{id}/cache/statistics`.
 */
async function backfill() {
    const tournaments = await firebase_1.db.collection("tournaments").select().get();
    console.log(`${applyChanges ? "Applying" : "Previewing"} the statistics backfill for ${firebase_1.firebaseProjectId}: ${tournaments.size} tournaments.`);
    let tables = 0;
    for (const [index, tournament] of tournaments.docs.entries()) {
        if (applyChanges) {
            tables += await (0, statisticsStore_1.refreshTournamentStats)(tournament.id);
            await tournament.ref.collection("cache").doc("statistics").delete();
        }
        else {
            tables += (await tournament.ref.collection("tables").select().get()).size;
        }
        if ((index + 1) % 10 === 0)
            console.log(`${index + 1} of ${tournaments.size} tournaments done.`);
    }
    console.log(`${applyChanges ? "Processed" : "Found"} ${tables} tables.`);
    if (!applyChanges)
        console.log("Run again with --apply to write the statistics.");
}
backfill().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
//# sourceMappingURL=backfillStatistics.js.map