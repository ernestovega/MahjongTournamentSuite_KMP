"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentSchedule_1 = require("./tournamentSchedule");
(0, node_test_1.default)("normalizes partial round schedules and keeps one row per round", () => {
    const schedules = (0, tournamentSchedule_1.normalizeRoundSchedules)([{ roundId: 2, date: "2026-10-03", startTime: "09:30" }], 3, "2026-10-02", "2026-10-04");
    strict_1.default.deepEqual(schedules, [
        { roundId: 1, date: null, startTime: null },
        { roundId: 2, date: "2026-10-03", startTime: "09:30" },
        { roundId: 3, date: null, startTime: null },
    ]);
});
(0, node_test_1.default)("rejects invalid round dates and times", () => {
    strict_1.default.throws(() => (0, tournamentSchedule_1.normalizeRoundSchedules)([{ roundId: 1, date: "2026-10-05", startTime: "25:00" }], 1, "2026-10-02", "2026-10-04"), /inside the tournament date range/);
    strict_1.default.throws(() => (0, tournamentSchedule_1.normalizeRoundSchedules)([{ roundId: 1, date: "2026-10-02", startTime: "25:00" }], 1, "2026-10-02", "2026-10-04"), /HH:mm/);
});
(0, node_test_1.default)("keeps partial agenda items and omits rows without a title", () => {
    const agenda = (0, tournamentSchedule_1.normalizeAgendaItems)([
        { title: " Registration ", date: "2026-10-02", startTime: "08:30", endTime: "09:00" },
        { title: "Awards" },
        { title: "", date: "2026-10-02" },
    ], "2026-10-02", "2026-10-04");
    strict_1.default.deepEqual(agenda, [
        { title: "Registration", date: "2026-10-02", startTime: "08:30", endTime: "09:00" },
        { title: "Awards", date: null, startTime: null, endTime: null },
    ]);
});
(0, node_test_1.default)("rejects agenda items that end before they start", () => {
    strict_1.default.throws(() => (0, tournamentSchedule_1.normalizeAgendaItems)([{ title: "Lunch", startTime: "14:00", endTime: "13:00" }], "2026-10-02", "2026-10-04"), /must not be before/);
});
//# sourceMappingURL=tournamentSchedule.test.js.map