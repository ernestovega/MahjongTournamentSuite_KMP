import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { db } from "../firebase";
import { validDrawTables } from "../testing/draw";
import { makeDrawFromRegistrations } from "./registrationDraw";
import * as tournamentContentService from "./tournamentContentService";

const codeOf = (error: unknown) => (error as { details?: { code?: string } }).details?.code;

/** A tournament of 16 confirmed people whose registrations are closed, written by hand. */
async function closedTournament(tag: string) {
  const ref = db.doc(`tournaments/draw-failures-${tag}-${Date.now()}`);
  await ref.set({ name: "Draw", isTeams: false, drawPending: true, numRounds: 2, registration: { status: "CLOSED" } });
  const people = Array.from({ length: 16 }, (_, index) => `0773${tag}${String(index).padStart(2, "0")}`);
  await Promise.all(people.map((emaId, index) => db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Person", lastName: String(index + 1), country: "ES" })));
  await Promise.all(people.map((emaId, index) => ref.collection("registrations").doc(emaId).set({
    status: "CONFIRMED", order: index + 1, name: `Person ${index + 1}`, country: "ES", email: "",
  })));
  const draw = {
    tournamentId: ref.id, numTries: 1, uid: null,
    players: people.map((_, index) => ({ id: index + 1, team: 0 })),
    tables: validDrawTables(16, 2),
    assignments: people.map((emaId, index) => ({ playerId: index + 1, emaId })),
  };
  return { ref, draw };
}

describe("draw of the registrations that fails", () => {
  it("refuses a draw of the wrong size, a wrong draw, and a tournament without rounds", async () => {
    const { ref, draw } = await closedTournament("10");
    await assert.rejects(makeDrawFromRegistrations({ ...draw, players: draw.players.slice(1) }), (error) => codeOf(error) === "BAD_DRAW");
    const repeated = draw.tables.map((table, index) => (index === 1 ? { ...draw.tables[0], roundId: table.roundId, tableId: table.tableId } : table));
    await assert.rejects(makeDrawFromRegistrations({ ...draw, tables: repeated }), (error) => codeOf(error) === "BAD_DRAW");
    await ref.update({ numRounds: null });
    await assert.rejects(makeDrawFromRegistrations(draw), (error) => codeOf(error) === "BAD_DRAW");
  });

  it("goes back to no draw when a step fails", async () => {
    const { ref, draw } = await closedTournament("20");
    const failing = mock.method(tournamentContentService, "overwriteTournamentPlayers", async () => {
      throw new Error("slots unavailable");
    });
    try {
      await assert.rejects(makeDrawFromRegistrations(draw), /slots unavailable/);
    } finally {
      failing.mock.restore();
    }
    assert.equal((await ref.get()).get("drawPending"), true);
    assert.equal((await ref.collection("tables").get()).size, 0);
    assert.equal((await ref.collection("players").get()).size, 0);

    const made = await makeDrawFromRegistrations(draw);
    assert.equal(made.numPlayers, 16);
  });
});
