import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db } from "../firebase";
import { createTournament, getTournament, renameTournament, updateTournamentSettings } from "./tournamentsService";

/** Tournaments read from documents with missing fields, and the refusals of their creation and their settings. */
describe("tournaments with malformed documents", () => {
  const id = `tournaments-malformed-${Date.now()}`;

  it("reads a tournament without its fields, created by an account without an email", async () => {
    await db.doc("users/tournaments-malformed-creator").set({ email: "" });
    await db.doc(`tournaments/${id}`).set({ createdByUid: "tournaments-malformed-creator" });
    const tournament = await getTournament(id);
    assert.equal(tournament?.createdByName, "");
    assert.equal(tournament?.hostCountry, "");
    assert.equal(tournament?.isCompleted, false);
    assert.equal(tournament?.hasTableData, false);
    assert.equal(await getTournament(`${id}-missing`), null);
  });

  it("refuses a rename without a name or a tournament", async () => {
    await assert.rejects(renameTournament(id, " "), /name is required/);
    await assert.rejects(renameTournament(`${id}-missing`, "Open"), /Tournament not found/);
  });

  it("refuses settings without a name, a short name or valid dates, and saves the optional ones", async () => {
    const settings = { tournamentId: id, name: "Open", shortName: "OPEN", eventStartDate: "2026-10-01", eventEndDate: "2026-10-02", hostCountry: "es", hostCity: "Madrid" };
    await assert.rejects(updateTournamentSettings({ ...settings, name: " " }), /name is required/);
    await assert.rejects(updateTournamentSettings({ ...settings, shortName: " " }), /short name is required/);
    await assert.rejects(updateTournamentSettings({ ...settings, shortName: "ELEVEN CHAR" }), /at most 10 characters/);
    await assert.rejects(updateTournamentSettings({ ...settings, eventEndDate: "2026-09-30" }), /Tournament dates/);
    await assert.rejects(updateTournamentSettings({ ...settings, tournamentId: `${id}-missing` }), /Tournament not found/);

    const saved = await updateTournamentSettings({ ...settings, countBestHands: false, countChickenHands: false, isPublic: false, agendaItems: [] });
    assert.deepEqual([saved.countBestHands, saved.countChickenHands, saved.isPublic, saved.hostCountry], [false, false, false, "ES"]);
  });

  it("refuses a creation whose players do not match, or a draw with registrations", async () => {
    const base = {
      name: "Wrong", shortName: "W", eventStartDate: "2026-10-01", eventEndDate: "2026-10-01", hostCountry: "ES", hostCity: "Madrid",
      isTeams: false, numPlayers: 4, numRounds: 1, numTries: 0, tables: [], createdByUid: "creator",
    };
    await assert.rejects(createTournament({ ...base, players: [] }), /Invalid players payload/);
    await assert.rejects(
      createTournament({ ...base, players: [{ id: 1, team: 0 }], registration: { maxPlayers: null } }),
      /registrations has no draw yet/,
    );
  });
});
