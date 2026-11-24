// The checks of report.mjs: the verdict of each corner case of a game day, with the run that passes it and the runs
// that do not. Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";

import { checkTournament, cornerCaseVerdicts, tablePoints, verdicts } from "../report.mjs";

const ok = (lines) => lines.filter((line) => line.startsWith("❌")).length === 0;
const event = (kind, data) => ({ at: "2026-11-19T10:00:00.000Z", kind, ...data });
const run = (events, hands = []) => [{ events, hands }];
const planOf = (...cornerCases) => ({ cornerCases });
/** A check of report.mjs with [tables] as the server has them, by key. */
function checkWith(tables = {}, extra = {}) {
  const check = { extra: [], scoreProblems: [], ranking: [], substitutions: {}, ...extra };
  Object.defineProperty(check, "tablesByKey", { value: new Map(Object.entries(tables)) });
  return check;
}
const done = (handId, more = {}) => ({ handId, isDone: true, playerWinnerId: "1", playerLooserId: "", handScore: "8", ...more });

test("players who tie at a table share the points of their places", () => {
  assert.deepEqual(tablePoints([30, 10, 10, -50]), [4, 1.5, 1.5, 0]);
  assert.deepEqual(tablePoints([0, 0, 0, 0]), [1.75, 1.75, 1.75, 1.75]);
  assert.deepEqual(tablePoints([5, 5, -1, -9]), [3, 3, 1, 0]);
});

test("a race passes with one save and one refusal, and fails with two saves", () => {
  const plan = planOf({ kind: "race", round: 1, table: 1 });
  assert.ok(ok(cornerCaseVerdicts(plan, run([event("race", { table: "1_1", statuses: [200, 409] })]), checkWith())));

  const twice = cornerCaseVerdicts(plan, run([event("race", { table: "1_1", statuses: [200, 200] })], [{ table: "1_1", handId: 1, at: "2026-11-19T10:00:01Z", hand: { handScore: "8" } }]), checkWith());
  assert.ok(!ok(twice));
  // A failed corner case shows what its table saved and saw.
  assert.ok(twice.some((line) => line.includes("Saved at 1_1: #1 10:00:01 8")), twice.join("\n"));
  assert.ok(twice.some((line) => line.startsWith("  Seen at 1_1:")));
});

test("the penalties and the draw pass when the draw holds a row for each penalty", () => {
  const plan = planOf({ kind: "penalty-draw", round: 1, table: 1, afterHands: 2, penalties: ["-10", "-5"] });
  const events = [
    event("penalty", { table: "1_1", planned: true, status: 200, value: "-10" }),
    event("penalty", { table: "1_1", planned: true, status: 200, value: "-5" }),
    event("planned-draw", { table: "1_1", handId: 3 }),
  ];
  const draw = { handId: 3, isDone: true, playerWinnerId: "", handScore: "0", playerEastPenalty: "-10;", playerSouthPenalty: ";-5", playerWestPenalty: "", playerNorthPenalty: "" };
  assert.ok(ok(cornerCaseVerdicts(plan, run(events), checkWith({ "1_1": { hands: [done(1), done(2), draw] } }))));

  // The second penalty replaced the first one instead of adding a row.
  const lost = { ...draw, playerEastPenalty: "", playerSouthPenalty: "-5" };
  assert.ok(!ok(cornerCaseVerdicts(plan, run(events), checkWith({ "1_1": { hands: [done(1), done(2), lost] } }))));
  // The totals of the table do not count them.
  const wrong = checkWith({ "1_1": { hands: [done(1), done(2), draw] } }, { scoreProblems: ["1_1 East: score 1 on the server, 2 here"] });
  assert.ok(!ok(cornerCaseVerdicts(plan, run(events), wrong)));
});

test("the seats pass when they change before the first hand, not after it, and the table keeps them", () => {
  const plan = planOf({ kind: "seat-change", round: 1, table: 4 });
  const events = [
    event("seat-change", { table: "1_4", when: "before", status: 200, seats: ["14", "13", "15", "16"] }),
    event("seat-change", { table: "1_4", when: "after", status: 409 }),
  ];
  const seated = (east, south) => checkWith({ "1_4": { table: { playerEastId: east, playerSouthId: south, playerWestId: "15", playerNorthId: "16" }, hands: [] } });
  assert.ok(ok(cornerCaseVerdicts(plan, run(events), seated("14", "13"))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run(events), seated("13", "14"))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([events[0], { ...events[1], status: 200 }]), seated("14", "13"))));
});

