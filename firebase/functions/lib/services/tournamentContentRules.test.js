"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentContentRules_1 = require("./tournamentContentRules");
(0, node_test_1.default)("allows an EMA assignment to an empty slot after progress starts", () => {
    strict_1.default.equal((0, tournamentContentRules_1.blocksAssignmentChangeAfterProgress)(null, "12345678", true), false);
});
(0, node_test_1.default)("blocks replacement or removal of an existing assignment after progress starts", () => {
    strict_1.default.equal((0, tournamentContentRules_1.blocksAssignmentChangeAfterProgress)("12345678", "87654321", true), true);
    strict_1.default.equal((0, tournamentContentRules_1.blocksAssignmentChangeAfterProgress)("12345678", null, true), true);
});
(0, node_test_1.default)("allows assignment changes before progress starts", () => {
    strict_1.default.equal((0, tournamentContentRules_1.blocksAssignmentChangeAfterProgress)("12345678", "87654321", false), false);
});
(0, node_test_1.default)("allows an unchanged assignment after progress starts", () => {
    strict_1.default.equal((0, tournamentContentRules_1.blocksAssignmentChangeAfterProgress)("12345678", "12345678", true), false);
});
(0, node_test_1.default)("accepts four whole-number scores with a non-zero total", () => {
    strict_1.default.equal((0, tournamentContentRules_1.hasFourValidScores)([30000, 10000, -10000, -35000]), true);
});
(0, node_test_1.default)("rejects missing and non-whole-number scores", () => {
    strict_1.default.equal((0, tournamentContentRules_1.hasFourValidScores)([30000, 10000, -10000]), false);
    strict_1.default.equal((0, tournamentContentRules_1.hasFourValidScores)([30000, 10000, -10000, "1.5"]), false);
});
(0, node_test_1.default)("requires four numeric table-point values that total seven", () => {
    strict_1.default.equal((0, tournamentContentRules_1.hasValidTablePoints)([4, 2, 1, 0]), true);
    strict_1.default.equal((0, tournamentContentRules_1.hasValidTablePoints)([4, 2, 1, 1]), false);
    strict_1.default.equal((0, tournamentContentRules_1.hasValidTablePoints)([4, 2, 1]), false);
});
//# sourceMappingURL=tournamentContentRules.test.js.map