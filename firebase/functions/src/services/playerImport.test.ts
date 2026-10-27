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
  type PlanInput,
  type SlotInfo,
} from "./playerImport";

function slot(id: number, overrides: Partial<SlotInfo> = {}): SlotInfo {
  return { id, team: Math.ceil(id / 4), emaId: null, accountUid: null, email: null, ...overrides };
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
    accountEmailByEma: new Map(),
    emailOwners: new Map(),
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

test("a new email for a player with no account creates the account", () => {
  const result = plan([row(2, "1", "1", { email: "New@B.es" })], { slots: [slot(1, { emaId: "00000001" })] });
  assert.equal(result.rows[0].email, "new@b.es");
  assert.equal(result.rows[0].createsAccount, true);
  assert.equal(result.rows[0].status, "NEW");
  assert.deepEqual(result.rows[0].issues, []);
});

test("a row without an email creates no account", () => {
  const result = plan([row(2, "1", "1")], { slots: [slot(1, { emaId: "00000001" })] });
  assert.equal(result.rows[0].createsAccount, false);
  assert.equal(result.rows[0].status, "UNCHANGED");
});

test("an email that differs from the email of the account is refused with a warning and the account email stays", () => {
  const result = plan([row(2, "1", "1", { email: "New@B.es" })], {
    slots: [slot(1, { emaId: "00000001", accountUid: "uid", email: "old@b.es" })],
    accountEmailByEma: new Map([["00000001", "old@b.es"]]),
  });
  assert.deepEqual(result.rows[0].issues, [{ code: "EMAIL_KEPT_ACCOUNT", severity: "WARNING", args: ["old@b.es", "new@b.es"] }]);
  assert.equal(result.rows[0].createsAccount, false);
  // The rest of the row still applies. Here nothing else changes.
  assert.equal(result.rows[0].status, "UNCHANGED");
});

test("an email that matches the email of the account gives no warning and changes nothing", () => {
  const result = plan([row(2, "1", "1", { email: "OLD@b.es" })], {
    slots: [slot(1, { emaId: "00000001", accountUid: "uid", email: "old@b.es" })],
    accountEmailByEma: new Map([["00000001", "old@b.es"]]),
  });
  assert.deepEqual(result.rows[0].issues, []);
  assert.equal(result.rows[0].createsAccount, false);
  assert.equal(result.rows[0].status, "UNCHANGED");
});

test("a refused email does not block the other parts of the row", () => {
  const result = plan([row(2, "1", "1", { email: "other@b.es", team: "Blues" })], {
    isTeams: true,
    teamNames: new Map([[1, "Reds"]]),
    slots: [slot(1, { emaId: "00000001", accountUid: "uid", email: "old@b.es" })],
    accountEmailByEma: new Map([["00000001", "old@b.es"]]),
  });
  assert.deepEqual(codes(result, 0), ["EMAIL_KEPT_ACCOUNT"]);
  assert.equal(result.rows[0].status, "NEW");
  assert.deepEqual(result.teamRenames, [{ teamId: 1, name: "Blues" }]);
});

test("an account that exists for the EMA number decides, also when the player moves to an empty slot", () => {
  const result = plan([row(2, "2", "1", { email: "file@b.es" }), row(3, "1", "2")], {
    slots: [slot(1, { emaId: "00000001", accountUid: "uid", email: "old@b.es" }), slot(2, { emaId: "00000002" })],
    accountEmailByEma: new Map([["00000001", "old@b.es"]]),
  });
  assert.deepEqual(codes(result, 0), ["REPLACES_PLAYER", "EMAIL_KEPT_ACCOUNT"]);
  assert.equal(result.rows[0].createsAccount, false);
});

test("an email that belongs to an account of another player is refused and never links the account", () => {
  const result = plan([row(2, "1", "1", { email: "shared@b.es" })], {
    slots: [slot(1, { emaId: "00000001" })],
    emailOwners: new Map([["shared@b.es", "00000009"]]),
  });
  assert.deepEqual(result.rows[0].issues, [{ code: "EMAIL_IN_USE", severity: "WARNING", args: ["00000009"] }]);
  assert.equal(result.rows[0].createsAccount, false);
  assert.equal(result.rows[0].status, "UNCHANGED");
});

test("an email that belongs to an account with no EMA number is refused too", () => {
  const result = plan([row(2, "1", "1", { email: "boss@b.es" })], {
    slots: [slot(1, { emaId: "00000001" })],
    emailOwners: new Map([["boss@b.es", ""]]),
  });
  assert.deepEqual(result.rows[0].issues, [{ code: "EMAIL_IN_USE", severity: "WARNING", args: [""] }]);
  assert.equal(result.rows[0].createsAccount, false);
});

test("two players of the file with the same email: the first row gets the account, the second is refused", () => {
  const result = plan([row(2, "1", "1", { email: "couple@b.es" }), row(3, "2", "2", { email: "COUPLE@b.es" })], {
    slots: [slot(1), slot(2)],
  });
  assert.equal(result.rows[0].createsAccount, true);
  assert.equal(result.rows[1].createsAccount, false);
  assert.deepEqual(result.rows[1].issues, [{ code: "EMAIL_IN_USE", severity: "WARNING", args: ["00000001"] }]);
  assert.equal(result.rows[1].status, "NEW");
});

test("a row with an error claims no email", () => {
  const result = plan([row(2, "1", "99", { email: "a@b.es" }), row(3, "2", "2", { email: "a@b.es" })], {
    slots: [slot(1), slot(2)],
  });
  assert.deepEqual(codes(result, 0), ["UNKNOWN_EMA_ID"]);
  assert.equal(result.rows[0].createsAccount, false);
  assert.equal(result.rows[1].createsAccount, true);
});

test("a replacement the editor did not accept claims no email", () => {
  const result = plan([row(2, "1", "2", { email: "a@b.es" })], {
    slots: [slot(1, { emaId: "00000001" })],
    replaceExisting: false,
  });
  assert.equal(result.rows[0].status, "REPLACE");
  assert.equal(result.rows[0].createsAccount, false);
});

test("an invalid email is an error and no account is planned", () => {
  const result = plan([row(2, "1", "1", { email: "not-an-email" })], { slots: [slot(1)] });
  assert.deepEqual(codes(result, 0), ["INVALID_EMAIL"]);
  assert.equal(result.rows[0].createsAccount, false);
});

test("parseCsv keeps new lines inside quotes and reads tab separated text", () => {
  assert.deepEqual(parseCsv("a\tb\n\"x\ny\"\tz"), [["a", "b"], ["x\ny", "z"]]);
});

test("a team name over 80 characters is an error", () => {
  const result = plan([row(2, "1", "1", { team: "x".repeat(81) })], { isTeams: true, teamNames: new Map([[1, "Team 1"]]) });
  assert.deepEqual(codes(result, 0), ["TEAM_NAME_TOO_LONG"]);
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
    [slot(1, { team: 7 }), slot(2, { team: 1, emaId: "00000002", accountUid: "uid" })],
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
    slot(1, { team: 1, emaId: "00000001", accountUid: "uid", email: "a@b.es" }),
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
    registeredEmaIds: new Set(["00000001", "00000002"]),
    accountEmailByEma: new Map([["00000001", "a@b.es"]]), emailOwners: new Map([["a@b.es", "00000001"]]),
    replaceExisting: false,
  });

  assert.deepEqual(planned.rows.map((planRow) => planRow.status), ["UNCHANGED", "UNCHANGED", "ERROR"]);
  // The empty slot is the only problem. The editor fills its EMA number in the template.
  assert.deepEqual(planned.rows[2].issues.map((issue) => issue.code), ["MISSING_EMA_ID"]);
  assert.deepEqual(planned.teamRenames, []);
});

