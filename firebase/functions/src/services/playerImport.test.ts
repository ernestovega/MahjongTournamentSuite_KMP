import assert from "node:assert/strict";
import test from "node:test";

import {
  buildPlayerExportRecords,
  cellText,
  declaredUnpackedBytes,
  parseCsv,
  parsePlayerImportFile,
  planPlayerImport,
  writePlayerExportXlsx,
  type ImportRow,
  type PlanInput,
  type SlotInfo,
} from "./playerImport";

function slot(id: number, overrides: Partial<SlotInfo> = {}): SlotInfo {
  return { id, team: Math.ceil(id / 4), emaId: null, nonMemberName: null, accountUid: null, invitedEmail: null, ...overrides };
}

function row(line: number, id: string, emaId: string, extra: Partial<ImportRow> = {}): ImportRow {
  return { row: line, id, emaId, email: "", team: "", ...extra };
}

function plan(rows: ImportRow[], overrides: Partial<PlanInput> = {}) {
  return planPlayerImport({
    rows,
    slots: [slot(1), slot(2), slot(3), slot(4), slot(5)],
    isTeams: false,
    teamNames: new Map(),
    registeredEmaIds: new Set(["00000001", "00000002", "00000003", "00000004"]),
    replaceExisting: true,
    ...overrides,
  });
}

const codes = (result: ReturnType<typeof plan>, index: number) => result.rows[index].issues.map((issue) => issue.code);

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

test("a new player in an empty slot is NEW and pads the EMA number", () => {
  const result = plan([row(2, "1", "1")]);
  assert.equal(result.rows[0].status, "NEW");
  assert.equal(result.rows[0].emaId, "00000001");
  assert.deepEqual(result.untouchedSlotIds, [2, 3, 4, 5]);
});

test("the same player in the same slot is UNCHANGED", () => {
  const result = plan([row(2, "1", "1")], { slots: [slot(1, { emaId: "00000001" })] });
  assert.equal(result.rows[0].status, "UNCHANGED");
});

test("a different player in an assigned slot is REPLACE with a warning", () => {
  const result = plan([row(2, "1", "2")], { slots: [slot(1, { emaId: "00000001" })] });
  assert.equal(result.rows[0].status, "REPLACE");
  assert.deepEqual(codes(result, 0), ["REPLACES_PLAYER"]);
});

test("rows with a bad id, slot or EMA number are errors", () => {
  const result = plan([row(2, "x", "1"), row(3, "9", "1"), row(4, "1", ""), row(5, "2", "abc"), row(6, "3", "99")]);
  assert.deepEqual(result.rows.map((item) => item.issues[0].code), [
    "INVALID_ID", "UNKNOWN_SLOT", "MISSING_EMA_ID", "INVALID_EMA_ID", "UNKNOWN_EMA_ID",
  ]);
  assert.ok(result.rows.every((item) => item.status === "ERROR"));
});

test("a repeated slot or EMA number marks every row that uses it", () => {
  const result = plan([row(2, "1", "1"), row(3, "1", "2"), row(4, "2", "3"), row(5, "3", "3")]);
  assert.deepEqual(codes(result, 0), ["DUPLICATE_SLOT"]);
  assert.deepEqual(codes(result, 1), ["DUPLICATE_SLOT"]);
  assert.deepEqual(codes(result, 2), ["DUPLICATE_EMA_ID"]);
  assert.deepEqual(codes(result, 3), ["DUPLICATE_EMA_ID"]);
});

test("a player cannot go to a slot while another slot that stays keeps the player", () => {
  const result = plan([row(2, "2", "1")], { slots: [slot(1, { emaId: "00000001" }), slot(2)] });
  assert.deepEqual(codes(result, 0), ["EMA_ASSIGNED_ELSEWHERE"]);
});

test("players can swap slots when the file names both slots", () => {
  const result = plan([row(2, "1", "2"), row(3, "2", "1")], {
    slots: [slot(1, { emaId: "00000001" }), slot(2, { emaId: "00000002" })],
  });
  assert.deepEqual(result.rows.map((item) => item.status), ["REPLACE", "REPLACE"]);
  assert.deepEqual(result.rows.map((item) => item.issues.some((issue) => issue.severity === "ERROR")), [false, false]);
});

