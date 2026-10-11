"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.planTournamentBackfill = planTournamentBackfill;
exports.backfillTournament = backfillTournament;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const tableManagerService_1 = require("./tableManagerService");
/** Works out what an old tournament needs, without touching the database. */
function planTournamentBackfill(tables) {
    return {
        tablesWithProgress: tables.filter((table) => (0, tableManagerService_1.calculateTableSummary)(table.data, table.hands).hasProgress).length,
        missingHands: tables
            .map((table) => {
            const present = new Set(table.hands.map((hand) => Number(hand.handId)));
            const handIds = Array.from({ length: tableManagerService_1.HANDS_PER_TABLE }, (_, index) => index + 1).filter((id) => !present.has(id));
            return { tableId: table.id, handIds };
        })
            .filter((entry) => entry.handIds.length > 0),
    };
}
/**
 * Prepares one tournament for the multi-editor features:
 * - creates every missing hand, so each table has hands 1 to 16,
 * - sets `tablesWithProgress`.
 *
 * Existing hands are never overwritten, so the function can run again and can run next to a save.
 * The counter can still be wrong if a save starts a table while the function runs. Run it when nobody edits.
 */
async function backfillTournament(tournamentId, apply) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(tournamentId);
    const tableDocs = (await tournamentRef.collection("tables").get()).docs;
    const tables = await Promise.all(tableDocs.map(async (table) => ({
        id: table.id,
        data: table.data(),
        hands: (await table.ref.collection("hands").get()).docs.map((hand) => hand.data()),
    })));
    const plan = planTournamentBackfill(tables);
    const result = {
        ...plan,
        tableCount: tables.length,
        applied: apply,
    };
    if (!apply)
        return result;
    let batch = firebase_1.db.batch();
    let operations = 0;
    const flush = async () => {
        if (operations === 0)
            return;
        await batch.commit();
        batch = firebase_1.db.batch();
        operations = 0;
    };
    for (const { tableId, handIds } of plan.missingHands) {
        for (const handId of handIds) {
            // create() fails if a hand appeared meanwhile, so a running save is never overwritten.
            batch.create(tournamentRef.collection("tables").doc(tableId).collection("hands").doc(String(handId)), {
                ...(0, tableManagerService_1.defaultHand)(handId),
                createdAt: firestore_1.FieldValue.serverTimestamp(),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
            operations++;
            if (operations >= 450)
                await flush();
        }
    }
    batch.update(tournamentRef, { tablesWithProgress: plan.tablesWithProgress });
    operations++;
    await flush();
    return result;
}
//# sourceMappingURL=tournamentBackfill.js.map