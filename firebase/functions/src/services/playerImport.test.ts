import assert from "node:assert/strict";
import test from "node:test";

import JSZip = require("jszip");

import {
  buildPlayerExportRecords,
  cellText,
  declaredUnpackedBytes,
  parseCsv,
  parsePlayerImportFile,
  planPlayerImport,
  writePlayerExportXlsx,
  type ImportRow,
  type ExportSlot,
  type PlanInput,
} from "./playerImport";

function slot(id: number, overrides: Partial<ExportSlot> = {}): ExportSlot {
  return { id, team: Math.ceil(id / 4), emaId: null, email: null, ...overrides };
}

function row(line: number, id: string, emaId: string, extra: Partial<ImportRow> = {}): ImportRow {
  return { row: line, id, emaId, email: "", team: "", ...extra };
}

function plan(rows: ImportRow[], overrides: Partial<PlanInput> = {}) {
  return planPlayerImport({
    rows,
    slots: [slot(1), slot(2), slot(3), slot(4), slot(5)],
    isTeams: false,
    hasTeamColumn: false,
    registeredEmaIds: new Set(["00000001", "00000002", "00000003", "00000004"]),
    emailOwners: new Map(),
    ...overrides,
  });
}

const codes = (result: ReturnType<typeof plan>, index: number) => result.rows[index].issues.map((issue) => issue.code);
const errorCodes = (result: ReturnType<typeof plan>) => result.rows.flatMap((item) => item.issues.map((issue) => issue.code));

test("parseCsv reads semicolons, quotes and a byte order mark", () => {
  assert.deepEqual(parseCsv("﻿id;ema id;email\r\n1;\"1;2\";\"a\"\"b\"\n"), [["id", "ema id", "email"], ["1", "1;2", "a\"b"]]);
});

test("cellText reads links, rich text and formulas", () => {
  assert.equal(cellText({ text: "a@b.es", hyperlink: "mailto:a@b.es" }), "a@b.es");
  assert.equal(cellText({ richText: [{ text: "a@" }, { text: "b.es" }] }), "a@b.es");
  assert.equal(cellText({ formula: "A1", result: 12 }), "12");
  assert.equal(cellText(12), "12");
});

test("parsePlayerImportFile finds columns by name and skips empty lines", async () => {
  const csv = "\n Team ,ID,EMA Number,E-mail\n\nRed,1,123,a@b.es\n";
  const parsed = await parsePlayerImportFile("players.csv", Buffer.from(csv));
  assert.deepEqual(parsed.rows, [{ row: 4, id: "1", emaId: "123", email: "a@b.es", team: "Red" }]);
  assert.equal(parsed.hasEmailColumn, true);
  assert.equal(parsed.hasTeamColumn, true);
});

test("parsePlayerImportFile needs the id and the EMA id columns", async () => {
  await assert.rejects(parsePlayerImportFile("players.csv", Buffer.from("name,email\nA,a@b.es")), /id, ema id/);
});

test("parseCsv keeps new lines inside quotes and reads tab separated text", () => {
  assert.deepEqual(parseCsv("a\tb\n\"x\ny\"\tz"), [["a", "b"], ["x\ny", "z"]]);
});


test("declaredUnpackedBytes adds the sizes of a real xlsx and refuses text", async () => {
  const ExcelJS = await import("exceljs");
  const workbook = new ExcelJS.Workbook();
  workbook.addWorksheet("A").addRow(["id", "ema id"]);
  const buffer = Buffer.from(await workbook.xlsx.writeBuffer());

  const total = declaredUnpackedBytes(buffer);

  assert.ok(total != null && total > 0 && total < 1_000_000);
  assert.equal(declaredUnpackedBytes(Buffer.from("not a zip file at all")), null);
});

test("an Excel file that claims a huge sheet is refused before it is read cell by cell", async () => {
  const ExcelJS = await import("exceljs");
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("A");
  sheet.addRow(["id", "ema id"]);
  sheet.getCell("A6000").value = "1";
  const buffer = Buffer.from(await workbook.xlsx.writeBuffer());

  await assert.rejects(parsePlayerImportFile("big.xlsx", buffer), /too large/);
});

