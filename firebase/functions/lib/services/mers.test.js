"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const mers_1 = require("./mers");
(0, node_test_1.default)("inclusiveDayCount counts both event dates", () => {
    strict_1.default.equal((0, mers_1.inclusiveDayCount)("2025-02-01", "2025-02-02"), 2);
});
(0, node_test_1.default)("calculateMers adds player and country bonuses", () => {
    strict_1.default.equal((0, mers_1.calculateMers)({
        startDate: "2025-02-01",
        endDate: "2025-02-02",
        participantCount: 100,
        representedCountries: ["ESP", "FRA", "ITA", "NED", "SUI", "GER"],
    }), 3.5);
});
//# sourceMappingURL=mers.test.js.map