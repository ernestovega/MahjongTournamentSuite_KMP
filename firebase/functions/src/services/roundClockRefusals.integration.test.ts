import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { completeTables, getRoundClocks, updateRoundClock, updateRoundControl } from "./roundClockService";

const notFound = (pattern: RegExp) => (error: unknown) => (error as { status?: number }).status === 404 && pattern.test(String((error as Error).message));

/** What the round clock and the round control refuse: a tournament, a round or a table that does not exist. */
describe("round clock refusals", () => {
  before(requireEmulator);

  it("refuses a tournament that does not exist", async () => {
    await assert.rejects(getRoundClocks("no-such-tournament"), notFound(/Tournament not found/));
    await assert.rejects(updateRoundClock({ tournamentId: "no-such-tournament", roundId: 1, action: { kind: "pause", agoMs: 0 }, actor: null }), notFound(/Tournament not found/));
    await assert.rejects(updateRoundClock({ tournamentId: "no-such-tournament", roundId: 2, action: { kind: "start", agoMs: 0 }, actor: null }), notFound(/Tournament not found/));
    await assert.rejects(updateRoundControl({ tournamentId: "no-such-tournament", roundId: 1, change: { hands: "auto" }, actor: null }), notFound(/Tournament not found/));
    await assert.rejects(completeTables({ tournamentId: "no-such-tournament", tables: [{ roundId: 1, tableId: 1 }], actor: null }), notFound(/Tournament not found/));
  });

  it("refuses a round after the last one and a table that does not exist", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${tournamentId}`).update({ numRounds: 1 });
    await assert.rejects(updateRoundClock({ tournamentId, roundId: 2, action: { kind: "pause", agoMs: 0 }, actor: null }), notFound(/Round not found/));
    await assert.rejects(updateRoundControl({ tournamentId, roundId: 2, change: { hands: "auto" }, actor: null }), notFound(/Round not found/));
    await assert.rejects(completeTables({ tournamentId, tables: [{ roundId: 2, tableId: 1 }], actor: null }), notFound(/Round 2 not found/));
    await assert.rejects(completeTables({ tournamentId, tables: [{ roundId: 1, tableId: 9 }], actor: null }), notFound(/Table 9 not found/));
  });

  it("has no rounds in a tournament without a number of rounds", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }] });
    await db.doc(`tournaments/${tournamentId}`).update({ numRounds: FieldValue.delete() });
    await updateRoundClock({ tournamentId, roundId: 2, action: { kind: "start", agoMs: 0 }, actor: null }).catch(() => undefined);
    await assert.rejects(updateRoundClock({ tournamentId, roundId: 1, action: { kind: "pause", agoMs: 0 }, actor: null }), notFound(/Round not found/));
    await assert.rejects(updateRoundControl({ tournamentId, roundId: 1, change: { hands: "auto" }, actor: null }), notFound(/Round not found/));
    await assert.rejects(completeTables({ tournamentId, tables: [{ roundId: 1, tableId: 1 }], actor: null }), notFound(/Round 1 not found/));
  });

  it("completes a table that has no version yet", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${tournamentId}`).update({ numRounds: 1 });
    await db.doc(`tournaments/${tournamentId}/tables/1_1`).update({ version: FieldValue.delete() });
    assert.deepEqual(await completeTables({ tournamentId, tables: [{ roundId: 1, tableId: 1 }], actor: null }), { completed: 1 });
  });
});
