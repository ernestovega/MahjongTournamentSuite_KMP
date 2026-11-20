// The bots of a game day against a staging in memory (fakeStaging.mjs): the corners plan of scenario.mjs, played by
// bots.mjs, checked by report.mjs. Every corner case of the plan must pass, as in a green run of scenario.yml.
// Run with: node --test scripts/rehearsal/test/*.test.mjs
import { before, test } from "node:test";
import assert from "node:assert/strict";

import { Metrics } from "../api.mjs";
import { planOptions, runBots } from "../bots.mjs";
import { checkTournament, cornerCaseVerdicts, verdicts } from "../report.mjs";
import { makePlan } from "../scenario.mjs";
import { FakeStaging } from "./fakeStaging.mjs";
import { ENV, planStaging, registry, withEnv, withFetch } from "./support.mjs";

async function cornersPlan() {
  const { plan } = await withEnv(ENV, () => withFetch(planStaging(registry(40)), () => makePlan({ scenario: "corners" })));
  return plan;
}

/** Round 1: slots 1-4 at table 1, 5-8 at table 2... Round 2: table t has slots t, t+6, t+12 and t+18. */
function draws(players) {
  const tables = players / 4;
  return [
    Array.from({ length: tables }, (_, table) => [1, 2, 3, 4].map((seat) => table * 4 + seat)),
    Array.from({ length: tables }, (_, table) => [0, 1, 2, 3].map((seat) => table + 1 + seat * tables)),
  ];
}

async function play(plan, { minutes = [60, 0.05] } = {}) {
  const metrics = new Metrics();
  const staging = new FakeStaging({ metrics, draws: draws(plan.players), minutes, plan });
  let doneAt = null;
  const report = await runBots({
    api: staging,
    metrics,
    tournamentId: "t1",
    slots: plan.slots.map((slot) => slot.slot),
    emailOf: (slot) => plan.slots.find((item) => item.slot === slot).email,
    password: "secret",
    until: Date.now() + 60_000,
    // The bots stop a little after the last table closes, so the phones see the last corrections.
    stopped: () => {
      if (!staging.allDone) return false;
      doneAt ??= Date.now();
      return Date.now() - doneAt > 500;
    },
    handSeconds: [0.04, 0.08],
    penaltyRate: 0,
    pollMs: 15,
    signInGapMs: 0,
    clockCacheMs: 20,
    waitMs: 50,
    holdMs: 5_000,
    extraTimeAfterMs: 100,
    ...planOptions(plan),
    // The phone without network is away 0.2 s instead of a minute.
    offlineTables: Object.fromEntries(Object.entries(planOptions(plan).offlineTables).map(([key, item]) => [key, { ...item, seconds: 0.2 }])),
    lateMs: 300,
    matchMinutes: minutes[1],
  });
  const check = await checkTournament(staging, "t1", [report]);
  return { report, check, staging };
}

let plan;
let game;
// One game for all the tests of this file: it takes about 5 s.
before(async () => {
  plan = await cornersPlan();
  game = await play(plan);
}, { timeout: 60_000 });

test("the corners plan plays every corner case, and the report finds them all", async () => {
  const { report, check, staging } = game;
  const corner = cornerCaseVerdicts(plan, [report], check);
  const general = verdicts(check, [report], null);

  assert.deepEqual(corner.filter((line) => line.startsWith("❌")), [], corner.join("\n"));
  assert.deepEqual(general.filter((line) => line.startsWith("❌")), [], general.join("\n"));
  // One line for each corner case and one for the other tables.
  assert.equal(corner.length, plan.cornerCases.length + 1);
  assert.equal(staging.edits.length, 4);
  assert.equal(report.signedIn, plan.players);
});

test("the race sends the same hand from two phones, and the server keeps one", async () => {
  const { report, check } = game;
  const race = report.events.find((event) => event.kind === "race");

  assert.equal(race.table, "1_1");
  assert.deepEqual([...race.statuses].sort(), [200, 409]);
  assert.equal(check.raceDuplicates, 0);
  assert.equal(check.extra.length, 0);
});

