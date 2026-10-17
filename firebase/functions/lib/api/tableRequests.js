"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseTablePatch = parseTablePatch;
exports.parseHandPatch = parseHandPatch;
exports.parseSaveTableRequest = parseSaveTableRequest;
const httpError_1 = require("./httpError");
const tableManagerService_1 = require("../services/tableManagerService");
const TABLE_STRING_FIELDS = [
    "playerEastId", "playerSouthId", "playerWestId", "playerNorthId",
    "playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
    "manualPlayerEastScore", "manualPlayerSouthScore", "manualPlayerWestScore", "manualPlayerNorthScore",
];
const TABLE_BOOLEAN_FIELDS = ["isCompleted", "useTotalsOnly"];
const HAND_STRING_FIELDS = [
    "playerWinnerId", "playerLooserId", "handScore", "playerEastPenalty",
    "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty",
];
const HAND_BOOLEAN_FIELDS = ["isChickenHand", "isDone"];
function parsePatch(value, stringFields, booleanFields) {
    const source = value != null && typeof value === "object" ? value : {};
    const result = {};
    for (const field of stringFields) {
        if (!(field in source))
            continue;
        if (typeof source[field] !== "string")
            throw (0, httpError_1.badRequest)(`${field} must be a string`);
        result[field] = source[field];
    }
    for (const field of booleanFields) {
        if (!(field in source))
            continue;
        if (typeof source[field] !== "boolean")
            throw (0, httpError_1.badRequest)(`${field} must be a boolean`);
        result[field] = source[field];
    }
    return result;
}
function parseTablePatch(value) {
    return parsePatch(value, TABLE_STRING_FIELDS, TABLE_BOOLEAN_FIELDS);
}
function parseHandPatch(value) {
    return parsePatch(value, HAND_STRING_FIELDS, HAND_BOOLEAN_FIELDS);
}
/** Checks the URL and the body of a table save. Every problem becomes a 400 answer. */
function parseSaveTableRequest(params, body) {
    const roundId = Number(params.roundId);
    const tableId = Number(params.tableId);
    if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
        throw (0, httpError_1.badRequest)("roundId and tableId must be positive integers");
    }
    const source = body != null && typeof body === "object" ? body : {};
    // Number(null), Number("") and Number(false) are 0. Only a real number is a version.
    const expectedVersion = source.expectedVersion;
    if (typeof expectedVersion !== "number" || !Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
        throw (0, httpError_1.badRequest)("expectedVersion must be a non-negative integer");
    }
    const tablePatch = parseTablePatch(source.tablePatch);
    const handPatchesRaw = Array.isArray(source.handPatches) ? source.handPatches : [];
    const handPatches = handPatchesRaw.map((value) => {
        const item = value != null && typeof value === "object" ? value : {};
        const handId = item.handId;
        if (typeof handId !== "number" || !Number.isInteger(handId) || handId <= 0 || handId > tableManagerService_1.HANDS_PER_TABLE) {
            throw (0, httpError_1.badRequest)(`handId must be an integer from 1 to ${tableManagerService_1.HANDS_PER_TABLE}`);
        }
        return { handId, patch: parseHandPatch(item.patch) };
    });
    if (new Set(handPatches.map((item) => item.handId)).size !== handPatches.length) {
        throw (0, httpError_1.badRequest)("Each hand can occur only once");
    }
    return { roundId, tableId, expectedVersion, tablePatch, handPatches };
}
//# sourceMappingURL=tableRequests.js.map