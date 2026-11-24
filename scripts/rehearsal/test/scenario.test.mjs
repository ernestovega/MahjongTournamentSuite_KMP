// The plan of a game day (scenario.mjs), against a staging that answers in memory. Run with:
// node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";

import { accountsToFreeUp, eligiblePlayers, makePlan, MISTYPED_EMA, plannedStart, returningPlayers, SCENARIOS, slotKind } from "../scenario.mjs";
import { ENV, planStaging, registry, withEnv, withFetch } from "./support.mjs";

const planFor = (args, players = registry(300), users = []) => withEnv(ENV, () => withFetch(planStaging(players, users), () => makePlan(args)));

test("only EMA numbers of 8 digits of real people without an account play", () => {
  const players = [
    { emaId: "10000001", firstName: "Ana", lastName: "García" },
    // A number that lost its leading zero in the registry: the import pads it to 8 digits and does not find it.
    { emaId: "8010027", firstName: "Lost", lastName: "Zero" },
    { emaId: "123456789", firstName: "Too", lastName: "Long" },
    { emaId: "00000000", firstName: "Google", lastName: "Play" },
    { emaId: "10000002", firstName: "Test", lastName: "Player" },
    { emaId: "99980001", firstName: "Made", lastName: "Up" },
    { emaId: "10000003", firstName: "Off", lastName: "Line", disabled: true },
    { emaId: "10000004", firstName: "Has", lastName: "Account" },
    { emaId: "10000005", firstName: "", lastName: "NoName" },
    { emaId: "10000006", firstName: "EMA", lastName: "GUEST 1" },
  ];
  const free = eligiblePlayers(players, [{ emaId: "10000004" }, { emaId: null }]);
  assert.deepEqual(free.map((player) => player.emaId), ["10000001", "10000006"]);
});

test("the rounds start an hour later in Madrid, and at 23:59 when that is tomorrow", () => {
  // 10:00 in Madrid (UTC+1 in November).
  assert.deepEqual(plannedStart(new Date("2026-11-19T09:00:00Z")), { date: "2026-11-19", time: "11:00" });
  // 23:30 in Madrid: an hour later is the next day, and the form keeps the dates of a new tournament on today.
  assert.deepEqual(plannedStart(new Date("2026-11-19T22:30:00Z")), { date: "2026-11-19", time: "23:59" });
  // 00:30 in Madrid is still 23:30 in UTC: the day is the one of Madrid.
  assert.deepEqual(plannedStart(new Date("2026-11-19T23:30:00Z")), { date: "2026-11-20", time: "01:30" });
  // Summer time (UTC+2).
  assert.deepEqual(plannedStart(new Date("2026-07-01T21:30:00Z")), { date: "2026-07-01", time: "23:59" });
});

test("the corners plan has every corner case in round 1, and the slow and abandoned tables in the last round", async () => {
  const { plan } = await planFor({ scenario: "corners" });
  assert.equal(plan.players, 28);
  assert.equal(plan.rounds, 2);
  assert.equal(plan.matchDurationMinutes, 8);
  const at = (kind) => plan.cornerCases.filter((item) => item.kind === kind).map((item) => `${item.round}_${item.table}`);
  assert.deepEqual(at("race"), ["1_1"]);
  assert.deepEqual(at("penalty-draw"), ["1_1"]);
  assert.deepEqual(at("late"), ["1_3"]);
  assert.deepEqual(at("substitution"), ["1_3"]);
  assert.deepEqual(at("seat-change"), ["1_4"]);
  assert.deepEqual(at("extra-time"), ["2_1"]);
  assert.deepEqual(at("abandoned"), ["2_2"]);
  assert.deepEqual(at("manual-totals"), ["2_3"]);
  assert.deepEqual(at("offline"), ["2_4"]);
  // Table 7 of round 1 has no other case: its players finish level.
  assert.deepEqual(at("tie"), ["1_7"]);
  // The editor never plays at the late table.
  assert.deepEqual([...at("delete"), ...at("edit")], ["1_2", "1_4", "1_5", "1_6"]);
  assert.deepEqual(plan.cornerCases.find((item) => item.kind === "penalty-draw").penalties, ["-10", "-5"]);
  // Before the first round the organizer takes out a table: the tournament is created with 32 players.
  assert.equal(plan.createWith, 32);
  assert.deepEqual(plan.cornerCases.filter((item) => item.kind === "player-count").map(({ round, table, from, via, to }) => [round, table, from, via, to]), [[0, 0, 32, 24, 28]]);
  assert.equal(plan.slots.length, 28, "the slots of the plan are the players after the change");
});

