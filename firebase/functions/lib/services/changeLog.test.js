"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const changeLog_1 = require("./changeLog");
const actor = { uid: "u1", name: "Ana" };
(0, node_test_1.default)("actor comes from the verified token and falls back to the e-mail", () => {
    strict_1.default.deepEqual((0, changeLog_1.actorFromToken)({ uid: "u1", name: " Ana " }), { uid: "u1", name: "Ana" });
    strict_1.default.deepEqual((0, changeLog_1.actorFromToken)({ uid: "u1", email: "ana@example.com" }), { uid: "u1", name: "ana@example.com" });
    strict_1.default.deepEqual((0, changeLog_1.actorFromToken)({ uid: "u1" }), { uid: "u1", name: null });
    strict_1.default.equal((0, changeLog_1.actorFromToken)(undefined), null);
    strict_1.default.equal((0, changeLog_1.actorFromToken)({ name: "No uid" }), null);
});
(0, node_test_1.default)("save entry lists only the values that changed", () => {
    const entry = (0, changeLog_1.buildSaveChangeEntry)({
        roundId: 2,
        tableId: 5,
        version: 7,
        actor,
        tableBefore: { playerEastId: "1", isCompleted: false },
        tablePatch: { playerEastId: "1", isCompleted: true, manualPlayerEastScore: "10" },
        handsBefore: new Map([["3", { handScore: "8", isDone: false }]]),
        handPatches: [{ handId: 3, patch: { handScore: "8", isDone: true } }],
    });
    strict_1.default.deepEqual(entry.tableChanges, [
        { field: "isCompleted", from: false, to: true },
        { field: "manualPlayerEastScore", from: null, to: "10" },
    ]);
    strict_1.default.deepEqual(entry.handChanges, [{ handId: 3, field: "isDone", from: false, to: true }]);
    strict_1.default.equal(entry.version, 7);
    strict_1.default.equal(entry.kind, "save");
    strict_1.default.equal(entry.uid, "u1");
});
(0, node_test_1.default)("an empty value and a missing value count as the same", () => {
    const entry = (0, changeLog_1.buildSaveChangeEntry)({
        roundId: 1, tableId: 1, version: 1, actor,
        tableBefore: {}, tablePatch: { playerEastId: "" },
        handsBefore: new Map(), handPatches: [],
    });
    strict_1.default.deepEqual(entry.tableChanges, []);
});
(0, node_test_1.default)("derived totals are not logged", () => {
    const entry = (0, changeLog_1.buildSaveChangeEntry)({
        roundId: 1, tableId: 1, version: 1, actor,
        tableBefore: {}, tablePatch: { playerEastScore: "32", playerNorthPoints: "1" },
        handsBefore: new Map(), handPatches: [],
    });
    strict_1.default.deepEqual(entry.tableChanges, []);
});
(0, node_test_1.default)("hand changes are ordered by hand and keep the field order", () => {
    const entry = (0, changeLog_1.buildSaveChangeEntry)({
        roundId: 1, tableId: 1, version: 1, actor,
        tableBefore: {}, tablePatch: {},
        handsBefore: new Map(),
        handPatches: [
            { handId: 9, patch: { handScore: "8" } },
            { handId: 2, patch: { handScore: "12", isDone: true } },
        ],
    });
    strict_1.default.deepEqual(entry.handChanges.map((change) => `${change.handId}:${change.field}`), ["2:handScore", "2:isDone", "9:handScore"]);
});
(0, node_test_1.default)("entry without a user has null uid and name", () => {
    const entry = (0, changeLog_1.buildSaveChangeEntry)({
        roundId: 1, tableId: 1, version: 1, actor: null,
        tableBefore: {}, tablePatch: { playerEastId: "1" }, handsBefore: new Map(), handPatches: [],
    });
    strict_1.default.equal(entry.uid, null);
    strict_1.default.equal(entry.name, null);
});
(0, node_test_1.default)("reset entry has no field lists", () => {
    const entry = (0, changeLog_1.buildResetChangeEntry)({ roundId: 3, tableId: 4, version: 9, actor });
    strict_1.default.deepEqual(entry, {
        kind: "reset", roundId: 3, tableId: 4, version: 9, uid: "u1", name: "Ana", tableChanges: [], handChanges: [],
    });
});
//# sourceMappingURL=changeLog.test.js.map