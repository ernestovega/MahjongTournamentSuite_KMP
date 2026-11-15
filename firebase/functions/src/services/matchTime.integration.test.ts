import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { giveEmaIdToAccount, seatEmaId } from "../testing/authFixtures";
import { readChanges, readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { startedClock } from "../testing/schedule";
import { closeRoundTables } from "./matchTimeCloseService";
import { assignSeats, parseHandInput, recordHand } from "./playerHandService";
import { updateRoundControl } from "./roundClockService";

type Mode = "STOP" | "FINISH_HAND";

/** A tournament whose Timer started round 1 [startedMinutesAgo] minutes ago. The match lasts 120 minutes. */
async function seedTimedTable(startedMinutesAgo: number, mode: Mode, doneHands = 0) {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  await db.doc(`tournaments/${tournamentId}`).update({
    numRounds: 1,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: mode,
    matchExtraMinutes: 15,
    roundClocks: { 1: startedClock(startedMinutesAgo) },
  });
  await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
    playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4", hasProgress: doneHands > 0,
  });
  for (let handId = 1; handId <= doneHands; handId++) {
    await db.doc(`tournaments/${tournamentId}/tables/1_1/hands/${handId}`).update({
      playerWinnerId: "1", playerLooserId: "2", handScore: "8", isDone: true,
    });
  }
  const uid = `u-${tournamentId}-1`;
  await giveEmaIdToAccount(uid, "E1");
  await seatEmaId(tournamentId, 1, "E1");
  return { tournamentId, uid };
}

const win = () => parseHandInput({ playerWinnerId: "1", playerLooserId: "4", handScore: "10" });