test("every scenario plays the editor in round 2, the extra time and the abandoned table in its last round", async () => {
  for (const scenario of ["small", "medium", "large"]) {
    const { plan } = await planFor({ scenario });
    assert.equal(plan.players, SCENARIOS[scenario].players);
    assert.equal(plan.slots.length, plan.players);
    for (const item of plan.cornerCases.filter((each) => each.kind === "delete" || each.kind === "edit")) assert.equal(item.round, 2);
    for (const kind of ["extra-time", "abandoned", "manual-totals", "offline"]) assert.equal(plan.cornerCases.find((item) => item.kind === kind).round, plan.rounds);
    assert.equal(plan.cornerCases.some((item) => item.kind === "tie"), false, "the tie is in corners only");
    assert.equal(plan.cornerCases.some((item) => item.kind === "player-count"), false, "the change of the number of players is in corners only");
    assert.equal(plan.createWith, undefined);
    assert.ok(plan.roundSchedules.every((round) => round.date === plan.roundSchedules[0].date), "every round on one day");
  }
});

test("the slots: an email of the mailbox each, 1 in 20 with an account made in Users, the last ones by hand", async () => {
  const { plan, files } = await planFor({ scenario: "small" });
  const emails = new Set(plan.slots.map((slot) => slot.email));
  assert.equal(emails.size, 24);
  assert.match(plan.slots[0].email, new RegExp(`^mailbox\\+${plan.tag}-s001@example\\.test$`));
  // Without players of earlier game days, new players take their slots.
  assert.equal(plan.slots.filter((slot) => slot.returning).length, 0);
  assert.deepEqual(plan.slots.filter((slot) => slot.accountBefore).map((slot) => slot.slot), [2, 22]);
  assert.deepEqual(plan.slots.filter((slot) => slot.via === "manual").map((slot) => slot.slot), [23, 24]);
  assert.ok(plan.slots.every((slot) => /^\d{8}$/.test(slot.emaId)));
  // The right file has the 22 slots of the import, and the file with mistakes 2 rows more.
  const good = files["players.csv"].trim().split("\n");
  const bad = files["players-errors.csv"].trim().split("\n");
  assert.equal(good[0], "id,ema id,email,team");
  assert.equal(good.length, 23);
  assert.equal(bad.length, 25);
  assert.match(bad[1], /,99999999,/);
  assert.match(bad[2], /@@/);
});

test("two EMA guests play when the registry has them, and a registry too small is refused", async () => {
  const guests = [
    { emaId: "10900001", firstName: "EMA", lastName: "GUEST 1" },
    { emaId: "10900002", firstName: "EMA", lastName: "GUEST 2" },
    { emaId: "10900003", firstName: "EMA", lastName: "GUEST 3" },
  ];
  const { plan } = await planFor({ scenario: "small" }, registry(40, guests));
  assert.equal(plan.slots.filter((slot) => /GUEST/.test(slot.name)).length, 2);
  await assert.rejects(planFor({ scenario: "small" }, registry(10)), /only 10 free EMA players/);
  await assert.rejects(planFor({ scenario: "huge" }), /Unknown scenario huge/);
});