test("an Excel file with too many columns is refused", async () => {
  const ExcelJS = await import("exceljs");
  const workbook = new ExcelJS.Workbook();
  workbook.addWorksheet("A").getCell("AZ1").value = "x";
  const buffer = Buffer.from(await workbook.xlsx.writeBuffer());

  await assert.rejects(parsePlayerImportFile("wide.xlsx", buffer), /too large/);
});

test("the export lists every slot in the format that the import reads", async () => {
  const slots = [
    slot(2, { team: 1 }),
    slot(1, { team: 1, emaId: "00000001", email: "ana@example.com" }),
  ];
  const records = buildPlayerExportRecords(slots, true, new Map([[1, "Dragons"]]));
  assert.deepEqual(records, [
    ["id", "ema id", "email", "team"],
    ["1", "00000001", "ana@example.com", "Dragons"],
    ["2", "", "", "Dragons"],
  ]);

  const file = await writePlayerExportXlsx(records);
  const parsed = await parsePlayerImportFile("players.xlsx", file);
  assert.equal(parsed.hasTeamColumn, true);
  assert.deepEqual(parsed.rows.map((r) => [r.id, r.emaId, r.email, r.team]), [
    ["1", "00000001", "ana@example.com", "Dragons"],
    ["2", "", "", "Dragons"],
  ]);
});