test("the exported Excel file keeps the leading zeros of EMA numbers and the order of the columns", async () => {
  const file = await writePlayerExportXlsx(buildPlayerExportRecords(
    [slot(1, { team: 1, emaId: "00000042", accountUid: "uid", email: "z@y.es" })], true, new Map([[1, "Reds"]]),
  ));

  assert.ok(file.length > 0 && file[0] === 0x50 && file[1] === 0x4b);
  const parsed = await parsePlayerImportFile("players.xlsx", file);
  assert.deepEqual(parsed.rows, [{ row: 2, id: "1", emaId: "00000042", email: "z@y.es", team: "Reds" }]);
  assert.equal(parsed.hasEmailColumn, true);
});

test("the export writes the email of the linked account and leaves it empty for a slot with no account", () => {
  const records = buildPlayerExportRecords(
    [slot(1, { emaId: "00000001", accountUid: "uid", email: "ana@example.com" }), slot(2, { emaId: "00000002" })],
    false,
    new Map(),
  );
  assert.deepEqual(records.slice(1), [["1", "00000001", "ana@example.com"], ["2", "00000002", ""]]);
});

test("a file exported with the emails of the accounts plans a second import as UNCHANGED", () => {
  const slots = [
    slot(1, { emaId: "00000001", accountUid: "uid1", email: "ana@example.com" }),
    slot(2, { emaId: "00000002", accountUid: "uid2", email: "bob@example.com" }),
  ];
  const rows: ImportRow[] = buildPlayerExportRecords(slots, false, new Map()).slice(1).map((record, index) => ({
    row: index + 2, id: record[0], emaId: record[1], email: record[2], team: "",
  }));
  const planned = plan(rows, {
    slots,
    accountEmailByEma: new Map([["00000001", "ana@example.com"], ["00000002", "bob@example.com"]]),
    emailOwners: new Map([["ana@example.com", "00000001"], ["bob@example.com", "00000002"]]),
    replaceExisting: false,
  });
  assert.deepEqual(planned.rows.map((item) => item.status), ["UNCHANGED", "UNCHANGED"]);
  assert.deepEqual(planned.rows.flatMap((item) => item.issues), []);
});

test("players with an account keep the email of the account when the file has other emails (case of 2026-10-26)", () => {
  const slots = [1, 2, 5, 9].map((id) => slot(id, { emaId: `0000000${id}`, accountUid: `uid${id}`, email: `account${id}@b.es` }));
  const result = plan(slots.map((item, index) => row(index + 2, String(item.id), item.emaId!, { email: `file${item.id}@b.es` })), {
    slots: [...slots, slot(3), slot(4), slot(6), slot(7), slot(8)],
    registeredEmaIds: new Set(slots.map((item) => item.emaId!)),
    accountEmailByEma: new Map(slots.map((item) => [item.emaId!, item.email] as const)),
  });
  assert.deepEqual(result.rows.map((item) => item.issues.map((issue) => issue.code)), Array(4).fill(["EMAIL_KEPT_ACCOUNT"]));
  assert.ok(result.rows.every((item) => !item.createsAccount));
});
