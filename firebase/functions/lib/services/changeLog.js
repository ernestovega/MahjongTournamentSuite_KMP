"use strict";
/**
 * The change log keeps one record for each table save or reset: who changed what, and when.
 * It is append-only. The app does not show it yet. It also answers "who edited this table last".
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.actorFromToken = actorFromToken;
exports.buildSaveChangeEntry = buildSaveChangeEntry;
exports.buildResetChangeEntry = buildResetChangeEntry;
/** The totals follow from the hands. A person did not type them, so the log leaves them out. */
const DERIVED_FIELDS = new Set([
    "playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
    "playerEastPoints", "playerSouthPoints", "playerWestPoints", "playerNorthPoints",
]);
function sameValue(a, b) {
    return (a ?? "") === (b ?? "");
}
/** Reads the acting user from the verified token. A missing token gives an anonymous entry. */
function actorFromToken(token) {
    if (!token?.uid)
        return null;
    return { uid: token.uid, name: token.name?.trim() || token.email?.trim() || null };
}
function buildSaveChangeEntry(params) {
    const tableChanges = Object.entries(params.tablePatch)
        .filter(([field]) => !DERIVED_FIELDS.has(field))
        .filter(([field, to]) => !sameValue(params.tableBefore[field], to))
        .map(([field, to]) => ({ field, from: params.tableBefore[field] ?? null, to: to ?? null }));
    const handChanges = [];
    for (const { handId, patch } of [...params.handPatches].sort((a, b) => a.handId - b.handId)) {
        const before = params.handsBefore.get(String(handId)) ?? {};
        for (const [field, to] of Object.entries(patch)) {
            if (sameValue(before[field], to))
                continue;
            handChanges.push({ handId, field, from: before[field] ?? null, to: to ?? null });
        }
    }
    return {
        kind: "save",
        roundId: params.roundId,
        tableId: params.tableId,
        version: params.version,
        uid: params.actor?.uid ?? null,
        name: params.actor?.name ?? null,
        tableChanges,
        handChanges,
    };
}
function buildResetChangeEntry(params) {
    return {
        kind: "reset",
        roundId: params.roundId,
        tableId: params.tableId,
        version: params.version,
        uid: params.actor?.uid ?? null,
        name: params.actor?.name ?? null,
        tableChanges: [],
        handChanges: [],
    };
}
//# sourceMappingURL=changeLog.js.map