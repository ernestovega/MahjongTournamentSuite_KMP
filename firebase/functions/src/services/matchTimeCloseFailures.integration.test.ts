import assert from "node:assert/strict";
import { before, describe, it, mock } from "node:test";

import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { startedClock } from "../testing/schedule";
import { closeRoundsEndedByChange, closeRoundTables } from "./matchTimeCloseService";

const timed = (startedMinutesAgo: number) => ({
  numRounds: 1, timeZone: "UTC", matchDurationMinutes: 120, matchEndMode: "STOP", matchExtraMinutes: 15,
  roundClocks: { 1: startedClock(startedMinutesAgo) },
});

/** The close at the end of the time, for a table with only penalties and for a store that fails. */
describe("closing tables at the end of the time", () => {
  before(requireEmulator);

  it("marks a table with progress but no done hand as checked, and leaves it open", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${tournamentId}`).update(timed(200));
    await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({ hasProgress: true });
    await db.doc(`tournaments/${tournamentId}/tables/1_1/hands/1`).update({ playerEastPenalty: "-10" });

    assert.deepEqual(await closeRoundTables(tournamentId, 1), { tournaments: 1, closed: 0 });
    const table = await readTable(tournamentId, 1, 1);
    assert.equal(table.isCompleted, false);
    assert.equal(table.matchTimeChecked, true);
  });

  it("logs a round that cannot be closed after a change and goes on", async () => {
    const errors: unknown[] = [];
    const quiet = mock.method(console, "error", (...args: unknown[]) => { errors.push(args); });
    const broken = mock.method(db, "doc", () => {
      throw new Error("store unavailable");
    });
    try {
      const closed = await closeRoundsEndedByChange({
        tournamentId: "t-broken", before: { numRounds: 1 }, after: timed(200), nowMs: Date.now(),
      });
      assert.equal(closed, 0);
      assert.equal(errors.length, 1);
    } finally {
      broken.mock.restore();
      quiet.mock.restore();
    }
  });

  it("has no round to close without a number of rounds, and closes a table that has no version yet", async () => {
    const bare = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const { numRounds: _omitted, ...withoutRounds } = timed(200);
    await db.doc(`tournaments/${bare}`).update(withoutRounds);
    assert.deepEqual(await closeRoundTables(bare, 1), { tournaments: 0, closed: 0 });
    assert.equal(await closeRoundsEndedByChange({ tournamentId: bare, before: {}, after: withoutRounds, nowMs: Date.now() }), 0);

    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], withHands: true });
    await db.doc(`tournaments/${tournamentId}`).update(timed(200));
    await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({ hasProgress: true, version: FieldValue.delete() });
    await db.doc(`tournaments/${tournamentId}/tables/1_1/hands/1`).update({ playerWinnerId: "1", playerLooserId: "2", handScore: "8", isDone: true });
    assert.deepEqual(await closeRoundTables(tournamentId, 1), { tournaments: 1, closed: 1 });
    assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, true);
  });
});
