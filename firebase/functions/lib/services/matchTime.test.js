"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const matchTime_1 = require("./matchTime");
const matchTimeTaskService_1 = require("./matchTimeTaskService");
(0, node_test_1.default)("converts a local time to an instant, also near summer time", () => {
    // Madrid is UTC+2 on 2026-10-10 and UTC+1 on 2026-12-10.
    strict_1.default.equal((0, matchTime_1.zonedEpochMs)("2026-10-10", "10:00", "Europe/Madrid"), Date.UTC(2026, 9, 10, 8, 0));
    strict_1.default.equal((0, matchTime_1.zonedEpochMs)("2026-12-10", "10:00", "Europe/Madrid"), Date.UTC(2026, 11, 10, 9, 0));
    strict_1.default.equal((0, matchTime_1.zonedEpochMs)("2026-10-10", "10:00", "UTC"), Date.UTC(2026, 9, 10, 10, 0));
    strict_1.default.equal((0, matchTime_1.zonedEpochMs)("bad", "10:00", "UTC"), null);
});
(0, node_test_1.default)("time zone and extra minutes are validated", () => {
    strict_1.default.equal((0, matchTime_1.normalizeTimeZone)(""), null);
    strict_1.default.equal((0, matchTime_1.normalizeTimeZone)(" Europe/Madrid "), "Europe/Madrid");
    strict_1.default.throws(() => (0, matchTime_1.normalizeTimeZone)("Mars/Base"));
    strict_1.default.equal((0, matchTime_1.normalizeMatchExtraMinutes)(undefined), 15);
    strict_1.default.throws(() => (0, matchTime_1.normalizeMatchExtraMinutes)(0));
});
(0, node_test_1.default)("the phase follows the start, the duration and the extra time", () => {
    const schedule = { date: "2026-10-10", startTime: "10:00" };
    const base = { timeZone: "Europe/Madrid", durationMinutes: 115, extraMinutes: 15 };
    const at = (time) => (0, matchTime_1.zonedEpochMs)("2026-10-10", time, "Europe/Madrid");
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)(schedule, { ...base, endMode: "STOP" }, at("09:59")), "NOT_STARTED");
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)(schedule, { ...base, endMode: "STOP" }, at("11:54")), "PLAYING");
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)(schedule, { ...base, endMode: "STOP" }, at("11:55")), "OVER");
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)(schedule, { ...base, endMode: "FINISH_HAND" }, at("11:55")), "EXTRA_TIME");
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)(schedule, { ...base, endMode: "FINISH_HAND" }, at("12:09")), "EXTRA_TIME");
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)(schedule, { ...base, endMode: "FINISH_HAND" }, at("12:10")), "OVER");
});
(0, node_test_1.default)("the phase is unknown without time zone or date", () => {
    const rules = { timeZone: null, durationMinutes: 120, extraMinutes: 15, endMode: "STOP" };
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)({ date: "2026-10-10", startTime: "10:00" }, rules, 0), null);
    strict_1.default.equal((0, matchTime_1.matchPhaseAt)({ date: null, startTime: null }, { ...rules, timeZone: "UTC" }, 0), null);
});
(0, node_test_1.default)("one close task is planned for each round whose end is in the future", () => {
    const tournament = {
        numRounds: 3,
        timeZone: "UTC",
        matchDurationMinutes: 120,
        matchEndMode: "FINISH_HAND",
        matchExtraMinutes: 15,
        roundSchedules: [
            { roundId: 1, date: "2026-10-10", startTime: "10:00" },
            { roundId: 2, date: "2026-10-10", startTime: "14:00" },
        ],
    };
    const now = Date.UTC(2026, 9, 10, 12, 30);
    const tasks = (0, matchTimeTaskService_1.roundCloseTasks)("t1", tournament, now);
    // Round 1 ends at 12:15, which is past. Round 2 ends at 16:15. Round 3 has no date.
    strict_1.default.deepEqual(tasks.map((task) => [task.roundId, task.at.getTime()]), [[2, Date.UTC(2026, 9, 10, 16, 15)]]);
    strict_1.default.equal(tasks[0].id, `t1_r2_${Date.UTC(2026, 9, 10, 16, 15)}`);
    strict_1.default.deepEqual((0, matchTimeTaskService_1.roundCloseTasks)("t1", { ...tournament, timeZone: null }, now), []);
});
(0, node_test_1.default)("a round that ends more than 29 days ahead gets no task yet", () => {
    const tournament = {
        numRounds: 1,
        timeZone: "UTC",
        matchDurationMinutes: 120,
        matchEndMode: "STOP",
        roundSchedules: [{ roundId: 1, date: "2026-12-12", startTime: "10:00" }],
    };
    strict_1.default.deepEqual((0, matchTimeTaskService_1.roundCloseTasks)("t1", tournament, Date.UTC(2026, 9, 23)), []);
    strict_1.default.equal((0, matchTimeTaskService_1.roundCloseTasks)("t1", tournament, Date.UTC(2026, 10, 20)).length, 1);
});
//# sourceMappingURL=matchTime.test.js.map