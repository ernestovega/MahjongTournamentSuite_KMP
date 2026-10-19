"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseHandInput = parseHandInput;
exports.previewHand = previewHand;
exports.recordHand = recordHand;
exports.parsePenaltyInput = parsePenaltyInput;
exports.addPenalties = addPenalties;
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const handScoring_1 = require("./handScoring");
const playerAccountsService_1 = require("./playerAccountsService");
const tableManagerService_1 = require("./tableManagerService");
const MAX_PENALTIES_PER_HAND = 5;
/**
 * The penalties of one seat. Each ";" separated part belongs to one penalty, like the rows of the admin app.
 * A part is empty or a signed whole number.
 */
function penaltyParts(value) {
    const text = String(value ?? "").trim();
    if (text === "")
        return [];
    const parts = text.split(";").map((part) => part.trim());
    if (parts.length > MAX_PENALTIES_PER_HAND)
        throw (0, httpError_1.badRequest)("A hand has at most 5 penalties");
    return parts.map((part) => {
        if (part === "")
            return "";
        if (!/^[-+]?\d+$/.test(part))
            throw (0, httpError_1.badRequest)("A penalty must be a whole number");
        const number = Number(part);
        return number === 0 ? "" : String(number);
    });
}
/**
 * Checks the four seats together. Every penalty needs a penalized player with a negative value.
 * The other players can receive positive values from a shared penalty. Returns the normalized texts.
 */
