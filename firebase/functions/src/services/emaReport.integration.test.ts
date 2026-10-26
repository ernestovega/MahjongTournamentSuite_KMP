import assert from "node:assert/strict";
import { before, describe, it } from "node:test";
import ExcelJS = require("exceljs");

import { db } from "../firebase";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { generateEmaReport } from "./emaReportService";

describe("EMA report with a slot that has no person", () => {
  before(requireEmulator);

  it("leaves out the slot and keeps the places of the other players", async () => {
    const id = await seedTournament({ tables: [] });
    const tournament = db.collection("tournaments").doc(id);
    await tournament.update({ name: "Test", numPlayers: 3, eventStartDate: "2026-10-16", eventEndDate: "2026-10-17", hostCountry: "ESP", hostCity: "Madrid", shortName: "TEST" });
    for (const [emaId, firstName] of [["10000001", "ANA"], ["10000002", "BEA"]]) {
      await db.collection("emaPlayerRegistry").doc(emaId).set({ firstName, lastName: "LOPEZ", country: "ESP" });
    }
    await tournament.collection("players").doc("1").set({ id: 1, assignedEmaId: "10000001" });
    await tournament.collection("players").doc("2").set({ id: 2, assignedEmaId: null });
    await tournament.collection("players").doc("3").set({ id: 3, assignedEmaId: "10000002" });

    const bytes = await generateEmaReport({
      tournamentId: id,
      rows: [
        { playerId: 1, place: 1, tablePoints: 10, score: 100 },
        { playerId: 2, place: 2, tablePoints: 8, score: 50 },
        { playerId: 3, place: 3, tablePoints: 4, score: -20 },
      ],
    });

    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(bytes as unknown as ExcelJS.Buffer);
    const sheet = workbook.getWorksheet("MCR template");
    assert.ok(sheet);
    assert.equal(sheet.rowCount, 3);
    assert.equal(sheet.getCell("C2").value, 1);
    assert.equal(sheet.getCell("C3").value, 3);
  });

  it("leaves out a player that the head referee excluded after an illness, and keeps a disqualified player", async () => {
    const id = await seedTournament({ tables: [] });
    const tournament = db.collection("tournaments").doc(id);
    await tournament.update({
      name: "Test", numPlayers: 3, eventStartDate: "2026-10-16", eventEndDate: "2026-10-17",
      hostCountry: "ESP", hostCity: "Madrid", shortName: "TEST",
    });
    for (const [emaId, firstName] of [["10000001", "ANA"], ["10000002", "BEA"], ["10000003", "CLARA"]]) {
      await db.collection("emaPlayerRegistry").doc(emaId).set({ firstName, lastName: "LOPEZ", country: "ESP" });
    }
    await tournament.collection("players").doc("1").set({
      id: 1, assignedEmaId: "10000001",
      substitution: { reason: "ILLNESS", fromRound: 2, duringSession: false, excludedFromEma: true },
    });
    await tournament.collection("players").doc("2").set({
      id: 2, assignedEmaId: "10000002",
      substitution: { reason: "DISQUALIFIED", fromRound: 2, duringSession: false, excludedFromEma: false },
    });
    await tournament.collection("players").doc("3").set({ id: 3, assignedEmaId: "10000003" });

    const bytes = await generateEmaReport({
      tournamentId: id,
      rows: [
        { playerId: 1, place: 1, tablePoints: 10, score: 100 },
        { playerId: 3, place: 2, tablePoints: 8, score: 50 },
        { playerId: 2, place: 3, tablePoints: 0, score: 0 },
      ],
    });

    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(bytes as unknown as ExcelJS.Buffer);
    const sheet = workbook.getWorksheet("MCR template");
    assert.ok(sheet);
    assert.equal(sheet.rowCount, 3);
    assert.equal(sheet.getCell("D2").value, "Clara");
    assert.equal(sheet.getCell("D3").value, "Bea");
    assert.equal(sheet.getCell("G3").value, 0);
  });
});
