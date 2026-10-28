import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { giveEmaIdToAccount, seatEmaId } from "../testing/authFixtures";
import { readChanges, readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { closeRoundTables } from "./matchTimeCloseService";
import { parseHandInput, recordHand } from "./playerHandService";

type Mode = "STOP" | "FINISH_HAND";

/** A tournament in UTC whose round 1 started [startedMinutesAgo] minutes ago. The match lasts 120 minutes. */
async function seedTimedTable(startedMinutesAgo: number, mode: Mode, doneHands = 0) {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  const start = new Date(Date.now() - startedMinutesAgo * 60_000).toISOString();
  await db.doc(`tournaments/${tournamentId}`).update({
    numRounds: 1,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: mode,
    matchExtraMinutes: 15,
    roundSchedules: [{ roundId: 1, date: start.slice(0, 10), startTime: start.slice(11, 16) }],
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

  it("skips a tournament without time zone", async () => {
    const { tournamentId } = await seedTimedTable(200, "STOP", 3);
    await db.doc(`tournaments/${tournamentId}`).update({ timeZone: null });

    await closeRoundTables(tournamentId, 1);

    assert.equal((await readTable(tournamentId, 1, 1)).isCompleted, false);
  });
});
