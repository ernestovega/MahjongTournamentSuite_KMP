import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db, storage } from "../firebase";
import { runTournamentIntegrityCheck } from "./tournamentIntegrityService";
import { seedTournament } from "../testing/firestoreFixtures";
import { createTournament, deleteTournament, getTournament, renameTournament, updateTournamentSettings } from "./tournamentsService";

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

    await db.doc(`tournaments/${id}`).update({ numRounds: 3 });
    const roundSchedules = [1, 2, 3].map((roundId) => ({ roundId, date: "2026-10-01", startTime: `1${roundId}:00` }));
    await updateTournamentSettings({ ...settings, roundSchedules, hideResultsFromRound: 2 });
    assert.equal((await db.doc(`tournaments/${id}`).get()).get("hideResultsFromRound"), 2);
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

  it("checks a tournament whose slots hold EMA ids, known to the registry or not", async () => {
    const checked = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${checked}/players/1`).set({ id: 1, team: 0, assignedEmaId: "07720001" }, { merge: true });
    await db.doc(`tournaments/${checked}/players/2`).set({ id: 2, team: 0, assignedEmaId: "07729998" }, { merge: true });
    await db.doc("emaPlayerRegistry/07720001").set({ emaId: "07720001", firstName: "ANA", lastName: "RUIZ", country: "esp" }, { merge: true });
    const report = await runTournamentIntegrityCheck(checked);
    assert.equal(report.tournamentId, checked);
  });

  it("deletes the logo of a tournament with the tournament", async () => {
    const deleted = await seedTournament({ tables: [] });
    const logoPath = `tournamentLogos/${deleted}.png`;
    await storage.file(logoPath).save(Buffer.from("png"), { contentType: "image/png" });
    await db.doc(`tournaments/${deleted}`).update({ associationLogoPath: logoPath });
    await deleteTournament(deleted);
    assert.equal((await storage.file(logoPath).exists())[0], false);
    assert.equal((await db.doc(`tournaments/${deleted}`).get()).exists, false);
  });
});
