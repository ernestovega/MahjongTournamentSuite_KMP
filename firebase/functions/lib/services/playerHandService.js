"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseHandInput = parseHandInput;
exports.previewHand = previewHand;
exports.recordHand = recordHand;
exports.parseSeatAssignment = parseSeatAssignment;
exports.assignSeats = assignSeats;
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const handScoring_1 = require("./handScoring");
const playerAccountsService_1 = require("./playerAccountsService");
const tableManagerService_1 = require("./tableManagerService");
/** Parses the body of a request. It accepts only the fields that a player may send. */
function parseHandInput(body) {
    const data = (body != null && typeof body === "object") ? body : {};
    const input = {
        playerWinnerId: String(data.playerWinnerId ?? "").trim(),
        playerLooserId: String(data.playerLooserId ?? "").trim(),
        handScore: String(data.handScore ?? "").trim(),
        isChickenHand: data.isChickenHand === true,
    };
    if (input.playerLooserId === "-")
        input.playerLooserId = "";
    if (!input.playerWinnerId)
        throw (0, httpError_1.badRequest)("A winner is required");
    if ((0, handScoring_1.isResultSelectionInvalid)({ ...input }))
        throw (0, httpError_1.badRequest)("The hand result is not valid");
    if (input.isChickenHand && input.playerLooserId === "")
        throw (0, httpError_1.badRequest)("A chicken hand needs a loser");
    return input;
}
async function loadFor(key, uid, read, readAll) {
    const registration = await (0, playerAccountsService_1.getPlayerRegistration)(uid, key.tournamentId);
    if (!registration)
        throw (0, httpError_1.forbidden)("You are not registered in this tournament");
    const tableRef = firebase_1.db.doc(`tournaments/${key.tournamentId}/tables/${key.roundId}_${key.tableId}`);
    const [tableSnap, handSnaps] = await Promise.all([
        read.get(tableRef),
        readAll(Array.from({ length: tableManagerService_1.HANDS_PER_TABLE }, (_, i) => tableRef.collection("hands").doc(String(i + 1)))),
    ]);
    if (!tableSnap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    const table = tableSnap.data() ?? {};
    const seats = (0, handScoring_1.seatIdsOf)(table);
    if (Boolean(table.isCompleted))
        throw (0, httpError_1.conflict)("This table is already completed");
    if (Boolean(table.useTotalsOnly ?? false))
        throw (0, httpError_1.conflict)("This table uses totals only. An editor enters its results");
    if (seats == null)
        throw (0, httpError_1.conflict)("The seats of this table are not assigned yet");
    if (!seats.includes(String(registration.playerId)))
        throw (0, httpError_1.forbidden)("You do not play at this table");
    const hands = (0, tableManagerService_1.handsFromData)(handSnaps.map((snap) => snap.data() ?? {}));
    return { registration, table, seats, hands };
}
function buildPatch(table, seats, hands, input) {
    const next = hands.find((hand) => !hand.isDone);
    if (!next)
        throw (0, httpError_1.conflict)("All hands of this table are done");
    if (!seats.includes(input.playerWinnerId))
        throw (0, httpError_1.badRequest)("The winner does not play at this table");
    if (input.playerLooserId !== "" && !seats.includes(input.playerLooserId)) {
        throw (0, httpError_1.badRequest)("The loser does not play at this table");
    }
    if (input.playerLooserId === input.playerWinnerId)
        throw (0, httpError_1.badRequest)("The winner and the loser must differ");
    const handPatch = { ...input, isDone: true };
    const handsAfter = hands.map((hand) => (hand.handId === next.handId ? { ...hand, ...handPatch } : hand));
    const handScores = (0, handScoring_1.handSeatScores)(handsAfter.find((hand) => hand.handId === next.handId), seats);
    if (handScores == null)
        throw (0, httpError_1.badRequest)("The hand result is not valid");
    return { next, handPatch, totals: (0, handScoring_1.tableTotalsPatch)(table, handsAfter), handScores };
}
/** Calculates what the table would look like after the hand. It writes nothing. */
async function previewHand(params) {
    const { table, seats, hands } = await loadFor(params, params.uid, { get: (ref) => ref.get() }, (refs) => firebase_1.db.getAll(...refs));
    const { next, totals, handScores } = buildPatch(table, seats, hands, params.input);
    return {
        handId: next.handId,
        seats,
        handScores,
        tableScores: [totals.playerEastScore, totals.playerSouthScore, totals.playerWestScore, totals.playerNorthScore],
        tablePoints: [totals.playerEastPoints, totals.playerSouthPoints, totals.playerWestPoints, totals.playerNorthPoints],
        tableVersion: Number(table.version ?? 0),
    };
}
/** Writes the hand and the new table totals. It refuses when the table changed after the preview. */
async function recordHand(params) {
    try {
        return await firebase_1.db.runTransaction(async (transaction) => {
            const { registration, table, seats, hands } = await loadFor(params, params.uid, { get: (ref) => transaction.get(ref) }, (refs) => transaction.getAll(...refs));
            const version = Number(table.version ?? 0);
            if (version !== params.expectedVersion) {
                throw (0, httpError_1.conflict)("The table changed after the review. Check the new totals and confirm again");
            }
            const { next, handPatch, totals, handScores } = buildPatch(table, seats, hands, params.input);
            const actor = { uid: params.uid, name: `Player ${registration.emaId}` };
            await (0, tableManagerService_1.saveTableStateInTransaction)(transaction, {
                tournamentId: params.tournamentId,
                roundId: params.roundId,
                tableId: params.tableId,
                expectedVersion: version,
                tablePatch: totals,
                handPatches: [{ handId: next.handId, patch: handPatch }],
                actor,
            });
            return {
                handId: next.handId,
                seats,
                handScores,
                tableScores: [totals.playerEastScore, totals.playerSouthScore, totals.playerWestScore, totals.playerNorthScore],
                tablePoints: [totals.playerEastPoints, totals.playerSouthPoints, totals.playerWestPoints, totals.playerNorthPoints],
                tableVersion: version + 1,
            };
        });
    }
    catch (error) {
        if (error instanceof tableManagerService_1.StaleTableVersion)
            throw (0, httpError_1.conflict)("The table changed. Review the hand again");
        throw error;
    }
}
/** Parses the body of a seat assignment. */
function parseSeatAssignment(body) {
    const data = (body != null && typeof body === "object") ? body : {};
    const text = (value) => String(value ?? "").trim();
    const seats = {
        east: text(data.playerEastId),
        south: text(data.playerSouthId),
        west: text(data.playerWestId),
        north: text(data.playerNorthId),
    };
    if (Object.values(seats).some((id) => id === ""))
        throw (0, httpError_1.badRequest)("Every seat needs a player");
    if (new Set(Object.values(seats)).size !== 4)
        throw (0, httpError_1.badRequest)("A player can take only one seat");
    return seats;
}
/**
 * A player of the table assigns the four seats. It is allowed until the first hand is saved.
 */
async function assignSeats(params) {
    const registration = await (0, playerAccountsService_1.getPlayerRegistration)(params.uid, params.tournamentId);
    if (!registration)
        throw (0, httpError_1.forbidden)("You are not registered in this tournament");
    const tableRef = firebase_1.db.doc(`tournaments/${params.tournamentId}/tables/${params.roundId}_${params.tableId}`);
    try {
        return await firebase_1.db.runTransaction(async (transaction) => {
            const [tableSnap, handSnaps] = await Promise.all([
                transaction.get(tableRef),
                transaction.getAll(...Array.from({ length: tableManagerService_1.HANDS_PER_TABLE }, (_, i) => tableRef.collection("hands").doc(String(i + 1)))),
            ]);
            if (!tableSnap.exists)
                throw (0, httpError_1.notFound)("Table not found");
            const table = tableSnap.data() ?? {};
            const playerIds = (table.playerIds ?? []).map(String);
            if (!playerIds.includes(String(registration.playerId)))
                throw (0, httpError_1.forbidden)("You do not play at this table");
            if (Boolean(table.isCompleted))
                throw (0, httpError_1.conflict)("This table is already completed");
            if (handSnaps.some((snap) => Boolean(snap.get("isDone")))) {
                throw (0, httpError_1.conflict)("A hand is already saved. A staff member changes the seats now");
            }
            // A staff member may have entered totals already. The seats then stay with the staff.
            const hasManualTotals = ["playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
                "manualPlayerEastScore", "manualPlayerSouthScore", "manualPlayerWestScore", "manualPlayerNorthScore"]
                .some((field) => String(table[field] ?? "").trim() !== "");
            if (hasManualTotals)
                throw (0, httpError_1.conflict)("A staff member already entered totals for this table");
            const chosen = [params.seats.east, params.seats.south, params.seats.west, params.seats.north];
            if (chosen.some((id) => !playerIds.includes(id)))
                throw (0, httpError_1.badRequest)("A seat has a player who is not at this table");
            const version = Number(table.version ?? 0);
            if (version !== params.expectedVersion) {
                throw (0, httpError_1.conflict)("The table changed. Check the seats again");
            }
            await (0, tableManagerService_1.saveTableStateInTransaction)(transaction, {
                tournamentId: params.tournamentId,
                roundId: params.roundId,
                tableId: params.tableId,
                expectedVersion: version,
                tablePatch: {
                    playerEastId: params.seats.east,
                    playerSouthId: params.seats.south,
                    playerWestId: params.seats.west,
                    playerNorthId: params.seats.north,
                },
                handPatches: [],
                actor: { uid: params.uid, name: `Player ${registration.emaId}` },
            });
            return { seats: chosen, tableVersion: version + 1 };
        });
    }
    catch (error) {
        if (error instanceof tableManagerService_1.StaleTableVersion)
            throw (0, httpError_1.conflict)("The table changed. Check the seats again");
        throw error;
    }
}
//# sourceMappingURL=playerHandService.js.map