test("the extra time passes when the hand after the gong is the last one and closes the table", () => {
  const plan = planOf({ kind: "extra-time", round: 2, table: 1, afterHands: 3 });
  const hands = [done(1), done(2), done(3), done(4)];
  const closed = checkWith({ "2_1": { table: { isCompleted: true }, hands } });
  const after = event("extra-time-hand", { table: "2_1", handId: 4, savedAt: 2_000, matchEnd: 1_000 });
  assert.ok(ok(cornerCaseVerdicts(plan, run([after]), closed)));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([{ ...after, savedAt: 500 }]), closed)), "saved before the gong");
  assert.ok(!ok(cornerCaseVerdicts(plan, run([after]), checkWith({ "2_1": { table: { isCompleted: false }, hands } }))), "the table stays open");
});

test("an abandoned table passes when the organizer closes it with the hands that it played", () => {
  const plan = planOf({ kind: "abandoned", round: 2, table: 2, afterHands: 3 });
  const left = [event("table-abandoned", { table: "2_2", hands: 3 })];
  const hands = [done(1), done(2), done(3)];
  assert.ok(ok(cornerCaseVerdicts(plan, run(left), checkWith({ "2_2": { table: { isCompleted: true }, hands } }))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run(left), checkWith({ "2_2": { table: { isCompleted: false }, hands } }))));
});

test("a substitution passes when the slot has no result in the ranking", () => {
  const plan = planOf({ kind: "substitution", round: 1, table: 3, reason: "LATE", afterMinutes: 1 });
  const substitutions = { 12: { reason: "LATE", fromRound: 1 } };
  assert.ok(ok(cornerCaseVerdicts(plan, run([]), checkWith({}, { substitutions }))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([]), checkWith({}, { substitutions, ranking: [{ playerId: "12", points: 4, score: 10 }] }))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([]), checkWith())));
});

test("a deleted hand passes when a watcher sees it change, as the scorer may write it again before the next look", () => {
  const plan = planOf({ kind: "delete", round: 1, table: 2, which: "last", minHands: 4 });
  const scorer = event("hand-deleted-seen", { table: "1_2", handId: 4, newest: 4, slot: 5 });
  const again = event("hand-reentered", { table: "1_2", handId: 4, slot: 5 });
  const changed = event("hand-corrected-seen", { table: "1_2", handId: 4, slot: 6, watcher: true });
  assert.ok(ok(cornerCaseVerdicts(plan, run([scorer, again, changed]), checkWith())));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([scorer, again]), checkWith())), "no watcher saw it");
  assert.ok(!ok(cornerCaseVerdicts(plan, run([scorer, changed]), checkWith())), "not written again");
  // The last hand was deleted, but the scorer saw an older one go.
  assert.ok(!ok(cornerCaseVerdicts(plan, run([{ ...scorer, handId: 2 }, again, changed]), checkWith())));
});

test("a change of a hand at a table without a planned case fails", () => {
  const plan = planOf({ kind: "edit", round: 1, table: 5, which: "older", minHands: 6, back: 3 });
  const seen = [
    event("hand-corrected-seen", { table: "1_5", handId: 4, newest: 7, slot: 17 }),
    event("hand-corrected-seen", { table: "1_5", handId: 4, slot: 18, watcher: true }),
  ];
  assert.ok(ok(cornerCaseVerdicts(plan, run(seen), checkWith())));
  const elsewhere = cornerCaseVerdicts(plan, run([...seen, event("hand-corrected-seen", { table: "1_6", handId: 2, slot: 21, watcher: true })]), checkWith());
  assert.ok(!ok(elsewhere));
  assert.match(elsewhere.at(-1), /1_6/);
});

test("the verdicts take the slowest of a long list without a spread", () => {
  // The medium scenario gives hundreds of thousands of values: Math.max(...list) overflows the stack there.
  const delays = Array.from({ length: 300_000 }, (_, index) => ({ handDelayMs: { p95: index % 19_000 }, labels: {}, hands: [{ ms: 300 }] }));
  const check = { scoreProblems: [], lost: [], changed: [], extra: [], raceDuplicates: 0, races: 0, handsSavedByBots: 1, openTables: [] };
  const lines = verdicts(check, delays, null);
  assert.ok(lines.some((line) => line === "✅ A hand reaches the other phones of the table in p95 18999 ms (target under 20 s)."), lines.join("\n"));
});

