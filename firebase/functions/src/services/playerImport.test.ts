import assert from "node:assert/strict";
import test from "node:test";

import {
  cellText,
  parseCsv,
  parsePlayerImportFile,
  planPlayerImport,
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
