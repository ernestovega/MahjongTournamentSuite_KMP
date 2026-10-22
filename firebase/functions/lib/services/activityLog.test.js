"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const activityLog_1 = require("./activityLog");
(0, node_test_1.default)("details drop e-mail addresses, image data and tokens", () => {
    strict_1.default.deepEqual((0, activityLog_1.sanitizeDetails)({ name: "Open", contactEmail: "a@b.c", associationLogoDataBase64: "AAAA", token: "x", rounds: 5 }), { name: "Open", rounds: 5 });
});
(0, node_test_1.default)("details cut long text and nested data", () => {
    const clean = (0, activityLog_1.sanitizeDetails)({ note: "x".repeat(300), list: [{ email: "a@b.c", id: 1 }], deep: { a: { b: { c: { d: 1 } } } } });
    strict_1.default.equal(clean.note.length, 201);
    strict_1.default.deepEqual(clean.list, [{ id: 1 }]);
    strict_1.default.deepEqual(clean.deep, { a: { b: { c: null } } });
});
(0, node_test_1.default)("an entry keeps who, what and where", () => {
    const entry = (0, activityLog_1.buildActivityEntry)({
        action: "player.assign", actor: { uid: "u1", name: "Ana" }, tournamentId: "t1", target: "player 5", details: { emaId: "10990039" },
    });
    strict_1.default.deepEqual(entry, {
        action: "player.assign", uid: "u1", name: "Ana", tournamentId: "t1", target: "player 5", details: { emaId: "10990039" },
    });
    strict_1.default.equal((0, activityLog_1.buildActivityEntry)({ action: "user.create", actor: null }).uid, null);
});
//# sourceMappingURL=activityLog.test.js.map