test("the check leaves out the results of a substituted slot from the round of the substitution", async () => {
  const seats = { playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4" };
  const table = (roundId, scores, points) => ({
    table: { roundId, tableId: 1, isCompleted: true, ...seats, ...Object.fromEntries(["East", "South", "West", "North"].flatMap((name, index) => [[`player${name}Score`, String(scores[index])], [`player${name}Points`, String(points[index])]])) },
    hands: [{ handId: 1, isDone: true, playerWinnerId: "1", playerLooserId: "4", handScore: "10", playerEastPenalty: "", playerSouthPenalty: "-5", playerWestPenalty: "", playerNorthPenalty: "" }],
  });
  const api = {
    async call(method, path) {
      if (path.endsWith("/tables-with-hands")) return { status: 200, json: { tables: [table(1, [34, -13, -8, -18], [4, 1, 2, 0]), { ...table(2, [34, -13, -8, -18], [4, 1, 2, 0]), table: { ...table(2, [34, -13, -8, -18], [4, 1, 2, 0]).table, roundId: 2, tableId: 1 } }] } };
      if (path.endsWith("/players")) return { status: 200, json: { players: [{ id: 4, substitution: { reason: "LATE", fromRound: 2 } }] } };
      return { status: 404, json: null };
    },
  };
  const check = await checkTournament(api, "t1", []);
  assert.deepEqual(check.scoreProblems, []);
  // Slot 4 keeps the result of round 1 only.
  assert.deepEqual(check.ranking.find((entry) => entry.playerId === "4"), { playerId: "4", points: 0, score: -18 });
  assert.deepEqual(check.ranking.find((entry) => entry.playerId === "1"), { playerId: "1", points: 8, score: 68 });
});

/** The tables of a draw as the server has them: [rounds] of [seats] lists. */
function drawOf(rounds) {
  const tables = {};
  rounds.forEach((round, roundIndex) => round.forEach((seats, tableIndex) => {
    const [East, South, West, North] = seats.map(String);
    tables[`${roundIndex + 1}_${tableIndex + 1}`] = {
      table: { roundId: roundIndex + 1, tableId: tableIndex + 1, playerEastId: East, playerSouthId: South, playerWestId: West, playerNorthId: North },
      hands: [],
    };
  }));
  return checkWith(tables);
}

test("a table taken out passes when every round has the new tables with each player once", () => {
  const plan = { rounds: 2, cornerCases: [{ kind: "player-count", round: 0, table: 0, from: 12, to: 8 }] };
  const good = cornerCaseVerdicts(plan, run([]), drawOf([[[1, 2, 3, 4], [5, 6, 7, 8]], [[1, 5, 2, 6], [3, 7, 4, 8]]]));
  assert.ok(ok(good), good.join("\n"));
  assert.ok(good[0].includes("created with 12 players") && good[0].includes("2 tables in each of its 2 rounds"), good[0]);

  // A slot above the new number left at a table, a round with the old number of tables, or a missing round fail.
  const leftOver = cornerCaseVerdicts(plan, run([]), drawOf([[[1, 2, 3, 4], [5, 6, 7, 9]], [[1, 5, 2, 6], [3, 7, 4, 8]]]));
  assert.ok(!ok(leftOver));
  assert.ok(leftOver[0].includes("wrong in round 1"), leftOver[0]);
  assert.ok(!ok(cornerCaseVerdicts(plan, run([]), drawOf([[[1, 2, 3, 4], [5, 6, 7, 8], [9, 10, 11, 12]], [[1, 5, 2, 6], [3, 7, 4, 8]]]))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([]), drawOf([[[1, 2, 3, 4], [5, 6, 7, 8]]]))));
  assert.ok(!ok(cornerCaseVerdicts(plan, run([]), null)));

  // Tables out and one back (corners): the last draw is the one checked, and the verdict says both changes.
  const both = { rounds: 2, cornerCases: [{ kind: "player-count", round: 0, table: 0, from: 12, via: 4, to: 8 }] };
  const backIn = cornerCaseVerdicts(both, run([]), drawOf([[[1, 2, 3, 4], [5, 6, 7, 8]], [[1, 5, 2, 6], [3, 7, 4, 8]]]));
  assert.ok(ok(backIn), backIn.join("\n"));
  assert.ok(backIn[0].includes("took 2 tables out (4 players) and added 1 back"), backIn[0]);
});