test("1 in 4 players is new, 1 in 20 has an account made in Users, and the others come back", () => {
  const kinds = Array.from({ length: 20 }, (_, index) => slotKind(index + 1));
  assert.equal(kinds.filter((kind) => kind === "new").length, 5);
  assert.deepEqual(kinds.flatMap((kind, index) => (kind === "account-before" ? [index + 1] : [])), [2]);
  assert.equal(kinds.filter((kind) => kind === "returning").length, 14);
});

test("the players of earlier game days: accounts of the mailbox of the tests with a player that may play", () => {
  const players = [
    { emaId: "10000001", firstName: "Ana", lastName: "García" },
    { emaId: "10000002", firstName: "Ben", lastName: "Li" },
    { emaId: "10000003", firstName: "Cai", lastName: "Wu" },
    { emaId: "8010027", firstName: "Lost", lastName: "Zero" },
  ];
  const users = [
    { emaId: "10000001", email: "Mailbox+gd1119abcd-s001@example.test" },
    // Another address, a disabled account, a number that may not play and a tag that is not a game day.
    { emaId: "10000002", email: "someone@example.test" },
    { emaId: "10000003", email: "mailbox+gd1119abcd-s003@example.test", disabled: true },
    { emaId: "8010027", email: "mailbox+gd1119abcd-s004@example.test" },
    { emaId: "10000002", email: "mailbox+mts-j02@example.test" },
  ];
  assert.deepEqual(returningPlayers(players, users, "mailbox@example.test"),
    [{ emaId: "10000001", firstName: "Ana", lastName: "García", email: "mailbox+gd1119abcd-s001@example.test" }]);
});

test("a plan seats the players of earlier game days with their own email, and few free players are then enough", async () => {
  const players = registry(300);
  const users = players.slice(0, 200).map((player, index) => ({
    emaId: player.emaId, email: `mailbox+gd1118old${index % 10}-s${String(index + 1).padStart(3, "0")}@example.test`,
  }));
  // 100 free players and 200 of earlier game days: a large scenario needs 240.
  const { plan } = await planFor({ scenario: "large" }, players, users);
  const back = plan.slots.filter((slot) => slot.returning);
  assert.equal(back.length, plan.slots.filter((slot) => slotKind(slot.slot) === "returning").length);
  assert.ok(back.every((slot) => slot.accountBefore && /\+gd1118old/.test(slot.email)));
  assert.ok(plan.slots.filter((slot) => !slot.returning).every((slot) => slot.email.includes(plan.tag)));
  assert.equal(new Set(plan.slots.map((slot) => slot.emaId)).size, 240);
  await assert.rejects(planFor({ scenario: "large" }, players.slice(0, 210), users), /free EMA players, and 200 players of earlier game days/);
});

test("with few free players, players of earlier game days also take slots of new players, and a few new ones stay", async () => {
  const players = registry(600);
  const users = players.slice(0, 540).map((player, index) => ({
    emaId: player.emaId, email: `mailbox+gd1118old${index % 10}-s${String(index + 1).padStart(3, "0")}@example.test`,
  }));
  // 60 free players: a large scenario would want 72 of them (1 in 4 new, 1 in 20 with an account made in Users).
  const { plan } = await planFor({ scenario: "large" }, players, users);
  const fresh = plan.slots.filter((slot) => !slot.returning);
  assert.ok(fresh.length <= 60 && fresh.length >= 12, `${fresh.length} new players`);
  assert.equal(new Set(plan.slots.map((slot) => slot.emaId)).size, 240);
});

test("with no free player, every player comes back from an earlier game day", async () => {
  const players = registry(300);
  const users = players.map((player, index) => ({
    emaId: player.emaId, email: `mailbox+gd1118old${index % 10}-s${String(index + 1).padStart(3, "0")}@example.test`,
  }));
  const { plan } = await planFor({ scenario: "large" }, players, users);
  assert.ok(plan.slots.every((slot) => slot.returning && slot.accountBefore));
  assert.equal(new Set(plan.slots.map((slot) => slot.emaId)).size, 240);
  // With 5 free players, those 5 are the new ones.
  const { plan: few } = await planFor({ scenario: "large" }, players, users.slice(5));
  assert.equal(few.slots.filter((slot) => !slot.returning).length, 5);
});

