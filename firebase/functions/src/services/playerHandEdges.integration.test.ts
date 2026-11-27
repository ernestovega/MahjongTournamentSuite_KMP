import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { giveEmaIdToAccount, seatEmaId } from "../testing/authFixtures";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { startedClock } from "../testing/schedule";
import { addPenalties, assignSeats, parsePenaltyInput } from "./playerHandService";

/** A table of round 1, whose round started [startedMinutesAgo] minutes ago, with the account of the player of slot 1. */
async function playingTable(startedMinutesAgo: number, seated = true) {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  await db.doc(`tournaments/${tournamentId}`).update({
    numRounds: 1, timeZone: "UTC", matchDurationMinutes: 120, matchEndMode: "STOP", matchExtraMinutes: 15,
    roundClocks: { 1: startedClock(startedMinutesAgo) },
  });
  if (seated) {
    await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
      playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
    });
  }
  const uid = `u-${tournamentId}-1`;
  await giveEmaIdToAccount(uid, "E1");
  await seatEmaId(tournamentId, 1, "E1");
  return { tournamentId, uid, roundId: 1, tableId: 1 };
}

const codeOf = (code: string) => (error: unknown) => (error as { details?: { code?: string } }).details?.code === code;

/** What a phone sends after the table changed under it. */
describe("hands, penalties and seats of a player that do not fit the table", () => {
  before(requireEmulator);

  it("refuses a penalty and seats for a version of the table that the phone does not have", async () => {
    const table = await playingTable(10);
    await assert.rejects(
      addPenalties({ ...table, penalties: parsePenaltyInput({ playerEastPenalty: "-10" }), expectedVersion: 7 }),
      codeOf("TABLE_CHANGED_PENALTY"),
    );
    const unseated = await playingTable(10, false);
    await assert.rejects(assignSeats({ ...unseated, seats: ["1", "2", "3", "4"], expectedVersion: 7 }), codeOf("TABLE_CHANGED_SEATS"));
  });

  it("adds penalties after the parts that a hand of an older app has, on a table without a version", async () => {
    const table = await playingTable(10);
    await db.doc(`tournaments/${table.tournamentId}/tables/1_1`).update({ version: FieldValue.delete() });
    await db.doc(`tournaments/${table.tournamentId}/tables/1_1/hands/1`).update({
      playerEastPenalty: "-10;-5", playerSouthPenalty: FieldValue.delete(),
    });
    await addPenalties({ ...table, penalties: parsePenaltyInput({ playerEastPenalty: "-5", playerSouthPenalty: "0" }), expectedVersion: 0 });
    const hand = (await db.doc(`tournaments/${table.tournamentId}/tables/1_1/hands/1`).get()).data();
    assert.equal(hand?.playerEastPenalty, "-10;-5;-5");
    assert.equal(hand?.playerSouthPenalty, "");
  });

  it("sets the seats of a table without a version, whose hands have no penalty fields", async () => {
    const table = await playingTable(10, false);
    await db.doc(`tournaments/${table.tournamentId}/tables/1_1`).update({ version: FieldValue.delete() });
    const hands = await db.collection(`tournaments/${table.tournamentId}/tables/1_1/hands`).get();
    const noPenalties = Object.fromEntries(["playerEastPenalty", "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty"].map((field) => [field, FieldValue.delete()]));
    await Promise.all(hands.docs.map((doc) => doc.ref.update(noPenalties)));
    assert.deepEqual(await assignSeats({ ...table, seats: ["1", "2", "3", "4"], expectedVersion: 0 }), { tableVersion: 1 });
  });

  it("finds no round in play when the tournament document is gone", async () => {
    const table = await playingTable(10);
    await db.doc(`tournaments/${table.tournamentId}`).delete();
    await assert.rejects(
      addPenalties({ ...table, penalties: parsePenaltyInput({ playerEastPenalty: "-5" }), expectedVersion: 0 }),
      codeOf("ROUND_NOT_STARTED"),
    );
  });
});
