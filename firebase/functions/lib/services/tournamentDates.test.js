"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentDates_1 = require("./tournamentDates");
(0, node_test_1.default)("accepts valid single-day and multi-day periods", () => {
    strict_1.default.equal((0, tournamentDates_1.isValidIsoDateRange)("2026-09-23", "2026-09-23"), true);
    strict_1.default.equal((0, tournamentDates_1.isValidIsoDateRange)("2026-09-23", "2026-09-26"), true);
});
(0, node_test_1.default)("rejects invalid dates and reversed periods", () => {
    strict_1.default.equal((0, tournamentDates_1.isValidIsoDate)("2026-02-29"), false);
    strict_1.default.equal((0, tournamentDates_1.isValidIsoDate)("2028-02-29"), true);
    strict_1.default.equal((0, tournamentDates_1.isValidIsoDateRange)("2026-09-26", "2026-09-23"), false);
});
//# sourceMappingURL=tournamentDates.test.js.map