test("the planned penalties go in two calls before a draw, and count in the totals", async () => {
  const { report, check } = game;
  const penalties = report.events.filter((event) => event.kind === "penalty" && event.table === "1_1");
  const draw = report.events.find((event) => event.kind === "planned-draw" && event.table === "1_1");
  const hand = check.tablesByKey.get("1_1").hands.find((each) => each.handId === draw.handId);

  assert.deepEqual(penalties.map((event) => [event.value, event.status]), [["-10", 200], ["-5", 200]]);
  assert.equal(hand.playerWinnerId, "");
  assert.equal(hand.playerEastPenalty, "-10;");
  assert.equal(hand.playerSouthPenalty, ";-5");
  assert.deepEqual(check.scoreProblems, []);
});

test("the slow table of the last round saves its last hand after the gong, and the abandoned table stops", async () => {
  const { report, check } = game;
  const extra = report.events.find((event) => event.kind === "extra-time-hand");
  const left = report.events.find((event) => event.kind === "table-abandoned");

  assert.equal(extra.table, `${plan.rounds}_1`);
  assert.ok(extra.savedAt > extra.matchEnd, "saved after the gong");
  assert.equal(check.tablesByKey.get(`${plan.rounds}_1`).table.isCompleted, true);
  assert.equal(left.table, `${plan.rounds}_2`);
  assert.equal(left.hands, 3);
  assert.equal(check.tablesByKey.get(`${plan.rounds}_2`).hands.filter((each) => each.isDone).length, 3);
});

test("the seats change before the first hand, and not after it", async () => {
  const { report } = game;
  const changes = report.events.filter((event) => event.kind === "seat-change");

  assert.deepEqual(changes.map((event) => [event.when, event.status]), [["before", 200], ["after", 409]]);
  assert.match(changes[1].message, /cannot change/);
});

test("a hand that the editor deletes is written again, and a corrected one is taken as the right one", async () => {
  const { report, check, staging } = game;
  const deletedLast = staging.edits.find((edit) => edit.kind === "delete" && edit.handId === edit.newest);
  const again = report.events.filter((event) => event.kind === "hand-reentered");

  assert.ok(deletedLast != null, JSON.stringify(staging.edits));
  // The scorer waits for the editor when the newest hand is the one to change: it is still the newest.
  assert.equal(deletedLast.newest, 4);
  assert.equal(again.length, 2);
  assert.deepEqual(check.lost, []);
  assert.deepEqual(check.changed, []);
});

test("the table of manual totals saves its seats and stops, and the editor's totals count in the ranking", async () => {
  const { report, check } = game;
  const key = plan.cornerCases.find((item) => item.kind === "manual-totals");
  const data = check.tablesByKey.get(`${key.round}_${key.table}`);

  assert.ok(report.events.some((event) => event.kind === "table-left-for-totals" && event.table === `${key.round}_${key.table}`));
  assert.equal(data.table.useTotalsOnly, true);
  assert.equal(data.hands.filter((hand) => hand.isDone).length, 0);
  assert.deepEqual(["East", "South", "West", "North"].map((name) => data.table[`player${name}Score`]), key.scores);
  const east = check.ranking.find((entry) => entry.playerId === data.table.playerEastId);
  assert.ok(east != null, "the players of the table are in the ranking");
});

test("a phone that lost the network sends the hand again and is refused, and a phone in the background catches up", async () => {
  const { report } = game;
  const lost = report.events.find((event) => event.kind === "offline-lost-reply");
  const back = report.events.find((event) => event.kind === "offline-back");
  const caughtUp = report.events.find((event) => event.kind === "background-back");

  assert.equal(lost.table, "2_4");
  assert.equal(back.status, 409);
  assert.equal(back.shown, 1);
  assert.ok(caughtUp.after >= caughtUp.before);
});

test("the players of the tie table finish level: their tables play only draws", async () => {
  const { report, check } = game;
  const tied = check.tablesByKey.get("1_7").table;
  const rows = check.ranking.filter((entry) => ["East", "South", "West", "North"].map((name) => tied[`player${name}Id`]).includes(entry.playerId));

  assert.equal(rows.length, 4);
  assert.ok(rows.every((row) => row.points === rows[0].points && row.score === 0));
  assert.ok(report.events.filter((event) => event.kind === "tie-table").length >= 4, "the tables of round 2 with those players play draws too");
});
