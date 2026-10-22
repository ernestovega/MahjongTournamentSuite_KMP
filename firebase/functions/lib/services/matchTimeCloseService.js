"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.closeRoundTables = closeRoundTables;
const firebase_1 = require("../firebase");
const matchTime_1 = require("./matchTime");
const dataVersionsService_1 = require("./dataVersionsService");
const tableManagerService_1 = require("./tableManagerService");
const SYSTEM_ACTOR = { uid: "system", name: "Match time", source: "system" };
/** Rounds of the tournament whose match time is over at an instant. Empty when the server cannot know it. */
function overRounds(tournament, nowMs) {
    const numRounds = Number(tournament.numRounds ?? 0);
    const rounds = [];
    for (let roundId = 1; roundId <= numRounds; roundId += 1) {
        const { rules, schedule } = (0, matchTime_1.matchTimeOfTournament)(tournament, roundId);
        if ((0, matchTime_1.matchPhaseAt)(schedule, rules, nowMs) === "OVER")
            rounds.push(roundId);
    }
    return rounds;
}
/**
 * Closes one table whose time is over. A table with played hands becomes completed. A table with no played hands
 * is only marked as checked, so the next runs skip it. The mark `matchTimeChecked` also stops the server from
 * closing a table again when an organizer opens it to add the missing data.
 */
async function closeTable(tournamentId, roundId, tableId) {
    const tableRef = firebase_1.db.doc(`tournaments/${tournamentId}/tables/${(0, dataVersionsService_1.tableVersionKey)(roundId, tableId)}`);
    return firebase_1.db.runTransaction(async (transaction) => {
        const tableSnap = await transaction.get(tableRef);
        if (!tableSnap.exists || tableSnap.get("isCompleted") === true || tableSnap.get("matchTimeChecked") === true)
            return false;
        const handSnaps = await transaction.getAll(...Array.from({ length: tableManagerService_1.HANDS_PER_TABLE }, (_, index) => tableRef.collection("hands").doc(String(index + 1))));
        if (!handSnaps.some((snap) => snap.get("isDone") === true)) {
            transaction.update(tableRef, { matchTimeChecked: true });
            return false;
        }
        await (0, tableManagerService_1.saveTableStateInTransaction)(transaction, {
            tournamentId,
            roundId,
            tableId,
            expectedVersion: Number(tableSnap.get("version") ?? 0),
            tablePatch: { isCompleted: true, matchTimeChecked: true },
            handPatches: [],
            actor: SYSTEM_ACTOR,
        });
        return true;
    });
}
/**
 * Marks as completed the tables of one round that have played hands, when the match time of the round is over.
 * A task runs it at the end of the round. It checks the time again, so a task made before an edit of the
 * schedule does nothing. A tournament without time zone is skipped, because the server cannot know the local
 * time of its rounds.
 */
async function closeRoundTables(tournamentId, roundId, nowMs = Date.now()) {
    const result = { tournaments: 0, closed: 0 };
    const tournament = await firebase_1.db.doc(`tournaments/${tournamentId}`).get();
    if (!tournament.exists || tournament.get("isCompleted") === true)
        return result;
    if (!overRounds(tournament.data() ?? {}, nowMs).includes(roundId))
        return result;
    const started = await tournament.ref.collection("tables")
        .where("roundId", "==", roundId)
        .where("isCompleted", "==", false)
        .where("hasProgress", "==", true)
        .get();
    const candidates = started.docs.filter((doc) => doc.get("matchTimeChecked") !== true);
    if (candidates.length === 0)
        return result;
    result.tournaments = 1;
    for (const doc of candidates) {
        if (await closeTable(tournamentId, roundId, Number(doc.get("tableId"))))
            result.closed += 1;
    }
    return result;
}
//# sourceMappingURL=matchTimeCloseService.js.map