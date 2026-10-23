"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const usageService_1 = require("./usageService");
const HOUR = 3600000;
const DAY = 24 * HOUR;
// Saturday 2026-10-10 09:00 UTC.
const base = Date.UTC(2026, 9, 10, 9, 0, 0);
const now = new Date(base + 5 * DAY);
function change(partial) {
    return {
        tournamentId: "t1", kind: "save", source: "editor", uid: "u1", name: "Ana",
        roundId: 1, tableId: 1, handChangeCount: 2, at: base, ...partial,
    };
}
function activity(partial) {
    return { action: "account.send", uid: "u1", name: "Ana", tournamentId: "t1", at: base, ...partial };
}
function tournament(partial = {}) {
    return {
        id: "t1", name: "Open", country: "ES", startDate: "2026-10-12", endDate: "2026-10-18", timeZone: "Europe/Madrid",
        createdAt: base - 20 * DAY, players: 8, isTeams: false, hasMatchTimer: true, hasAgenda: false, hasVenueMap: false,
        hasLogo: false, ...partial,
    };
}
function account(partial) {
    return { uid: "u1", name: "Ana", role: "EDITOR", disabled: false, createdAt: base - 40 * DAY, lastSeenAt: base, ...partial };
}
const empty = { tables: [], slots: [], activities: [], accounts: [], changes: [], tournaments: [] };
(0, node_test_1.default)("report counts saves by source, conflicts and resets", () => {
    const report = (0, usageService_1.buildUsageReport)({
        ...empty,
        now,
        days: 30,
        tournaments: [tournament()],
        changes: [
            change({}),
            change({ source: "player", uid: "p1", name: "Player 10", tableId: 2, at: base + 2 * HOUR }),
            change({ kind: "conflict", source: "player", uid: "p1" }),
            change({ kind: "reset" }),
        ],
        tables: [
            { tournamentId: "t1", isCompleted: true, useTotalsOnly: false, hasValidManualTotals: false, hasProgress: true },
            { tournamentId: "t1", isCompleted: true, useTotalsOnly: true, hasValidManualTotals: true, hasProgress: false },
        ],
        slots: [
            { tournamentId: "t1", invitationStatus: "ACCEPTED", hasAccount: true, isAssigned: true },
            { tournamentId: "t1", invitationStatus: "NONE", hasAccount: false, isAssigned: false },
        ],
    });
    strict_1.default.equal(report.current.saves, 2);
    strict_1.default.equal(report.current.playerSaves, 1);
    strict_1.default.equal(report.current.editorSaves, 1);
    strict_1.default.equal(report.current.conflicts, 1);
    strict_1.default.equal(report.current.resets, 1);
    strict_1.default.equal(report.current.activeUsers, 2);
    strict_1.default.equal(report.current.activeTournaments, 1);
    const item = report.tournaments[0];
    strict_1.default.equal(item.name, "Open");
    strict_1.default.equal(item.status, "LIVE");
    strict_1.default.equal(item.tablesTouched, 2);
    strict_1.default.equal(item.savesPerTouchedTable, 1);
    strict_1.default.equal(item.activeHours, 2);
    strict_1.default.equal(item.handsTables, 1);
    strict_1.default.equal(item.totalsOnlyTables, 1);
    strict_1.default.equal(item.manualTotalsTables, 1);
    strict_1.default.equal(item.slotsWithAccount, 1);
    strict_1.default.equal(item.invitationsSent, 1);
    strict_1.default.equal(item.invitationsAccepted, 1);
});
(0, node_test_1.default)("a change without a source counts as an editor save", () => {
    const report = (0, usageService_1.buildUsageReport)({ ...empty, now, days: 30, changes: [change({ source: null })] });
    strict_1.default.equal(report.current.editorSaves, 1);
});
(0, node_test_1.default)("changes of the system are not work of a user", () => {
    const report = (0, usageService_1.buildUsageReport)({ ...empty, now, days: 30, changes: [change({ source: "system", uid: null })] });
    strict_1.default.equal(report.current.saves, 0);
});
(0, node_test_1.default)("the previous period is the same length before the current one", () => {
    const report = (0, usageService_1.buildUsageReport)({
        ...empty,
        now,
        days: 7,
        changes: [change({ at: base }), change({ at: base - 6 * DAY }), change({ at: base - 20 * DAY })],
    });
    strict_1.default.equal(report.current.saves, 1);
    strict_1.default.equal(report.previous.saves, 1);
    strict_1.default.equal(report.daily.length, 7);
    strict_1.default.equal(report.daily.reduce((sum, day) => sum + day.saves, 0), 1);
});
(0, node_test_1.default)("the heatmap uses the time zone of the tournament", () => {
    const report = (0, usageService_1.buildUsageReport)({ ...empty, now, days: 30, tournaments: [tournament()], changes: [change({})] });
    // 09:00 UTC is 11:00 in Madrid in October. It is a Saturday.
    strict_1.default.equal(report.heatmap[5][11], 1);
    strict_1.default.deepEqual((0, usageService_1.localSlot)(base, "Not/AZone"), { weekday: 5, hour: 9 });
});
(0, node_test_1.default)("accounts get a status from their work and their last sign-in", () => {
    const report = (0, usageService_1.buildUsageReport)({
        ...empty,
        now,
        days: 30,
        changes: [change({})],
        accounts: [
            account({}),
            account({ uid: "u2", name: "Visitor", role: "PLAYER", lastSeenAt: base + 2 * DAY }),
            account({ uid: "u3", name: "Old", role: "PLAYER", lastSeenAt: base - 90 * DAY }),
            account({ uid: "u4", name: "Never", role: "PLAYER", lastSeenAt: null }),
            account({ uid: "u5", name: "Off", role: "ADMIN", disabled: true }),
        ],
    });
    const status = Object.fromEntries(report.users.map((user) => [user.uid, user.status]));
    strict_1.default.deepEqual(status, { u1: "ACTIVE", u2: "VISITOR", u3: "DORMANT", u4: "NEVER", u5: "DISABLED" });
    strict_1.default.equal(report.accounts.total, 5);
    strict_1.default.equal(report.accounts.active, 1);
    strict_1.default.equal(report.accounts.visitors, 1);
    strict_1.default.equal(report.accounts.dormant, 1);
    strict_1.default.equal(report.accounts.neverSignedIn, 1);
    strict_1.default.equal(report.accounts.disabled, 1);
    strict_1.default.equal(report.accounts.admins, 1);
    strict_1.default.equal(report.users[0].uid, "u1");
    strict_1.default.equal(report.users[0].saves, 1);
    strict_1.default.equal(report.users[0].lastActiveDaysAgo, 5);
});
(0, node_test_1.default)("features show what is used and what is not", () => {
    const report = (0, usageService_1.buildUsageReport)({
        ...empty,
        now,
        days: 30,
        tournaments: [tournament(), tournament({ id: "t2", hasMatchTimer: false, isTeams: true })],
        activities: [activity({}), activity({ action: "account.resend", uid: "u2" }), activity({ action: "team.update", at: base - 60 * DAY })],
    });
    const byKey = Object.fromEntries(report.features.map((feature) => [feature.key, feature]));
    strict_1.default.equal(byKey.invitations.uses, 2);
    strict_1.default.equal(byKey.invitations.users, 2);
    strict_1.default.equal(byKey.teams.uses, 1);
    strict_1.default.equal(byKey.teams.adoption, 0.5);
    strict_1.default.equal(byKey.matchTimer.adoption, 0.5);
    strict_1.default.equal(byKey.agenda.uses, 0);
    strict_1.default.equal(byKey.substitution.uses, 0);
    strict_1.default.equal(byKey.substitution.lastUsedDaysAgo, null);
});
(0, node_test_1.default)("the funnel counts tournaments that reach each step", () => {
    const report = (0, usageService_1.buildUsageReport)({
        ...empty,
        now,
        days: 30,
        tournaments: [tournament(), tournament({ id: "t2" })],
        changes: [change({})],
        tables: [{ tournamentId: "t1", isCompleted: true, useTotalsOnly: false, hasValidManualTotals: false, hasProgress: true }],
        slots: [{ tournamentId: "t1", invitationStatus: "INVITED", hasAccount: false, isAssigned: true }],
    });
    const counts = Object.fromEntries(report.funnel.map((step) => [step.key, step.count]));
    strict_1.default.deepEqual(counts, { created: 2, assigned: 1, invited: 1, accounts: 0, data: 1, completed: 1 });
});
(0, node_test_1.default)("countries group the tournaments", () => {
    const report = (0, usageService_1.buildUsageReport)({
        ...empty,
        now,
        days: 30,
        tournaments: [tournament(), tournament({ id: "t2", country: "ES" }), tournament({ id: "t3", country: "FR" })],
        changes: [change({})],
    });
    strict_1.default.deepEqual(report.countries.map((item) => [item.country, item.tournaments]), [["ES", 2], ["FR", 1]]);
    strict_1.default.equal(report.countries[0].saves, 1);
});
//# sourceMappingURL=usageService.test.js.map