test("without the replace choice, a swap is blocked and nothing moves", () => {
  const result = plan([row(2, "2", "1"), row(3, "3", "2")], {
    slots: [slot(1, { emaId: "00000001" }), slot(2, { emaId: "00000003" }), slot(3)],
    replaceExisting: false,
  });
  // Slot 2 is not replaced, so player 1 stays in slot 1 and cannot go to slot 2.
  assert.equal(result.rows[1].status, "NEW");
  assert.equal(result.rows[0].status, "REPLACE");
});

test("an email that differs from the saved email gives a warning", () => {
  const result = plan([row(2, "1", "1", { email: "New@B.es" })], {
    slots: [slot(1, { emaId: "00000001", invitedEmail: "old@b.es" })],
  });
  assert.equal(result.rows[0].email, "new@b.es");
  assert.equal(result.rows[0].status, "NEW");
  assert.deepEqual(result.rows[0].issues, [{ code: "EMAIL_CHANGES", severity: "WARNING", args: ["old@b.es", "new@b.es"] }]);
});

test("an email for a slot with a linked account is ignored with a warning", () => {
  const result = plan([row(2, "1", "1", { email: "a@b.es" })], {
    slots: [slot(1, { emaId: "00000001", accountUid: "uid" })],
  });
  assert.deepEqual(codes(result, 0), ["EMAIL_IGNORED_LINKED"]);
  assert.equal(result.rows[0].status, "UNCHANGED");
});

test("an invalid email is an error", () => {
  assert.deepEqual(codes(plan([row(2, "1", "1", { email: "nope" })]), 0), ["INVALID_EMAIL"]);
});

test("team names rename the team of the slot", () => {
  const result = plan([row(2, "1", "1", { team: "Red" }), row(3, "2", "2", { team: "red" })], {
    isTeams: true,
    teamNames: new Map([[1, "Team 1"], [2, "Team 2"]]),
  });
  assert.deepEqual(result.teamRenames, [{ teamId: 1, name: "Red" }]);
  assert.deepEqual(result.rows.map((item) => item.status), ["NEW", "NEW"]);
});

test("rows of one team with different names are errors", () => {
  const result = plan([row(2, "1", "1", { team: "Red" }), row(3, "2", "2", { team: "Blue" })], {
    isTeams: true,
    teamNames: new Map([[1, "Team 1"]]),
  });
  assert.deepEqual(codes(result, 0), ["TEAM_NAME_CONFLICT"]);
  assert.deepEqual(result.teamRenames, []);
});

test("two teams cannot get the same name", () => {
  const result = plan([row(2, "1", "1", { team: "Red" }), row(3, "5", "2", { team: "Red" })], {
    isTeams: true,
    teamNames: new Map([[1, "Team 1"], [2, "Team 2"]]),
  });
  assert.deepEqual(codes(result, 0), ["TEAM_NAME_IN_USE"]);
  assert.deepEqual(codes(result, 1), ["TEAM_NAME_IN_USE"]);
});

test("a team name in an individual tournament is ignored with a warning", () => {
  const result = plan([row(2, "1", "1", { team: "Red" })]);
  assert.deepEqual(codes(result, 0), ["TEAM_IGNORED"]);
  assert.equal(result.rows[0].status, "NEW");
});

test("parsePlayerImportFile reads a real Excel file with numbers, links and an empty line", async () => {
  const ExcelJS = await import("exceljs");
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("Players");
  sheet.addRow(["ID", "EMA", "Email", "Team"]);
  sheet.addRow([1, 12345, { text: "a@b.es", hyperlink: "mailto:a@b.es" }, "Reds"]);
  sheet.addRow([]);
  sheet.addRow([2, "00000002", "", ""]);
  const buffer = Buffer.from(await workbook.xlsx.writeBuffer());

  const parsed = await parsePlayerImportFile("players.xlsx", buffer);

  assert.deepEqual(parsed.rows, [
    { row: 2, id: "1", emaId: "12345", email: "a@b.es", team: "Reds" },
    { row: 4, id: "2", emaId: "00000002", email: "", team: "" },
  ]);
});

test("parsePlayerImportFile detects Excel content by its content, not only by the name", async () => {
  await assert.rejects(parsePlayerImportFile("players.xlsx", Buffer.from("not a zip")), /not a valid Excel file/);
});

