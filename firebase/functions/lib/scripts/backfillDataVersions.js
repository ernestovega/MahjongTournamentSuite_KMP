"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const tournamentContentRules_1 = require("../services/tournamentContentRules");
const applyChanges = process.argv.includes("--apply");
function hasText(value) {
    return String(value ?? "").trim().length > 0;
}
async function backfill() {
    const tournaments = await firebase_1.db.collection("tournaments").get();
    const [hands, ...tablesByTournament] = await Promise.all([
        firebase_1.db.collectionGroup("hands").get(),
        ...tournaments.docs.map((tournament) => tournament.ref.collection("tables").get()),
    ]);
    const handsByTable = new Map();
    for (const hand of hands.docs) {
        const tablePath = hand.ref.parent.parent?.path;
        if (!tablePath)
            continue;
        const tableHands = handsByTable.get(tablePath) ?? [];
        tableHands.push(hand);
        handsByTable.set(tablePath, tableHands);
    }
    let pending = [];
    let tableCount = 0;
    console.log(`${applyChanges ? "Applying" : "Previewing"} data version backfill for ${firebase_1.firebaseProjectId}.`);
    const flush = async () => {
        await Promise.all(pending);
        pending = [];
    };
    for (const [tournamentIndex, tournament] of tournaments.docs.entries()) {
        const tournamentUpdates = {};
        for (const resource of ["members", "players", "teams", "rounds", "tables"]) {
            if (!Number.isSafeInteger(tournament.get(`dataVersions.${resource}.revision`))) {
                tournamentUpdates[`dataVersions.${resource}.revision`] = 1;
                tournamentUpdates[`dataVersions.${resource}.changedAt`] = firestore_1.FieldValue.serverTimestamp();
            }
        }
        if (applyChanges && Object.keys(tournamentUpdates).length > 0) {
            pending.push(tournament.ref.update(tournamentUpdates));
        }
        const tables = tablesByTournament[tournamentIndex];
        for (const table of tables.docs) {
            const tableHands = handsByTable.get(table.ref.path) ?? [];
            const hasHandProgress = tableHands.some((hand) => {
                const data = hand.data();
                return [
                    data.playerWinnerId, data.playerLooserId, data.handScore, data.playerEastPenalty,
                    data.playerSouthPenalty, data.playerWestPenalty, data.playerNorthPenalty,
                ].some(hasText) || Boolean(data.isChickenHand) || Boolean(data.isDone);
            });
            const hasTableProgress = [
                table.get("playerEastId"), table.get("playerSouthId"), table.get("playerWestId"), table.get("playerNorthId"),
                table.get("playerEastScore"), table.get("playerSouthScore"), table.get("playerWestScore"), table.get("playerNorthScore"),
                table.get("playerEastPoints"), table.get("playerSouthPoints"), table.get("playerWestPoints"), table.get("playerNorthPoints"),
            ].some(hasText);
            const useTotalsOnly = Boolean(table.get("useTotalsOnly") ?? true);
            const usePointsCalculation = Boolean(table.get("usePointsCalculation") ?? true);
            const validScores = (0, tournamentContentRules_1.hasFourValidScores)([
                table.get("manualPlayerEastScore") || table.get("playerEastScore"),
                table.get("manualPlayerSouthScore") || table.get("playerSouthScore"),
                table.get("manualPlayerWestScore") || table.get("playerWestScore"),
                table.get("manualPlayerNorthScore") || table.get("playerNorthScore"),
            ]);
            const validPoints = (0, tournamentContentRules_1.hasValidTablePoints)([
                table.get("manualPlayerEastPoints") || table.get("playerEastPoints"),
                table.get("manualPlayerSouthPoints") || table.get("playerSouthPoints"),
                table.get("manualPlayerWestPoints") || table.get("playerWestPoints"),
                table.get("manualPlayerNorthPoints") || table.get("playerNorthPoints"),
            ]);
            if (applyChanges) {
                pending.push(table.ref.update({
                    hasProgress: hasTableProgress || hasHandProgress || Boolean(table.get("isCompleted")),
                    hasValidManualTotals: useTotalsOnly ? validScores : !usePointsCalculation && validPoints,
                    completionStatus: (0, tournamentContentRules_1.calculateTableCompletionStatus)({
                        hasData: hasTableProgress || hasHandProgress,
                        seatIds: [table.get("playerEastId"), table.get("playerSouthId"), table.get("playerWestId"), table.get("playerNorthId")],
                        useTotalsOnly,
                        hasValidTotals: useTotalsOnly ? validScores : (0, tournamentContentRules_1.hasFourValidScores)([
                            table.get("playerEastScore"), table.get("playerSouthScore"), table.get("playerWestScore"), table.get("playerNorthScore"),
                        ]),
                    }),
                    bestHandScore: (0, tournamentContentRules_1.calculateBestHandScore)(tableHands.map((hand) => hand.data())),
                    chickenHandCount: (0, tournamentContentRules_1.countChickenHands)(tableHands.map((hand) => hand.data())),
                    ...(Number.isSafeInteger(table.get("version")) ? {} : { version: 0 }),
                }));
            }
            tableCount++;
            if (pending.length >= 300)
                await flush();
        }
    }
    const global = await firebase_1.db.collection("_meta").doc("dataVersions").get();
    const globalResources = {};
    for (const resource of ["tournaments", "emaPlayers", "countries", "users"]) {
        if (!Number.isSafeInteger(global.get(`resources.${resource}.revision`))) {
            globalResources[resource] = {
                revision: 1,
                changedAt: firestore_1.FieldValue.serverTimestamp(),
            };
        }
    }
    if (applyChanges && Object.keys(globalResources).length > 0) {
        pending.push(global.ref.set({ resources: globalResources }, { merge: true }));
    }
    await flush();
    console.log(`${applyChanges ? "Updated" : "Would update"} ${tournaments.size} tournaments and ${tableCount} tables.`);
    if (!applyChanges)
        console.log("Run with --apply to write the changes.");
}
backfill().catch((error) => {
    console.error("Data version backfill failed.", error);
    process.exitCode = 1;
});
//# sourceMappingURL=backfillDataVersions.js.map