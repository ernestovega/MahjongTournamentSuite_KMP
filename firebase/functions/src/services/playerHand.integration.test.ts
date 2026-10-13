import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { readChanges, readHand, readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { getTournamentManifest } from "./dataVersionsService";
import { assignSeats, parseHandInput, parseSeatAssignment as parseSeatAssignmentForTest, previewHand, recordHand } from "./playerHandService";
import { saveTableState } from "./tableManagerService";

type Fixture = { tournamentId: string; uid: string; otherUid: string };

/** A hands-mode table with seats 1..4 from east to north. Slot 1 and slot 2 have a player account. */
async function seedTable(options: { totalsOnly?: boolean; seats?: boolean } = {}): Promise<Fixture> {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
    useTotalsOnly: options.totalsOnly ?? false,
    usePointsCalculation: true,
    ...(options.seats === false ? {} : { playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4" }),
  });
  const uid = `u-${tournamentId}-1`;
  const otherUid = `u-${tournamentId}-9`;
  await db.doc(`users/${uid}/playerTournaments/${tournamentId}`).set({ playerId: 1, emaId: "E1" });
  await db.doc(`users/${otherUid}/playerTournaments/${tournamentId}`).set({ playerId: 9, emaId: "E9" });
  return { tournamentId, uid, otherUid };
}

const at = (fixture: Fixture) => ({ tournamentId: fixture.tournamentId, roundId: 1, tableId: 1 });
const hand = (winner: string, looser: string, score = "10") => ({
  playerWinnerId: winner, playerLooserId: looser, handScore: score, isChickenHand: false,
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

describe("seat assignment by a player", () => {
  before(requireEmulator);

  const seats = { east: "3", south: "1", west: "4", north: "2" };

  it("lets a player of the table assign the seats and keeps the mode of the table", async () => {
    const fixture = await seedTable({ seats: false });

    const result = await assignSeats({ ...at(fixture), uid: fixture.uid, seats, expectedVersion: 0 });

    assert.deepEqual(result.seats, ["3", "1", "4", "2"]);
    const table = await readTable(fixture.tournamentId, 1, 1);
    assert.equal(table.playerEastId, "3");
    assert.equal(table.playerNorthId, "2");
    assert.equal(table.useTotalsOnly, false);
    assert.equal(table.version, 1);
    const totalsOnly = await seedTable({ totalsOnly: true, seats: false });
    await assignSeats({ ...at(totalsOnly), uid: totalsOnly.uid, seats, expectedVersion: 0 });
    assert.equal((await readTable(totalsOnly.tournamentId, 1, 1)).useTotalsOnly, true);
    // The hand entry works right after.
    const preview = await previewHand({ ...at(fixture), uid: fixture.uid, input: hand("3", "2") });
    assert.deepEqual(preview.seats, ["3", "1", "4", "2"]);
  });

  it("refuses a player who does not play at the table", async () => {
    const fixture = await seedTable({ seats: false });

    await rejects(assignSeats({ ...at(fixture), uid: fixture.otherUid, seats, expectedVersion: 0 }), 403);
  });

  it("refuses seats with a player from another table or a repeated player", async () => {
    const fixture = await seedTable({ seats: false });

    await rejects(assignSeats({ ...at(fixture), uid: fixture.uid, seats: { ...seats, north: "7" }, expectedVersion: 0 }), 400);
    assert.throws(
      () => parseSeatAssignmentForTest({ playerEastId: "1", playerSouthId: "1", playerWestId: "3", playerNorthId: "4" }),
      HttpError,
    );
  });

  it("refuses to change the seats after a hand is saved", async () => {
    const fixture = await seedTable();
    await recordHand({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 });

    await rejects(assignSeats({ ...at(fixture), uid: fixture.uid, seats, expectedVersion: 1 }), 409);
  });

  it("refuses when an editor entered totals or the table version changed", async () => {
    const withTotals = await seedTable({ seats: false });
    await db.doc(`tournaments/${withTotals.tournamentId}/tables/1_1`).update({ playerEastScore: "10" });
    await rejects(assignSeats({ ...at(withTotals), uid: withTotals.uid, seats, expectedVersion: 0 }), 409);

    const changed = await seedTable({ seats: false });
    await rejects(assignSeats({ ...at(changed), uid: changed.uid, seats, expectedVersion: 5 }), 409);
  });
});
