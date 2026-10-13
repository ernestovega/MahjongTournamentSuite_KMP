"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const firebase_1 = require("../firebase");
const dataVersionsService_1 = require("../services/dataVersionsService");
/**
 * Marks the draw hands of legacy tournaments as done. The legacy import left them as not done, so the apps hid them.
 * A draw has no winner, no discarder and score "0". A draw after the last done hand of a table can be an unplayed
 * hand, so the script skips it and lists it. It previews by default. Add --apply to write.
 */
const applyChanges = process.argv.includes("--apply");
const LEGACY_PREFIX = "legacy-";
function isDraw(data) {
    const loser = String(data.playerLooserId ?? "");
    return String(data.playerWinnerId ?? "") === "" && (loser === "" || loser === "-") && String(data.handScore ?? "") === "0";
}
async function main() {
    console.log(`${applyChanges ? "Applying" : "Previewing"} legacy draw hand backfill for ${firebase_1.firebaseProjectId}.`);
    const tournaments = await firebase_1.db.collection("tournaments").get();
    for (const tournament of tournaments.docs.filter((doc) => doc.id.startsWith(LEGACY_PREFIX))) {
        // eslint-disable-next-line no-await-in-loop
        const tables = await tournament.ref.collection("tables").get();
        let changed = 0;
        const skipped = [];
        for (const table of tables.docs) {
            // eslint-disable-next-line no-await-in-loop
            const hands = await table.ref.collection("hands").get();
            const lastDoneId = Math.max(0, ...hands.docs.filter((hand) => hand.get("isDone") === true).map((hand) => Number(hand.id)));
            for (const hand of hands.docs) {
                const data = hand.data();
                if (data.isDone === true || !isDraw(data) || Boolean(data.isChickenHand))
                    continue;
                if (Number(hand.id) > lastDoneId) {
                    skipped.push(`${table.id}/${hand.id}`);
                    continue;
                }
                changed += 1;
                // eslint-disable-next-line no-await-in-loop
                if (applyChanges)
                    await hand.ref.update({ isDone: true });
            }
        }
        if (applyChanges && changed > 0)
            await tournament.ref.update((0, dataVersionsService_1.tournamentVersionUpdate)("tables"));
        console.log(`${tournament.id}: ${changed} draw hands ${applyChanges ? "updated" : "to update"}, ${skipped.length} skipped${skipped.length ? ` (${skipped.join(", ")})` : ""}`);
    }
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
//# sourceMappingURL=backfillLegacyDrawHands.js.map