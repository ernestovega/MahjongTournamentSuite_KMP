import assert from "node:assert/strict";
import test from "node:test";
import ExcelJS = require("exceljs");

import { buildEmaReport, EMA_REPORT_HEADERS } from "./emaReportService";

test("EMA report keeps exact columns and typed Excel values", async () => {
  const bytes = await buildEmaReport({
    tournamentName: "Madrid MCR 2025 (5th)",
    participantCount: 100,
    startDate: "2025-02-01",
    endDate: "2025-02-02",
    hostCountry: "ESP",
    hostCity: "Madrid",
    shortName: "MMC2025",
    rows: [{
      place: 1,
      firstName: "ANTON",
      lastName: "KÖSTERS",
      emaId: "8010039",
      tablePoints: 24,
      score: 312,
      isEmaMember: true,
      country: "NED",
    }],
  });
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(bytes as unknown as ExcelJS.Buffer);
  const sheet = workbook.getWorksheet("MCR template");
  assert.ok(sheet);
  const headerValues = sheet.getRow(1).values as ExcelJS.CellValue[];
  assert.deepEqual(headerValues.slice(1), [...EMA_REPORT_HEADERS]);
  assert.equal(sheet.getCell("F2").value, "08010039");
  assert.equal(sheet.getCell("F2").numFmt, "@");
  assert.equal(sheet.getCell("D2").value, "Anton");
  assert.equal(sheet.getCell("E2").value, "KÖSTERS");
  assert.ok(sheet.getCell("K2").value instanceof Date);
  assert.equal(sheet.getCell("K2").numFmt, "d/m/yyyy");
  assert.equal(sheet.getCell("O2").value, "Chinese official");
  assert.equal(sheet.getCell("R2").value, "NO");
});