test("an Excel file with prefixed tags and absolute paths is read", async () => {
  // This is how the Open XML SDK writes a file: `<x:row>` tags and `/xl/...` targets in the relations.
  const zip = await JSZip.loadAsync(await writePlayerExportXlsx([["id", "ema id", "email"], ["1", "00000001", "ana@example.com"]]));
  for (const name of Object.keys(zip.files)) {
    if (!/\.(xml|rels)$/.test(name)) continue;
    const text = (await zip.file(name)!.async("string"))
      .replace(/<(\/?)([A-Za-z])/g, (_all, slash, letter) => letter === "?" ? _all : `<${slash}x:${letter}`)
      .replace(/<(\/?)x:(Relationship|Relationships|Types|Default|Override)\b/g, "<$1$2")
      .replace(/Target="(?!\/)/g, 'Target="/xl/');
    zip.file(name, text);
  }
  const parsed = await parsePlayerImportFile("players.xlsx", await zip.generateAsync({ type: "nodebuffer" }));
  assert.deepEqual(parsed.rows.map((r) => [r.id, r.emaId, r.email]), [["1", "00000001", "ana@example.com"]]);
});

test("the export has no team column for a tournament without teams", () => {
  assert.deepEqual(buildPlayerExportRecords([slot(1)], false, new Map()), [["id", "ema id", "email"], ["1", "", ""]]);
});

test("the export of a tournament without slots has only the header", () => {
  assert.deepEqual(buildPlayerExportRecords([], false, new Map()), [["id", "ema id", "email"]]);
  assert.deepEqual(buildPlayerExportRecords([], true, new Map()), [["id", "ema id", "email", "team"]]);
});

test("the export sorts the slots by id", () => {
  const records = buildPlayerExportRecords([slot(10), slot(2), slot(1)], false, new Map());
  assert.deepEqual(records.slice(1).map((record) => record[0]), ["1", "2", "10"]);
});

test("the export leaves the EMA number empty for an empty slot and the team empty when it has no name", () => {
  const records = buildPlayerExportRecords(
    [slot(1, { team: 7 }), slot(2, { team: 1, emaId: "00000002" })],
    true,
    new Map([[1, "Reds"]]),
  );
  assert.deepEqual(records.slice(1), [["1", "", "", ""], ["2", "00000002", "", "Reds"]]);
});

test("the export does not write the team column when the tournament has no teams", () => {
  const records = buildPlayerExportRecords([slot(1, { team: 1 })], false, new Map([[1, "Reds"]]));
  assert.ok(records.every((record) => record.length === 3));
});

test("the exported Excel file keeps the leading zeros of EMA numbers and the order of the columns", async () => {
  const file = await writePlayerExportXlsx(buildPlayerExportRecords(
    [slot(1, { team: 1, emaId: "00000042", email: "z@y.es" })], true, new Map([[1, "Reds"]]),
  ));

  assert.ok(file.length > 0 && file[0] === 0x50 && file[1] === 0x4b);
  const parsed = await parsePlayerImportFile("players.xlsx", file);
  assert.deepEqual(parsed.rows, [{ row: 2, id: "1", emaId: "00000042", email: "z@y.es", team: "Reds" }]);
  assert.equal(parsed.hasEmailColumn, true);
});

test("the export writes the email of the slot and leaves it empty when the slot has none", () => {
  const records = buildPlayerExportRecords(
    [slot(1, { emaId: "00000001", email: "ana@example.com" }), slot(2, { emaId: "00000002" })],
    false,
    new Map(),
  );
  assert.deepEqual(records.slice(1), [["1", "00000001", "ana@example.com"], ["2", "00000002", ""]]);
});


test("a valid file is valid, pads the EMA number and empties the slots that it does not list", () => {
  const result = plan([row(2, "1", "1"), row(3, "2", "2")]);
  assert.equal(result.valid, true);
  assert.equal(result.rows[0].emaId, "00000001");
  assert.deepEqual(result.emptiedSlotIds, [3, 4, 5]);
  assert.deepEqual(result.teamNames, []);
});

test("an empty email is allowed and a valid email is saved in lower case", () => {
  const result = plan([row(2, "1", "1", { email: "Ana@Example.com" }), row(3, "2", "2")]);
  assert.equal(result.valid, true);
  assert.deepEqual(result.rows.map((item) => item.email), ["ana@example.com", null]);
});

test("a row with an id and nothing else is an empty slot, as in the template of the export", () => {
  const result = plan([row(2, "1", "1"), row(3, "2", "")]);
  assert.equal(result.valid, true);
  assert.deepEqual(result.emptiedSlotIds, [2, 3, 4, 5]);
});

test("a row with an email and no EMA number is an error", () => {
  const result = plan([row(2, "1", "", { email: "a@b.es" })]);
  assert.equal(result.valid, false);
  assert.deepEqual(codes(result, 0), ["MISSING_EMA_ID"]);
});

test("rows with a bad id, slot or EMA number are errors and name their line", () => {
  const result = plan([row(2, "x", "1"), row(3, "99", "2"), row(4, "3", "abc"), row(5, "4", "9")]);
  assert.equal(result.valid, false);
  assert.deepEqual([0, 1, 2, 3].map((index) => codes(result, index)), [
    ["INVALID_ID"], ["UNKNOWN_SLOT"], ["INVALID_EMA_ID"], ["UNKNOWN_EMA_ID"],
  ]);
  assert.deepEqual(result.rows.map((item) => item.row), [2, 3, 4, 5]);
});

test("a repeated slot, EMA number or email marks every row that uses it", () => {
  const result = plan([
    row(2, "1", "1", { email: "a@b.es" }), row(3, "1", "2", { email: "A@B.es" }), row(4, "3", "2"),
  ]);
  assert.equal(result.valid, false);
  assert.deepEqual(codes(result, 0).sort(), ["DUPLICATE_EMAIL", "DUPLICATE_SLOT"]);
  assert.deepEqual(codes(result, 1).sort(), ["DUPLICATE_EMAIL", "DUPLICATE_EMA_ID", "DUPLICATE_SLOT"]);
  assert.deepEqual(codes(result, 2), ["DUPLICATE_EMA_ID"]);
});

test("a couple that shares one email is refused, so no account fails later", () => {
  const result = plan([row(2, "1", "1", { email: "pair@b.es" }), row(3, "2", "2", { email: "pair@b.es" })]);
  assert.equal(result.valid, false);
  assert.deepEqual(errorCodes(result), ["DUPLICATE_EMAIL", "DUPLICATE_EMAIL"]);
});

test("an invalid email is an error", () => {
  const result = plan([row(2, "1", "1", { email: "not-an-email" })]);
  assert.deepEqual(codes(result, 0), ["INVALID_EMAIL"]);
});

test("an email that an account of the same EMA number owns is fine", () => {
  const result = plan([row(2, "1", "1", { email: "ana@b.es" })], { emailOwners: new Map([["ana@b.es", "00000001"]]) });
  assert.equal(result.valid, true);
});

test("an email that an account of another EMA number or of no EMA number owns is an error", () => {
  const result = plan(
    [row(2, "1", "1", { email: "bob@b.es" }), row(3, "2", "2", { email: "ed@b.es" })],
    { emailOwners: new Map([["bob@b.es", "00000003"], ["ed@b.es", ""]]) },
  );
  assert.deepEqual(errorCodes(result), ["EMAIL_OWNED_BY_OTHER_ACCOUNT", "EMAIL_OWNED_BY_OTHER_ACCOUNT"]);
});

test("every error of the file is listed, not only the first", () => {
  const result = plan([row(2, "x", "1"), row(3, "2", "abc", { email: "bad" }), row(4, "3", "9")]);
  assert.equal(result.rows.filter((item) => item.issues.length > 0).length, 3);
});

test("a team name over 80 characters is an error", () => {
  const result = plan([row(2, "1", "1", { team: "x".repeat(81) })], { isTeams: true, hasTeamColumn: true });
  assert.deepEqual(codes(result, 0), ["TEAM_NAME_TOO_LONG"]);
});

test("a team column in a tournament without teams is a warning, not an error", () => {
  const result = plan([row(2, "1", "1", { team: "Reds" })]);
  assert.equal(result.valid, true);
  assert.deepEqual(codes(result, 0), ["TEAM_IGNORED"]);
});

test("the slots of one team must agree on its name", () => {
  const result = plan([row(2, "1", "1", { team: "Reds" }), row(3, "2", "2", { team: "Blues" })], { isTeams: true, hasTeamColumn: true });
  assert.deepEqual(errorCodes(result), ["TEAM_NAME_CONFLICT", "TEAM_NAME_CONFLICT"]);
});

test("two teams cannot get the same name, also in another case", () => {
  const result = plan(
    [row(2, "1", "1", { team: "Reds" }), row(3, "5", "2", { team: "reds" })],
    { isTeams: true, hasTeamColumn: true, slots: [slot(1), slot(5)] },
  );
  assert.equal(result.valid, false);
  assert.ok(errorCodes(result).every((code) => code === "TEAM_NAME_IN_USE"));
});

test("with a team column, a team that the file does not name gets the default name", () => {
  const result = plan([row(2, "1", "1", { team: "Reds" })], { isTeams: true, hasTeamColumn: true, slots: [slot(1), slot(5)] });
  assert.equal(result.valid, true);
  assert.deepEqual(result.teamNames, [{ teamId: 1, name: "Reds" }, { teamId: 2, name: "Team 2" }]);
});

test("without a team column, the team names stay as they are", () => {
  const result = plan([row(2, "1", "1")], { isTeams: true, hasTeamColumn: false });
  assert.deepEqual(result.teamNames, []);
});

test("a file exported from the current players is valid when every slot has a player", () => {
  const slots = [slot(1, { team: 1, emaId: "00000001", email: "a@b.es" }), slot(2, { team: 1, emaId: "00000002" })];
  const records = buildPlayerExportRecords(slots, true, new Map([[1, "Reds"]]));
  const rows: ImportRow[] = records.slice(1).map((record, index) => ({
    row: index + 2, id: record[0], emaId: record[1], email: record[2], team: record[3],
  }));
  const result = plan(rows, { slots, isTeams: true, hasTeamColumn: true, emailOwners: new Map([["a@b.es", "00000001"]]) });
  assert.equal(result.valid, true);
  assert.deepEqual(result.emptiedSlotIds, []);
});

test("a file exported with empty slots is valid and keeps them empty", () => {
  const slots = [slot(1, { emaId: "00000001" }), slot(2)];
  const rows: ImportRow[] = buildPlayerExportRecords(slots, false, new Map()).slice(1).map((record, index) => ({
    row: index + 2, id: record[0], emaId: record[1], email: record[2], team: "",
  }));
  const result = plan(rows, { slots });
  assert.equal(result.valid, true);
  assert.deepEqual(result.emptiedSlotIds, [2]);
});