test("parsePlayerImportFile refuses empty files and files without rows", async () => {
  await assert.rejects(parsePlayerImportFile("a.csv", Buffer.from("\n\n")), /empty/);
  await assert.rejects(parsePlayerImportFile("a.csv", Buffer.from("id,ema id\n")), /no player rows/);
});

test("parsePlayerImportFile refuses a file with too many rows", async () => {
  const lines = Array.from({ length: 1001 }, (_, index) => `${index + 1},1`).join("\n");
  await assert.rejects(parsePlayerImportFile("a.csv", Buffer.from(`id,ema id\n${lines}`)), /more than 1000/);
});

test("parseCsv keeps new lines inside quotes and reads tab separated text", () => {
  assert.deepEqual(parseCsv("a\tb\n\"x\ny\"\tz"), [["a", "b"], ["x\ny", "z"]]);
});

test("a team name over 80 characters is an error", () => {
  const result = plan([row(2, "1", "1", { team: "x".repeat(81) })], { isTeams: true, teamNames: new Map([[1, "Team 1"]]) });
  assert.deepEqual(codes(result, 0), ["TEAM_NAME_TOO_LONG"]);
});

test("a non-member in the slot makes the row a replacement", () => {
  const result = plan([row(2, "1", "1")], { slots: [slot(1, { nonMemberName: "Guest Player" })] });
  assert.equal(result.rows[0].status, "REPLACE");
});

test("an EMA number held by a slot outside the file blocks the row", () => {
  const result = plan([row(2, "2", "1")], {
    slots: [slot(1, { emaId: "00000001" }), slot(2), slot(3)],
  });
  assert.deepEqual(codes(result, 0), ["EMA_ASSIGNED_ELSEWHERE"]);
  assert.deepEqual(result.rows[0].issues[0].args, ["1"]);
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
    slot(1, { team: 1, emaId: "00000001", invitedEmail: "ana@example.com" }),
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

test("the export leaves the EMA number empty for a non-member and the team empty when it has no name", () => {
  const records = buildPlayerExportRecords(
    [slot(1, { team: 7, nonMemberName: "Guest One" }), slot(2, { team: 1, emaId: "00000002", accountUid: "uid" })],
    true,
    new Map([[1, "Reds"]]),
  );
  assert.deepEqual(records.slice(1), [["1", "", "", ""], ["2", "00000002", "", "Reds"]]);
});

test("the export does not write the team column when the tournament has no teams", () => {
  const records = buildPlayerExportRecords([slot(1, { team: 1 })], false, new Map([[1, "Reds"]]));
  assert.ok(records.every((record) => record.length === 3));
});

test("a file exported from the current players plans every assigned slot as UNCHANGED", () => {
  const slots = [
    slot(1, { team: 1, emaId: "00000001", invitedEmail: "a@b.es" }),
    slot(2, { team: 1, emaId: "00000002" }),
    slot(3, { team: 2 }),
  ];
  const names = new Map([[1, "Reds"], [2, "Blues"]]);
  const records = buildPlayerExportRecords(slots, true, names);
  const rows: ImportRow[] = records.slice(1).map((record, index) => ({
    row: index + 2, id: record[0], emaId: record[1], email: record[2], team: record[3],
  }));

  const planned = planPlayerImport({
    rows, slots, isTeams: true, teamNames: names,
    registeredEmaIds: new Set(["00000001", "00000002"]), replaceExisting: false,
  });

  assert.deepEqual(planned.rows.map((planRow) => planRow.status), ["UNCHANGED", "UNCHANGED", "ERROR"]);
  // The empty slot is the only problem. The editor fills its EMA number in the template.
  assert.deepEqual(planned.rows[2].issues.map((issue) => issue.code), ["MISSING_EMA_ID"]);
  assert.deepEqual(planned.teamRenames, []);
});

test("the exported Excel file keeps the leading zeros of EMA numbers and the order of the columns", async () => {
  const file = await writePlayerExportXlsx(buildPlayerExportRecords(
    [slot(1, { team: 1, emaId: "00000042", invitedEmail: "z@y.es" })], true, new Map([[1, "Reds"]]),
  ));

  assert.ok(file.length > 0 && file[0] === 0x50 && file[1] === 0x4b);
  const parsed = await parsePlayerImportFile("players.xlsx", file);
  assert.deepEqual(parsed.rows, [{ row: 2, id: "1", emaId: "00000042", email: "z@y.es", team: "Reds" }]);
  assert.equal(parsed.hasEmailColumn, true);
});
