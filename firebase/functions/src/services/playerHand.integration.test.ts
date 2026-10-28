import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { giveEmaIdToAccount, seatEmaId } from "../testing/authFixtures";
import { readChanges, readHand, readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { getTournamentManifest } from "./dataVersionsService";
import { addPenalties, parseHandInput, parsePenaltyInput, previewHand, recordHand } from "./playerHandService";
import { saveTableState } from "./tableManagerService";

type Fixture = { tournamentId: string; uid: string; otherUid: string };

/** Gives round 1 a schedule in UTC. The match lasts 120 minutes and ends with the hand in play cut off. */
async function startMatch(tournamentId: string, startedMinutesAgo: number): Promise<void> {
  const start = new Date(Date.now() - startedMinutesAgo * 60_000).toISOString();
  await db.doc(`tournaments/${tournamentId}`).update({
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: "STOP",
    matchExtraMinutes: 15,
    roundSchedules: [{ roundId: 1, date: start.slice(0, 10), startTime: start.slice(11, 16) }],
  });
}

/** A hands-mode table with seats 1..4 from east to north. Slot 1 and slot 2 have a player account. */
async function seedTable(options: { totalsOnly?: boolean; seats?: boolean; startedMinutesAgo?: number | null } = {}): Promise<Fixture> {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  if (options.startedMinutesAgo !== null) await startMatch(tournamentId, options.startedMinutesAgo ?? 10);
  await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
    useTotalsOnly: options.totalsOnly ?? false,
    ...(options.seats === false ? {} : { playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4" }),
  });
  const uid = `u-${tournamentId}-1`;
  const otherUid = `u-${tournamentId}-9`;
  await giveEmaIdToAccount(uid, "E1");
  await giveEmaIdToAccount(otherUid, "E9");
  await seatEmaId(tournamentId, 1, "E1");
  await seatEmaId(tournamentId, 9, "E9");
  return { tournamentId, uid, otherUid };
}

const at = (fixture: Fixture) => ({ tournamentId: fixture.tournamentId, roundId: 1, tableId: 1 });
const hand = (winner: string, looser: string, score = "10") => ({
  playerWinnerId: winner, playerLooserId: looser, handScore: score, isChickenHand: false,
  isDraw: false, isLastHand: false,
});

async function rejects(promise: Promise<unknown>, status: number): Promise<void> {
  await assert.rejects(promise, (error: unknown) => error instanceof HttpError && error.status === status);
}

describe("hand entry by a player", () => {
  before(requireEmulator);

  it("previews the hand and the table totals without writing", async () => {
    const fixture = await seedTable();

    const preview = await previewHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") });

    assert.equal(preview.handId, 1);
    assert.deepEqual(preview.seats, ["1", "2", "3", "4"]);
    assert.deepEqual(preview.handScores, [34, -8, -8, -18]);
    assert.deepEqual(preview.tableScores, ["34", "-8", "-8", "-18"]);
    assert.deepEqual(preview.tablePoints, ["4", "1,5", "1,5", "0"]);
    assert.equal(preview.tableVersion, 0);
    assert.equal((await readHand(fixture.tournamentId, 1, 1, 1)).isDone, false);
    assert.equal((await readTable(fixture.tournamentId, 1, 1)).version, 0);
  });

  it("records the hand, updates the totals and logs the player", async () => {
    const fixture = await seedTable();
    const preview = await previewHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") });

    const result = await recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: preview.tableVersion });

    assert.equal(result.tableVersion, 1);
    const stored = await readHand(fixture.tournamentId, 1, 1, 1);
    assert.equal(stored.isDone, true);
    assert.equal(stored.playerWinnerId, "1");
    const table = await readTable(fixture.tournamentId, 1, 1);
    assert.equal(table.version, 1);
    assert.equal(table.playerEastScore, "34");
    assert.equal(table.playerNorthPoints, "0");
    assert.equal((await getTournamentManifest(fixture.tournamentId)).tableVersions["1_1"], 1);
    const changes = await readChanges(fixture.tournamentId);
    assert.equal(changes[changes.length - 1]?.uid, fixture.uid);
  });

  it("uses the next free hand and adds to the totals", async () => {
    const fixture = await seedTable();
    await recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 });

    const preview = await previewHand({ ...at(fixture), uid: fixture.uid, input: hand("2", "") });

    assert.equal(preview.handId, 2);
    assert.deepEqual(preview.handScores, [-18, 54, -18, -18]);
    assert.deepEqual(preview.tableScores, ["16", "46", "-26", "-36"]);
  });

  it("refuses a record when the table changed after the review", async () => {
    const fixture = await seedTable();
    const preview = await previewHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") });
    await saveTableState({
      ...at(fixture), expectedVersion: 0, tablePatch: {},
      handPatches: [{ handId: 1, patch: { playerWinnerId: "2", playerLooserId: "3", handScore: "9", isDone: true } }],
    });

    await rejects(
      recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: preview.tableVersion }),
      409,
    );
  });

  it("refuses a player who does not sit at the table or is not registered", async () => {
    const fixture = await seedTable();

    await rejects(previewHand({ ...at(fixture), uid: fixture.otherUid, input: hand("1", "4") }), 403);
    await rejects(previewHand({ ...at(fixture), uid: "nobody", input: hand("1", "4") }), 403);
  });

  it("refuses a hand before the match starts, after it ends, and without a schedule", async () => {
    const early = await seedTable({ startedMinutesAgo: -30 });
    const late = await seedTable({ startedMinutesAgo: 300 });
    const unscheduled = await seedTable({ startedMinutesAgo: null });

    for (const fixture of [early, late, unscheduled]) {
      await rejects(previewHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") }), 409);
      await rejects(recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 }), 409);
      await rejects(addPenalties({ ...at(fixture), uid: fixture.uid, penalties: ["-10", "", "", ""], expectedVersion: 0 }), 409);
      assert.equal((await readTable(fixture.tournamentId, 1, 1)).version, 0);
    }
  });

  it("refuses an account whose EMA number no longer matches the slot", async () => {
    const otherPerson = await seedTable();
    const otherAccount = await seedTable();
    // The slot holds another person now, or the account owns another EMA number now.
    await db.doc(`tournaments/${otherPerson.tournamentId}/players/1`).update({ assignedEmaId: "E77" });
    await db.doc(`users/${otherAccount.uid}`).update({ emaId: "E99" });

    for (const fixture of [otherPerson, otherAccount]) {
      await rejects(previewHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") }), 403);
      await rejects(recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 }), 403);
    }
  });

  it("refuses totals-only tables and tables without four seats", async () => {
    const totalsOnly = await seedTable({ totalsOnly: true });
    const noSeats = await seedTable({ seats: false });

    await rejects(previewHand({ ...at(totalsOnly), uid: totalsOnly.uid, input: hand("1", "4") }), 409);
    await rejects(previewHand({ ...at(noSeats), uid: noSeats.uid, input: hand("1", "4") }), 409);
  });

  it("refuses values that are not a valid hand", async () => {
    const fixture = await seedTable();

    await rejects(previewHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "1") }), 400);
    await rejects(previewHand({ ...at(fixture), uid: fixture.uid, input: hand("9", "1") }), 400);
    assert.throws(() => parseHandInput({ playerWinnerId: "1", playerLooserId: "4", handScore: "7" }), HttpError);
    assert.throws(() => parseHandInput({ playerWinnerId: "1", playerLooserId: "", handScore: "10", isChickenHand: true }), HttpError);
  });

  it("marks the table completed when the player flags the last hand", async () => {
    const fixture = await seedTable();

    await recordHand({ ...at(fixture), uid: fixture.uid, input: { ...hand("1", "4"), isLastHand: true }, expectedVersion: 0 });

    const table = await readTable(fixture.tournamentId, 1, 1);
    assert.equal(table.isCompleted, true);
    assert.equal(table.playerEastScore, "34");
    await rejects(previewHand({ ...at(fixture), uid: fixture.uid, input: hand("2", "3") }), 409);
  });

  it("records a draw as a hand with zero for every seat", async () => {
    const fixture = await seedTable();
    const draw = parseHandInput({ isDraw: true });

    const preview = await previewHand({ ...at(fixture), uid: fixture.uid, input: draw });
    assert.deepEqual(preview.handScores, [0, 0, 0, 0]);
    await recordHand({ ...at(fixture), uid: fixture.uid, input: draw, expectedVersion: 0 });

    const saved = await readHand(fixture.tournamentId, 1, 1, 1);
    assert.equal(saved.isDone, true);
    assert.equal(saved.playerWinnerId, "");
    assert.equal(saved.handScore, "0");
    assert.equal((await readTable(fixture.tournamentId, 1, 1)).playerEastScore, "0");
  });

  it("parses the penalties of a request", () => {
    const base = { playerSouthPenalty: "-15", playerEastPenalty: "5", playerWestPenalty: "5", playerNorthPenalty: "5" };
    assert.throws(() => parsePenaltyInput({}), HttpError);
    assert.throws(() => parsePenaltyInput({ playerSouthPenalty: "abc" }), HttpError);
    // Every penalty needs a negative value.
    assert.throws(() => parsePenaltyInput({ playerSouthPenalty: "+5" }), HttpError);
    assert.throws(() => parsePenaltyInput({ playerSouthPenalty: "-5;5", playerEastPenalty: ";5" }), HttpError);
    assert.deepEqual(parsePenaltyInput(base), ["5", "-15", "5", "5"]);
    // A second penalty in the same request.
    assert.deepEqual(
      parsePenaltyInput({ playerSouthPenalty: "-15;5", playerEastPenalty: "+5;-6", playerWestPenalty: "5;2" }),
      ["5;-6", "-15;5", "5;2", ""],
    );
  });

  it("adds penalties to the hand in play without finishing the hand", async () => {
    const fixture = await seedTable();

    const first = await addPenalties({
      ...at(fixture), uid: fixture.uid, penalties: ["5", "-15", "5", "5"], expectedVersion: 0,
    });
    assert.equal(first.handId, 1);
    assert.equal(first.tableVersion, 1);
    // A second penalty goes after the first one.
    await addPenalties({ ...at(fixture), uid: fixture.uid, penalties: ["", "", "-4", ""], expectedVersion: 1 });

    const pending = await readHand(fixture.tournamentId, 1, 1, 1);
    assert.equal(pending.isDone, false);
    assert.deepEqual(
      [pending.playerEastPenalty, pending.playerSouthPenalty, pending.playerWestPenalty, pending.playerNorthPenalty],
      ["5;", "-15;", "5;-4", "5;"],
    );
    // The hand in play keeps its penalties when the result is saved, and they count in the totals.
    const result = await recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 2 });
    assert.deepEqual(result.handScores, [39, -23, -7, -13]);
    const saved = await readHand(fixture.tournamentId, 1, 1, 1);
    assert.equal(saved.playerWestPenalty, "5;-4");
  });

  it("refuses penalties from a player who is not at the table or with an old table version", async () => {
    const fixture = await seedTable();

    await rejects(addPenalties({ ...at(fixture), uid: fixture.otherUid, penalties: ["", "-5", "", ""], expectedVersion: 0 }), 403);
    await rejects(addPenalties({ ...at(fixture), uid: fixture.uid, penalties: ["", "-5", "", ""], expectedVersion: 7 }), 409);
  });

  it("keeps the table open when the hand is not the last one", async () => {
    const fixture = await seedTable();

    await recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 });

    assert.equal((await readTable(fixture.tournamentId, 1, 1)).isCompleted, false);
  });

  it("applies one hand when two records race with the same version", async () => {
    const fixture = await seedTable();

    const results = await Promise.allSettled([
      recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 }),
      recordHand({ ...at(fixture), uid: fixture.uid, input: hand("2", "3"), expectedVersion: 0 }),
    ]);

    assert.equal(results.filter((result) => result.status === "fulfilled").length, 1);
    assert.equal((await readTable(fixture.tournamentId, 1, 1)).version, 1);
  });
});
