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

test("the EMA report headers are always in English, whatever the language of the apps", () => {
  assert.deepEqual([...EMA_REPORT_HEADERS], [
    "Tournament name", "Number of participants", "Place", "Player's first name", "Player's last name",
    "EMA number", "Table points", "Score", "EMA member", "Country", "Date", "Countrycourt", "city",
    "shortname", "rules", "period", "NbDays", "Extra",
  ]);
});

test("the EMA report writes the period of any two dates and refuses missing host or names", async () => {
  const base = {
    tournamentName: "Open", participantCount: 4, startDate: "2025-02-01", endDate: "2025-02-01", hostCountry: "ESP", hostCity: "Madrid", shortName: "OPEN",
    rows: [{ place: 1, firstName: "Ana", lastName: "Gil", emaId: null, tablePoints: 4, score: 10, isEmaMember: false, country: "ESP" }],
  };
  const period = async (startDate: string, endDate: string) => {
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await buildEmaReport({ ...base, startDate, endDate }) as unknown as ExcelJS.Buffer);
    const sheet = workbook.getWorksheet("MCR template");
    assert.ok(sheet);
    return sheet.getRow(2).values as ExcelJS.CellValue[];
  };
  const row = await period("2025-02-01", "2025-02-01");
  assert.ok(row.includes("1 Feb. 2025"), JSON.stringify(row));
  assert.ok(row.includes(""), "a player without an EMA id has an empty id");
  assert.ok((await period("2025-02-28", "2025-03-01")).includes("28 Feb.-1 Mar. 2025"));
  assert.ok((await period("2025-12-31", "2026-01-01")).includes("31 Dec. 2025-1 Jan. 2026"));

  await assert.rejects(buildEmaReport({ ...base, hostCity: " " }), /Host country and host city/);
  await assert.rejects(buildEmaReport({ ...base, hostCountry: "Spain" }), /Host country and host city/);
  await assert.rejects(buildEmaReport({ ...base, rows: [{ ...base.rows[0], lastName: " " }] }), /first name and last name/);
});