function parsePenalties(values) {
    const seats = values.map(penaltyParts);
    const rows = Math.max(0, ...seats.map((parts) => parts.length));
    for (let row = 0; row < rows; row += 1) {
        const cells = seats.map((parts) => parts[row] ?? "");
        if (cells.some((cell) => cell !== "") && !cells.some((cell) => cell.startsWith("-"))) {
            throw (0, httpError_1.badRequest)("A penalty needs at least one player with a negative value");
        }
    }
    const texts = seats.map((parts) => {
        const padded = Array.from({ length: rows }, (_, row) => parts[row] ?? "");
        return padded.some((cell) => cell !== "") ? padded.join(";") : "";
    });
    return texts;
}
/** Parses the body of a request. It accepts only the fields that a player may send. */
function parseHandInput(body) {
    const data = (body != null && typeof body === "object") ? body : {};
    const input = {
        playerWinnerId: String(data.playerWinnerId ?? "").trim(),
        playerLooserId: String(data.playerLooserId ?? "").trim(),
        handScore: String(data.handScore ?? "").trim(),
        isChickenHand: data.isChickenHand === true,
        isDraw: data.isDraw === true,
        isLastHand: data.isLastHand === true,
    };
    // A draw has no winner, no loser and the score 0. This is the same form that the editors use.
    if (input.isDraw)
        return { ...input, playerWinnerId: "", playerLooserId: "", handScore: "0", isChickenHand: false };
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
    if (!input.isDraw) {
        if (!seats.includes(input.playerWinnerId))
            throw (0, httpError_1.badRequest)("The winner does not play at this table");
        if (input.playerLooserId !== "" && !seats.includes(input.playerLooserId)) {
            throw (0, httpError_1.badRequest)("The loser does not play at this table");
        }
        if (input.playerLooserId === input.playerWinnerId)
            throw (0, httpError_1.badRequest)("The winner and the loser must differ");
    }
    const { isLastHand, isDraw, ...handFields } = input;
    const handPatch = { ...handFields, isDone: true };
    const handsAfter = hands.map((hand) => (hand.handId === next.handId ? { ...hand, ...handPatch } : hand));
    const handScores = (0, handScoring_1.handSeatScores)(handsAfter.find((hand) => hand.handId === next.handId), seats);
    if (handScores == null)
        throw (0, httpError_1.badRequest)("The hand result is not valid");
    // The last hand closes the table, like the Completed switch of an editor.
    const tableAfter = isLastHand ? { ...table, isCompleted: true } : table;
    const totals = (0, handScoring_1.tableTotalsPatch)(tableAfter, handsAfter);
    return { next, handPatch, totals, handScores, tablePatch: isLastHand ? { ...totals, isCompleted: true } : totals };
}
/** True when the table changed after the player looked at it. */
function isVersionConflict(error) {
    return error instanceof tableManagerService_1.StaleTableVersion
        || (error instanceof httpError_1.HttpError && error.status === 409 && error.message.startsWith("The table changed"));
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
            const { next, handPatch, totals, handScores, tablePatch } = buildPatch(table, seats, hands, params.input);
            const actor = { uid: params.uid, name: `Player ${registration.emaId}`, source: "player" };
            await (0, tableManagerService_1.saveTableStateInTransaction)(transaction, {
                tournamentId: params.tournamentId,
                roundId: params.roundId,
                tableId: params.tableId,
                expectedVersion: version,
                tablePatch,
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
        if (isVersionConflict(error)) {
            await (0, tableManagerService_1.logTableConflict)({ ...params, actor: { uid: params.uid, name: null, source: "player" } });
        }
        if (error instanceof tableManagerService_1.StaleTableVersion)
            throw (0, httpError_1.conflict)("The table changed. Review the hand again");
        throw error;
    }
}
/** Parses the penalties of a request. The four texts are in the order east, south, west, north. */
function parsePenaltyInput(body) {
    const data = (body != null && typeof body === "object") ? body : {};
    const texts = parsePenalties([data.playerEastPenalty, data.playerSouthPenalty, data.playerWestPenalty, data.playerNorthPenalty]);
    if (texts.every((text) => text === ""))
        throw (0, httpError_1.badRequest)("A penalty is required");
    return texts;
}
/** Adds the new penalty parts after the parts that the hand already has. Every seat keeps the same number of parts. */
function appendPenalties(current, added) {
    const before = current.map((text) => (text === "" ? [] : text.split(";")));
    const after = added.map((text) => (text === "" ? [] : text.split(";")));
    const existingRows = Math.max(0, ...before.map((parts) => parts.length));
    const addedRows = Math.max(0, ...after.map((parts) => parts.length));
    if (existingRows + addedRows > MAX_PENALTIES_PER_HAND)
        throw (0, httpError_1.conflict)("A hand has at most 5 penalties");
    return before.map((parts, seat) => {
        const merged = [
            ...Array.from({ length: existingRows }, (_, row) => parts[row] ?? ""),
            ...Array.from({ length: addedRows }, (_, row) => after[seat][row] ?? ""),
        ];
        return merged.some((cell) => cell !== "") ? merged.join(";") : "";
    });
}
/**
 * Adds penalties to the hand in play. The hand is not done, so the hand does not advance.
 * The penalties count in the table totals when the hand is saved.
 */
async function addPenalties(params) {
    try {
        return await firebase_1.db.runTransaction(async (transaction) => {
            const { registration, table, hands } = await loadFor(params, params.uid, { get: (ref) => transaction.get(ref) }, (refs) => transaction.getAll(...refs));
            const version = Number(table.version ?? 0);
            if (version !== params.expectedVersion) {
                throw (0, httpError_1.conflict)("The table changed. Check the table and add the penalty again");
            }
            const next = hands.find((hand) => !hand.isDone);
            if (!next)
                throw (0, httpError_1.conflict)("All hands of this table are done");
            const current = [next.playerEastPenalty, next.playerSouthPenalty, next.playerWestPenalty, next.playerNorthPenalty]
                .map((value) => String(value ?? "").trim());
            const merged = appendPenalties(current, params.penalties);
            await (0, tableManagerService_1.saveTableStateInTransaction)(transaction, {
                tournamentId: params.tournamentId,
                roundId: params.roundId,
                tableId: params.tableId,
                expectedVersion: version,
                tablePatch: {},
                handPatches: [{
                        handId: next.handId,
                        patch: {
                            playerEastPenalty: merged[0],
                            playerSouthPenalty: merged[1],
                            playerWestPenalty: merged[2],
                            playerNorthPenalty: merged[3],
                        },
                    }],
                actor: { uid: params.uid, name: `Player ${registration.emaId}`, source: "player" },
            });
            return { handId: next.handId, tableVersion: version + 1 };
        });
    }
    catch (error) {
        if (isVersionConflict(error)) {
            await (0, tableManagerService_1.logTableConflict)({ ...params, actor: { uid: params.uid, name: null, source: "player" } });
        }
        if (error instanceof tableManagerService_1.StaleTableVersion)
            throw (0, httpError_1.conflict)("The table changed. Check the table and add the penalty again");
        throw error;
    }
}
//# sourceMappingURL=playerHandService.js.map