test("with too few free players, the accounts of the oldest game days are deleted, so new players are invited again", async () => {
  const players = registry(300);
  const users = players.map((player, index) => ({
    uid: `u${index}`, emaId: player.emaId,
    email: `mailbox+gd${index < 100 ? "1101" : "1118"}old-s${String(index + 1).padStart(3, "0")}@example.test`,
  }));
  const deleted = [];
  const { plan } = await withEnv(ENV, () => withFetch(planStaging(players, users, deleted), () => makePlan({ scenario: "small" })));
  // Small has 24 players: 6 new and 2 with an account made in Users need free EMA players.
  assert.equal(deleted.length, 8);
  assert.ok(deleted.every((uid) => Number(uid.slice(1)) < 100), "the oldest game day goes first");
  assert.equal(plan.slots.filter((slot) => !slot.returning).length, 8);
  assert.ok(plan.slots.filter((slot) => !slot.returning).every((slot) => slot.email.includes(plan.tag)));
});

test("the accounts to free up keep enough players of earlier game days and need a uid", () => {
  const back = Array.from({ length: 10 }, (_, index) => ({ uid: `u${index}`, email: `m+gd11${10 + index}x-s001@d` }));
  assert.deepEqual(accountsToFreeUp(back, 5, 4, 3), []);
  assert.deepEqual(accountsToFreeUp(back, 0, 4, 3).map((player) => player.uid), ["u0", "u1", "u2", "u3"]);
  assert.equal(accountsToFreeUp(back, 0, 20, 8).length, 2);
  assert.deepEqual(accountsToFreeUp([{ email: "m+gd1101x-s001@d" }], 0, 1, 0), []);
});

test("corners plans the registrations by hand: 16 people in the Google Form, a 17th by hand, and the first one leaves", async () => {
  const { plan, files } = await planFor({ scenario: "corners" });
  assert.equal(plan.byHand.places, 16);
  assert.deepEqual(plan.byHand.form, plan.slots.slice(0, 16).map((slot) => slot.emaId));
  assert.equal(plan.byHand.added, plan.slots[16].emaId);
  assert.equal(plan.byHand.removed, plan.slots[0].emaId);
  assert.match(plan.byHand.slug, /^[a-z0-9-]{1,40}$/);
  const rows = files["registrations-form.csv"].trim().split("\n");
  assert.equal(rows.length, 17);
  assert.ok(rows[0].includes("EMA number"));
  // The Google Form has the mistakes of real people: an EMA number that the registry does not have, an email with a
  // typo and an email shared by two people. The other rows are as the plan has them.
  const form = files["registrations-form.csv"];
  const { mistyped, badEmail, sharedEmail } = plan.byHand;
  assert.equal(mistyped.emaId, plan.slots[5].emaId);
  assert.equal(mistyped.file, MISTYPED_EMA);
  assert.ok(!form.includes(mistyped.emaId) && rows[6].includes(MISTYPED_EMA), rows[6]);
  assert.ok(plan.byHand.form.filter((emaId) => emaId !== mistyped.emaId).every((emaId) => form.includes(emaId)));
  assert.equal(badEmail.emaId, plan.slots[6].emaId);
  assert.ok(rows[7].includes(badEmail.email.replace("@", "@@")) && rows[7].includes(badEmail.emaId), rows[7]);
  assert.equal(sharedEmail.with, plan.slots[8].emaId);
  assert.ok(rows[8].includes(plan.slots[8].email) && rows[9].includes(plan.slots[8].email), `${rows[8]}\n${rows[9]}`);
  assert.ok(!rows[8].includes(plan.slots[7].email));
  // The other scenarios leave it to the bots.
  const { plan: small, files: smallFiles } = await planFor({ scenario: "small" });
  assert.equal(small.byHand, undefined);
  assert.equal(smallFiles["registrations-form.csv"], undefined);
});
