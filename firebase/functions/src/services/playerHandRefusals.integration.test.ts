import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { giveEmaIdToAccount, seatEmaId } from "../testing/authFixtures";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { startedClock } from "../testing/schedule";
import { setRoundCloseEnqueuer } from "./matchTimeTaskService";
import { addPenalties, assignSeats, parseHandInput, parsePenaltyInput, parseSeatsInput, previewHand, recordHand } from "./playerHandService";

/** A table in play whose seats 1..4 sit from east to north, and the account of slot 1. */
async function seedTable(): Promise<{ tournamentId: string; uid: string }> {
  const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  await db.doc(`tournaments/${tournamentId}`).update({
    numRounds: 1, timeZone: "UTC", matchDurationMinutes: 120, matchEndMode: "STOP", matchExtraMinutes: 15,
    roundClocks: { 1: startedClock(10) },
  });
  await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
    useTotalsOnly: false, playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
  });
  const uid = `u-${tournamentId}-refusals`;
  await giveEmaIdToAccount(uid, `R${tournamentId.slice(0, 6)}`);
  await seatEmaId(tournamentId, 1, `R${tournamentId.slice(0, 6)}`);
  return { tournamentId, uid };
}

const hand = { playerWinnerId: "1", playerLooserId: "4", handScore: "10", isChickenHand: false, isDraw: false, isLastHand: false };

async function rejects(promise: Promise<unknown>, status: number, code?: string): Promise<void> {
  await assert.rejects(promise, (error: unknown) => {
    assert.ok(error instanceof HttpError, String(error));
    assert.equal(error.status, status, error.message);
    if (code != null) assert.equal((error.details as { code?: string } | undefined)?.code, code);
    return true;
  });
}

/** The refusals of the hand entry of a player that the other tests do not reach. */
describe("hand entry refusals", () => {
  before(requireEmulator);
  before(() => setRoundCloseEnqueuer(async () => undefined));
  after(() => setRoundCloseEnqueuer());

  it("refuses a table that does not exist", async () => {
    const table = await seedTable();
    await rejects(previewHand({ tournamentId: table.tournamentId, roundId: 1, tableId: 9, uid: table.uid, input: hand }), 404);
  });

  it("refuses a hand when every hand of the table is done but the table is still open", async () => {
    const table = await seedTable();
    const hands = await db.collection(`tournaments/${table.tournamentId}/tables/1_1/hands`).get();
    const batch = db.batch();
    hands.docs.forEach((doc) => batch.update(doc.ref, { isDone: true }));
    await batch.commit();

    const at = { tournamentId: table.tournamentId, roundId: 1, tableId: 1, uid: table.uid };
    await rejects(previewHand({ ...at, input: hand }), 409, "ALL_HANDS_DONE");
    await rejects(addPenalties({ ...at, penalties: ["", "-5", "", ""], expectedVersion: 0 }), 409, "ALL_HANDS_DONE");
  });

  it("keeps at most five penalties in a hand", async () => {
    const table = await seedTable();
    const at = { tournamentId: table.tournamentId, roundId: 1, tableId: 1, uid: table.uid };
    assert.throws(() => parsePenaltyInput({ playerSouthPenalty: "-1;-1;-1;-1;-1;-1" }), HttpError);

    await addPenalties({ ...at, penalties: ["", "-5;-5;-5", "", ""], expectedVersion: 0 });
    await rejects(addPenalties({ ...at, penalties: ["", "-5;-5;-5", "", ""], expectedVersion: 1 }), 409, "TOO_MANY_PENALTIES");
  });

  it("refuses a record of a version that is not the one of the review", async () => {
    const table = await seedTable();
    await rejects(
      recordHand({ tournamentId: table.tournamentId, roundId: 1, tableId: 1, uid: table.uid, input: hand, expectedVersion: 5 }),
      409,
      "TABLE_CHANGED_TOTALS",
    );
  });

  it("reads a request that is not an object as empty", () => {
    assert.throws(() => parseHandInput(null), /A winner is required/);
    assert.equal(parseHandInput({ playerWinnerId: "1", playerLooserId: "-", handScore: "8" }).playerLooserId, "");
    assert.throws(() => parsePenaltyInput(null), /A penalty is required/);
    assert.throws(() => parseSeatsInput(null), /four seats need a player/);
  });

  it("refuses a loser who does not play at the table", async () => {
    const table = await seedTable();
    await rejects(previewHand({ tournamentId: table.tournamentId, roundId: 1, tableId: 1, uid: table.uid, input: { ...hand, playerLooserId: "9" } }), 400);
  });

  it("takes a table without its version and totals switch as version 0 with hands", async () => {
    const table = await seedTable();
    await db.doc(`tournaments/${table.tournamentId}/tables/1_1`).update({ version: FieldValue.delete(), useTotalsOnly: FieldValue.delete() });
    const at = { tournamentId: table.tournamentId, roundId: 1, tableId: 1, uid: table.uid };
    assert.equal((await previewHand({ ...at, input: hand })).tableVersion, 0);
    assert.equal((await recordHand({ ...at, input: hand, expectedVersion: 0 })).tableVersion, 1);
  });

  it("refuses the seats of a table without its drawn players", async () => {
    const table = await seedTable();
    await db.doc(`tournaments/${table.tournamentId}/tables/1_1`).update({ playerIds: FieldValue.delete() });
    await rejects(
      assignSeats({ tournamentId: table.tournamentId, roundId: 1, tableId: 1, uid: table.uid, seats: ["1", "2", "3", "4"], expectedVersion: 0 }),
      403,
    );
  });
});

