import assert from "node:assert/strict";
import test from "node:test";
import ExcelJS = require("exceljs");

import { buildIdList, ID_LIST_BASE_HEADERS } from "./idListService";

test("ID list contains every value used by the ID cards", async () => {
  const bytes = await buildIdList({
    tournamentShortName: "MMC2026",
    year: "2026",
    primaryColor: "#02B16B",
    numberOfRounds: 2,
    players: [
      {
        playerId: 7,
        name: "María de la Cruz",
        country: "Spain",
        countryCode: "ESP",
        teamName: "Dragons",
        tableNumbers: [3, 8],
      },
      {
        playerId: 8,
        name: "Guest Player",
        country: "",
        countryCode: "EU",
        teamName: null,
        tableNumbers: [4, 1],
      },
    ],
  });

  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(bytes as unknown as ExcelJS.Buffer);
  const sheet = workbook.getWorksheet("ID list");
  assert.ok(sheet);
  assert.deepEqual(
    (sheet.getRow(1).values as ExcelJS.CellValue[]).slice(1),
    [...ID_LIST_BASE_HEADERS, "Round 1 table", "Round 2 table"],
  );
  assert.deepEqual(
    (sheet.getRow(2).values as ExcelJS.CellValue[]).slice(1),
    [
      "MMC2026", "2026", 7, "María de la Cruz", "MARÍA", "DE LA CRUZ", "Spain", "", "Dragons", 3, 8,
    ],
  );
  assert.deepEqual(
    (sheet.getRow(3).values as ExcelJS.CellValue[]).slice(1),
    ["MMC2026", "2026", 8, "Guest Player", "GUEST", "PLAYER", "", "", "", 4, 1],
  );
  assert.equal(sheet.getImages().length, 2);
  assert.equal(sheet.views[0].state, "frozen");
  assert.equal(sheet.autoFilter?.toString(), "A1:K1");
});

test("lists players without a number of rounds, with missing and empty tables", async () => {
  const xlsx = await buildIdList({
    tournamentShortName: "MMC", year: "2026", primaryColor: "#02B16B",
    players: [
      { playerId: 1, name: "ANA RUIZ", country: "Spain", countryCode: "ESP", teamName: null, tableNumbers: [3, 0] },
      { playerId: 2, name: "BEA GIL", country: "", countryCode: "", teamName: null, tableNumbers: [] },
    ],
  });
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(xlsx as unknown as ExcelJS.Buffer);
  const sheet = workbook.getWorksheet("ID list")!;
  const header = (sheet.getRow(1).values as unknown[]).filter((value) => typeof value === "string" && value.startsWith("Round"));
  assert.equal(header.length, 2);
});
