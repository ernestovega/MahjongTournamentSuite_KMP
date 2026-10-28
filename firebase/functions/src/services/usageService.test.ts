import assert from "node:assert/strict";
import test from "node:test";

import {
  buildUsageReport, localSlot, type UsageAccount, type UsageActivity, type UsageChange, type UsageTournamentInfo,
} from "./usageService";

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
// Saturday 2026-10-10 09:00 UTC.
const base = Date.UTC(2026, 9, 10, 9, 0, 0);
const now = new Date(base + 5 * DAY);

function change(partial: Partial<UsageChange>): UsageChange {
  return {
    tournamentId: "t1", kind: "save", source: "editor", uid: "u1", name: "Ana",
    roundId: 1, tableId: 1, handChangeCount: 2, at: base, ...partial,
  };
}

function activity(partial: Partial<UsageActivity>): UsageActivity {
  return { action: "account.send", uid: "u1", name: "Ana", tournamentId: "t1", at: base, ...partial };
}

function tournament(partial: Partial<UsageTournamentInfo> = {}): UsageTournamentInfo {
  return {
    id: "t1", name: "Open", country: "ES", startDate: "2026-10-12", endDate: "2026-10-18", timeZone: "Europe/Madrid",
    createdAt: base - 20 * DAY, players: 8, isTeams: false, hasMatchTimer: true, hasAgenda: false, hasVenueMap: false,
    hasLogo: false, ...partial,
  };
}

function account(partial: Partial<UsageAccount>): UsageAccount {
  return { uid: "u1", name: "Ana", role: "EDITOR", disabled: false, createdAt: base - 40 * DAY, lastSeenAt: base, ...partial };
}

const empty = { tables: [], slots: [], activities: [], accounts: [], changes: [], tournaments: [] };

test("report counts saves by source, conflicts and resets", () => {
  const report = buildUsageReport({
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
      { tournamentId: "t1", hasAccount: true, hasPassword: true, isAssigned: true },
      { tournamentId: "t1", hasAccount: false, hasPassword: false, isAssigned: false },
    ],
  });

  assert.equal(report.current.saves, 2);
  assert.equal(report.current.playerSaves, 1);
  assert.equal(report.current.editorSaves, 1);
  assert.equal(report.current.conflicts, 1);
  assert.equal(report.current.resets, 1);
  assert.equal(report.current.activeUsers, 2);
  assert.equal(report.current.activeTournaments, 1);
  const item = report.tournaments[0];
  assert.equal(item.name, "Open");
  assert.equal(item.status, "LIVE");
  assert.equal(item.tablesTouched, 2);
  assert.equal(item.savesPerTouchedTable, 1);
  assert.equal(item.activeHours, 2);
  assert.equal(item.handsTables, 1);
  assert.equal(item.totalsOnlyTables, 1);
  assert.equal(item.manualTotalsTables, 1);
  assert.equal(item.slotsWithAccount, 1);
  assert.equal(item.slotsWithPassword, 1);
});

test("a change without a source counts as an editor save", () => {
  const report = buildUsageReport({ ...empty, now, days: 30, changes: [change({ source: null })] });
  assert.equal(report.current.editorSaves, 1);
});

test("changes of the system are not work of a user", () => {
  const report = buildUsageReport({ ...empty, now, days: 30, changes: [change({ source: "system", uid: null })] });
  assert.equal(report.current.saves, 0);
});

test("the previous period is the same length before the current one", () => {
  const report = buildUsageReport({
    ...empty,
    now,
    days: 7,
    changes: [change({ at: base }), change({ at: base - 6 * DAY }), change({ at: base - 20 * DAY })],
  });
  assert.equal(report.current.saves, 1);
  assert.equal(report.previous.saves, 1);
  assert.equal(report.daily.length, 7);
  assert.equal(report.daily.reduce((sum, day) => sum + day.saves, 0), 1);
});

test("the heatmap uses the time zone of the tournament", () => {
  const report = buildUsageReport({ ...empty, now, days: 30, tournaments: [tournament()], changes: [change({})] });
  // 09:00 UTC is 11:00 in Madrid in October. It is a Saturday.
  assert.equal(report.heatmap[5][11], 1);
  assert.deepEqual(localSlot(base, "Not/AZone"), { weekday: 5, hour: 9 });
});

test("accounts get a status from their work and their last sign-in", () => {
  const report = buildUsageReport({
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
  assert.deepEqual(status, { u1: "ACTIVE", u2: "VISITOR", u3: "DORMANT", u4: "NEVER", u5: "DISABLED" });
  assert.equal(report.accounts.total, 5);
  assert.equal(report.accounts.active, 1);
  assert.equal(report.accounts.visitors, 1);
  assert.equal(report.accounts.dormant, 1);
  assert.equal(report.accounts.neverSignedIn, 1);
  assert.equal(report.accounts.disabled, 1);
  assert.equal(report.accounts.admins, 1);
  assert.equal(report.users[0].uid, "u1");
  assert.equal(report.users[0].saves, 1);
  assert.equal(report.users[0].lastActiveDaysAgo, 5);
});

test("features show what is used and what is not", () => {
  const report = buildUsageReport({
    ...empty,
    now,
    days: 30,
    tournaments: [tournament(), tournament({ id: "t2", hasMatchTimer: false, isTeams: true })],
    activities: [activity({}), activity({ action: "account.resend", uid: "u2" }), activity({ action: "team.update", at: base - 60 * DAY })],
  });
  const byKey = Object.fromEntries(report.features.map((feature) => [feature.key, feature]));
  assert.equal(byKey.invitations.uses, 2);
  assert.equal(byKey.invitations.users, 2);
  assert.equal(byKey.teams.uses, 1);
  assert.equal(byKey.teams.adoption, 0.5);
  assert.equal(byKey.matchTimer.adoption, 0.5);
  assert.equal(byKey.agenda.uses, 0);
  assert.equal(byKey.substitution.uses, 0);
  assert.equal(byKey.substitution.lastUsedDaysAgo, null);
});

test("the funnel counts tournaments that reach each step", () => {
  const report = buildUsageReport({
    ...empty,
    now,
    days: 30,
    tournaments: [tournament(), tournament({ id: "t2" })],
    changes: [change({})],
    tables: [{ tournamentId: "t1", isCompleted: true, useTotalsOnly: false, hasValidManualTotals: false, hasProgress: true }],
    slots: [{ tournamentId: "t1", hasAccount: false, hasPassword: false, isAssigned: true }],
  });
  const counts = Object.fromEntries(report.funnel.map((step) => [step.key, step.count]));
  assert.deepEqual(counts, { created: 2, assigned: 1, accounts: 0, data: 1, completed: 1 });
});

test("countries group the tournaments", () => {
  const report = buildUsageReport({
    ...empty,
    now,
    days: 30,
    tournaments: [tournament(), tournament({ id: "t2", country: "ES" }), tournament({ id: "t3", country: "FR" })],
    changes: [change({})],
  });
  assert.deepEqual(report.countries.map((item) => [item.country, item.tournaments]), [["ES", 2], ["FR", 1]]);
  assert.equal(report.countries[0].saves, 1);
});
