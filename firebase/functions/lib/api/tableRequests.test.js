"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const httpError_1 = require("./httpError");
const tableRequests_1 = require("./tableRequests");
const params = { roundId: "2", tableId: "5" };
function rejects(action, pattern) {
    strict_1.default.throws(action, (error) => error instanceof httpError_1.HttpError && error.status === 400 && pattern.test(error.message));
}
(0, node_test_1.default)("accepts a complete save request", () => {
    const request = (0, tableRequests_1.parseSaveTableRequest)(params, {
        expectedVersion: 3,
        tablePatch: { playerEastId: "4", isCompleted: true },
        handPatches: [{ handId: 2, patch: { handScore: "12", isDone: true } }],
    });
    strict_1.default.deepEqual(request, {
        roundId: 2,
        tableId: 5,
        expectedVersion: 3,
        tablePatch: { playerEastId: "4", isCompleted: true },
        handPatches: [{ handId: 2, patch: { handScore: "12", isDone: true } }],
    });
});
(0, node_test_1.default)("a missing patch means no change", () => {
    const request = (0, tableRequests_1.parseSaveTableRequest)(params, { expectedVersion: 0 });
    strict_1.default.deepEqual(request.tablePatch, {});
    strict_1.default.deepEqual(request.handPatches, []);
});
(0, node_test_1.default)("version zero is valid", () => {
    strict_1.default.equal((0, tableRequests_1.parseSaveTableRequest)(params, { expectedVersion: 0 }).expectedVersion, 0);
});
(0, node_test_1.default)("the version is required and must be a real non-negative integer", () => {
    for (const bad of [undefined, null, "", "3", false, true, [], {}, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
        rejects(() => (0, tableRequests_1.parseSaveTableRequest)(params, { expectedVersion: bad }), /expectedVersion/);
    }
    rejects(() => (0, tableRequests_1.parseSaveTableRequest)(params, undefined), /expectedVersion/);
    rejects(() => (0, tableRequests_1.parseSaveTableRequest)(params, "text"), /expectedVersion/);
});
(0, node_test_1.default)("round and table must be positive integers", () => {
    for (const bad of ["0", "-1", "1.5", "abc", undefined, ""]) {
        rejects(() => (0, tableRequests_1.parseSaveTableRequest)({ roundId: bad, tableId: "1" }, { expectedVersion: 0 }), /roundId and tableId/);
        rejects(() => (0, tableRequests_1.parseSaveTableRequest)({ roundId: "1", tableId: bad }, { expectedVersion: 0 }), /roundId and tableId/);
    }
});
(0, node_test_1.default)("hand ids go from 1 to 16 and must be numbers", () => {
    for (const bad of [0, 17, -1, 1.5, "2", null, undefined]) {
        rejects(() => (0, tableRequests_1.parseSaveTableRequest)(params, { expectedVersion: 0, handPatches: [{ handId: bad, patch: {} }] }), /handId/);
    }
    strict_1.default.equal((0, tableRequests_1.parseSaveTableRequest)(params, { expectedVersion: 0, handPatches: [{ handId: 16, patch: {} }] }).handPatches.length, 1);
});
(0, node_test_1.default)("a hand can occur only once", () => {
    rejects(() => (0, tableRequests_1.parseSaveTableRequest)(params, {
        expectedVersion: 0,
        handPatches: [{ handId: 3, patch: {} }, { handId: 3, patch: {} }],
    }), /only once/);
});
(0, node_test_1.default)("table fields must have the right type", () => {
    rejects(() => (0, tableRequests_1.parseTablePatch)({ playerEastId: 4 }), /playerEastId must be a string/);
    rejects(() => (0, tableRequests_1.parseTablePatch)({ isCompleted: "yes" }), /isCompleted must be a boolean/);
    rejects(() => (0, tableRequests_1.parseHandPatch)({ handScore: 8 }), /handScore must be a string/);
    rejects(() => (0, tableRequests_1.parseHandPatch)({ isDone: 1 }), /isDone must be a boolean/);
});
(0, node_test_1.default)("unknown fields are dropped, so a client cannot write the version or the summary", () => {
    strict_1.default.deepEqual((0, tableRequests_1.parseTablePatch)({ version: 99, hasProgress: true, completionStatus: "completed", playerEastId: "1" }), { playerEastId: "1" });
    strict_1.default.deepEqual((0, tableRequests_1.parseHandPatch)({ handId: 5, createdAt: "x", handScore: "8" }), { handScore: "8" });
});
(0, node_test_1.default)("a body that is not an object gives an empty patch", () => {
    strict_1.default.deepEqual((0, tableRequests_1.parseTablePatch)(null), {});
    strict_1.default.deepEqual((0, tableRequests_1.parseTablePatch)("text"), {});
    strict_1.default.deepEqual((0, tableRequests_1.parseHandPatch)(undefined), {});
});
//# sourceMappingURL=tableRequests.test.js.map