"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
/**
 * Deletes the fields of the removed table models from every table document.
 * The table points are never typed or forced. They come only from the scores.
 * It previews by default. Add --apply to write. Run it when nobody edits the tournaments.
 */
const applyChanges = process.argv.includes("--apply");
const LEGACY_FIELDS = [
    "manualPlayerEastPoints", "manualPlayerSouthPoints", "manualPlayerWestPoints", "manualPlayerNorthPoints",
    "usePointsCalculation",
];
const BATCH_SIZE = 400;
async function main() {
    console.log(`${applyChanges ? "Applying" : "Previewing"} legacy table field removal for ${firebase_1.firebaseProjectId}.`);
    const tournaments = await firebase_1.db.collection("tournaments").get();
    let tableCount = 0;
    let changedCount = 0;
    for (const tournament of tournaments.docs) {
        // eslint-disable-next-line no-await-in-loop
        const tables = await tournament.ref.collection("tables").get();
        const stale = tables.docs.filter((table) => LEGACY_FIELDS.some((field) => table.get(field) !== undefined));
        tableCount += tables.size;
        changedCount += stale.length;
        console.log(`${tournament.id}: ${tables.size} tables, ${stale.length} with legacy fields`);
        if (!applyChanges)
            continue;
        for (let start = 0; start < stale.length; start += BATCH_SIZE) {
            const batch = firebase_1.db.batch();
            stale.slice(start, start + BATCH_SIZE).forEach((table) => {
                batch.update(table.ref, Object.fromEntries(LEGACY_FIELDS.map((field) => [field, firestore_1.FieldValue.delete()])));
            });
            // eslint-disable-next-line no-await-in-loop
            await batch.commit();
        }
    }
    console.log(`${changedCount} of ${tableCount} tables ${applyChanges ? "changed" : "would change"}.`);
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
//# sourceMappingURL=removeLegacyTableFields.js.map