describe("match time", () => {
  before(requireEmulator);

  it("refuses a hand of a player when the time is over", async () => {
    const { tournamentId, uid } = await seedTimedTable(200, "STOP");

    await assert.rejects(
      recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 }),
      (error: unknown) => error instanceof HttpError && error.status === 409,
    );
  });

  it("makes the hand in extra time the last hand of the table", async () => {
    const { tournamentId, uid } = await seedTimedTable(125, "FINISH_HAND");

    await recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });

    assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, true);
  });

  it("accepts a hand while the match is playing", async () => {
    const { tournamentId, uid } = await seedTimedTable(30, "STOP");

    await recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });

    assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, false);
  });

  it("completes an expired table with played hands, once", async () => {
    const { tournamentId } = await seedTimedTable(200, "STOP", 3);

    const first = await closeRoundTables(tournamentId, 1);
    assert.equal(first.closed, 1);
    const table = await readTable(tournamentId, 1, 1);
    assert.equal(table.isCompleted, true);
    const changes = await readChanges(tournamentId);
    assert.equal(changes[changes.length - 1]?.source, "system");

    // An organizer opens the table again to add the missing data. The server does not close it again.
    await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({ isCompleted: false });
    await closeRoundTables(tournamentId, 1);
    assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, false);
  });

  it("leaves a table without played hands and a table in extra time open", async () => {
    const empty = await seedTimedTable(200, "STOP", 0);
    const extra = await seedTimedTable(125, "FINISH_HAND", 3);

    await closeRoundTables(empty.tournamentId, 1);
    await closeRoundTables(extra.tournamentId, 1);

    assert.equal((await readTable(empty.tournamentId, 1, 1)).isCompleted, false);
    assert.equal((await readTable(extra.tournamentId, 1, 1)).isCompleted, false);
  });

  it("completes a table in extra time once the extra time is over", async () => {
    // The match lasts 120 minutes and the extra time 15 minutes, so 130 minutes is still extra time and 140 is not.
    const inExtra = await seedTimedTable(130, "FINISH_HAND", 3);
    const afterExtra = await seedTimedTable(140, "FINISH_HAND", 3);

    await closeRoundTables(inExtra.tournamentId, 1);
    await closeRoundTables(afterExtra.tournamentId, 1);

    assert.equal((await readTable(inExtra.tournamentId, 1, 1)).isCompleted, false);
    assert.equal((await readTable(afterExtra.tournamentId, 1, 1)).isCompleted, true);
  });

  it("leaves a table open while its match is still playing", async () => {
    const { tournamentId } = await seedTimedTable(30, "STOP", 3);

    await closeRoundTables(tournamentId, 1);

    assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, false);
  });

  it("refuses a hand before the Play of the Timer, even after the planned time", async () => {
    const { tournamentId, uid } = await seedTimedTable(30, "STOP");
    const planned = new Date(Date.now() - 60 * 60_000).toISOString();
    await db.doc(`tournaments/${tournamentId}`).update({
      roundClocks: {},
      roundSchedules: [{ roundId: 1, date: planned.slice(0, 10), startTime: planned.slice(11, 16) }],
    });

    await assert.rejects(
      recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 }),
      (error: unknown) => error instanceof HttpError && error.status === 409 && error.message.includes("starting gong"),
    );
  });

  it("does not close a round that has not started or is paused", async () => {
    const notStarted = await seedTimedTable(200, "STOP", 3);
    await db.doc(`tournaments/${notStarted.tournamentId}`).update({ roundClocks: {} });
    const paused = await seedTimedTable(200, "STOP", 3);
    const clock = startedClock(200);
    await db.doc(`tournaments/${paused.tournamentId}`).update({
      roundClocks: { 1: { ...clock, pausedAtMs: clock.startedAtMs + 60 * 60_000 } },
    });

    await closeRoundTables(notStarted.tournamentId, 1);
    await closeRoundTables(paused.tournamentId, 1);

    assert.equal((await readTable(notStarted.tournamentId, 1, 1)).isCompleted, false);
    assert.equal((await readTable(paused.tournamentId, 1, 1)).isCompleted, false);
  });

  describe("manual control (plan B of the Timer)", () => {
    const control = (tournamentId: string, change: Parameters<typeof updateRoundControl>[0]["change"]) =>
      updateRoundControl({ tournamentId, roundId: 1, change, actor: null });
    const refused = (error: unknown) => error instanceof HttpError && error.status === 409;

    it("an open round takes hands before the Play and after the end of the time", async () => {
      const before = await seedTimedTable(30, "STOP");
      await db.doc(`tournaments/${before.tournamentId}`).update({ roundClocks: {} });
      const after = await seedTimedTable(200, "STOP");

      await control(before.tournamentId, { hands: "open" });
      await control(after.tournamentId, { hands: "open" });

      await recordHand({ tournamentId: before.tournamentId, roundId: 1, tableId: 1, uid: before.uid, input: win(), expectedVersion: 0 });
      await recordHand({ tournamentId: after.tournamentId, roundId: 1, tableId: 1, uid: after.uid, input: win(), expectedVersion: 0 });
      assert.equal((await readTable(after.tournamentId, 1, 1)).isCompleted, false);
    });

    it("a closed round refuses hands while the clock runs, and auto gives the clock back", async () => {
      const { tournamentId, uid } = await seedTimedTable(30, "STOP");
      await control(tournamentId, { hands: "closed" });

      await assert.rejects(recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 }), refused);

      await control(tournamentId, { hands: "auto" });
      await recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });
    });

    it("a table control wins over the round", async () => {
      const { tournamentId, uid } = await seedTimedTable(200, "STOP");
      await control(tournamentId, { hands: "closed", table: { tableId: 1, hands: "open" } });

      await recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });
    });

    it("an open table in extra time is not forced to its last hand", async () => {
      const { tournamentId, uid } = await seedTimedTable(125, "FINISH_HAND");
      await control(tournamentId, { table: { tableId: 1, hands: "open" } });

      await recordHand({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });

      assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, false);
    });

    it("the end of the time does not close an open table, and a closed table opens again", async () => {
      const open = await seedTimedTable(200, "STOP", 3);
      await control(open.tournamentId, { hands: "open" });
      await closeRoundTables(open.tournamentId, 1);
      assert.equal((await readTable(open.tournamentId, 1, 1)).isCompleted, false);

      const closed = await seedTimedTable(200, "STOP", 3);
      await closeRoundTables(closed.tournamentId, 1);
      assert.equal((await readTable(closed.tournamentId, 1, 1)).isCompleted, true);
      const answer = await control(closed.tournamentId, { reopenTables: [1], table: { tableId: 1, hands: "open" } });
      assert.equal(answer.reopened, 1);
      assert.equal((await readTable(closed.tournamentId, 1, 1)).isCompleted, false);
      assert.deepEqual(answer.roundControls, [{ roundId: 1, hands: "auto", seats: "auto", tables: [{ tableId: 1, hands: "open" }] }]);
      await recordHand({ tournamentId: closed.tournamentId, roundId: 1, tableId: 1, uid: closed.uid, input: win(), expectedVersion: 2 });
    });

    it("open seats ignore the planned start, closed seats refuse them", async () => {
      const early = await seedTimedTable(30, "STOP");
      const later = new Date(Date.now() + 5 * 60 * 60_000).toISOString();
      await db.doc(`tournaments/${early.tournamentId}`).update({
        roundClocks: {},
        roundSchedules: [{ roundId: 1, date: later.slice(0, 10), startTime: later.slice(11, 16) }],
      });
      const seats: [string, string, string, string] = ["2", "1", "3", "4"];
      await assert.rejects(assignSeats({ tournamentId: early.tournamentId, roundId: 1, tableId: 1, uid: early.uid, seats, expectedVersion: 0 }), refused);
      await control(early.tournamentId, { seats: "open" });
      await assignSeats({ tournamentId: early.tournamentId, roundId: 1, tableId: 1, uid: early.uid, seats, expectedVersion: 0 });

      const playing = await seedTimedTable(30, "STOP");
      await control(playing.tournamentId, { seats: "closed" });
      await assert.rejects(assignSeats({ tournamentId: playing.tournamentId, roundId: 1, tableId: 1, uid: playing.uid, seats, expectedVersion: 0 }), refused);
    });
  });
});
