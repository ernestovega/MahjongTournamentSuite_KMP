import assert from "node:assert/strict";
import { describe, it } from "node:test";

import ExcelJS = require("exceljs");

import { db } from "../firebase";
import { generateEmaReport } from "./emaReportService";

/** The EMA report of a tournament whose documents miss fields, and of rankings that do not match its slots. */
describe("EMA report of malformed documents", () => {
  const tournamentId = `ema-report-malformed-${Date.now()}`;
  const ref = db.doc(`tournaments/${tournamentId}`);
  const row = (playerId: number, place: number) => ({ playerId, place, tablePoints: 4, score: 10 });

  it("fills the missing fields and leaves out the slots without a person", async () => {
    await ref.set({ eventStartDate: "2026-10-01", eventEndDate: "2026-10-01", hostCountry: "ES", hostCity: "Madrid" });
    await ref.collection("players").doc("1").set({ assignedEmaId: "07710001" });
    await ref.collection("players").doc("2").set({ id: 2 });
    await ref.collection("players").doc("3").set({ id: 3, assignedEmaId: "07710003", substitution: { reason: "ILLNESS", fromRound: 1, excludedFromEma: true } });
    await db.doc("emaPlayerRegistry/07710001").set({ firstName: "Ana", lastName: "Gil" });

    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await generateEmaReport({ tournamentId, rows: [row(1, 1), row(2, 2), row(3, 3)] }) as unknown as ExcelJS.Buffer);
    const sheet = workbook.getWorksheet("MCR template");
    assert.ok(sheet);
    assert.equal(sheet.getCell("E2").value, "GIL");
    assert.equal(sheet.getRow(3).getCell(5).value, null, "only one player is in the report");
  });

  it("refuses a ranking with an unknown slot or a person no longer in the registry", async () => {
    await assert.rejects(generateEmaReport({ tournamentId: `${tournamentId}-missing`, rows: [] }), /Tournament not found/);
    await assert.rejects(generateEmaReport({ tournamentId, rows: [row(99, 1)] }), /unknown tournament player/);
    await ref.collection("players").doc("4").set({ id: 4, assignedEmaId: "07710004" });
    await assert.rejects(generateEmaReport({ tournamentId, rows: [row(4, 1)] }), /Assigned EMA player does not exist/);
  });
});
