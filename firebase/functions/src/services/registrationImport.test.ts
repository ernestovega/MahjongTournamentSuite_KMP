import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { normalizeFormEmaId, parseCsv, parseFormTime, readFormRows } from "./registrationImport";

const FORM = [
  "Marca temporal,Dirección de correo electrónico,First name,Last name,EMA number,Country,Team,Food restrictions",
  "15/09/2026 18:28:56,Ana@Example.com,Ana,García,10990039,Spain,,no",
  "15/09/2026 19:03:05,cris@example.com,Cris,\"Horcajo, Jerez\",10990133.0,Spain,#masti,Seafood and mollusks",
  "26/09/2026 12:37:34,hector@example.com,Héctor,Escaso,4290023,France ,TBD,Ninguna",
  "27/09/2026 10:00:00,not-an-email,Bad,Row,ABC,Italy,?,",
  "28/09/2026 10:00:00,ana@example.com,Ana,Twice,10990039,Spain,,",
].join("\r\n");

describe("import of the old Google Form", () => {
  it("splits CSV with quotes, commas inside quotes and semicolons", () => {
    assert.deepEqual(parseCsv("a,\"b, c\",\"d \"\"e\"\"\"\n1,2,3\n"), [["a", "b, c", "d \"e\""], ["1", "2", "3"]]);
    assert.deepEqual(parseCsv("﻿a;b\r\n1;2"), [["a", "b"], ["1", "2"]]);
    assert.deepEqual(parseCsv("a,b\n\n1,2\n"), [["a", "b"], ["1", "2"]]);
  });

  it("gives EMA numbers 8 digits, also when the sheet made them decimals", () => {
    assert.equal(normalizeFormEmaId("10990039.0"), "10990039");
    assert.equal(normalizeFormEmaId(" 4290023 "), "04290023");
    assert.equal(normalizeFormEmaId("ABC"), "ABC");
  });

  it("reads the times of Google Sheets and ISO times", () => {
    assert.equal(parseFormTime("15/09/2026 18:28:56"), Date.UTC(2026, 8, 15, 18, 28, 56));
    assert.equal(parseFormTime("2026-09-15 18:28:56"), Date.UTC(2026, 8, 15, 18, 28, 56));
    assert.equal(parseFormTime("nonsense"), null);
  });

  it("reads every row, cleans the values and lists the problems", () => {
    const rows = readFormRows(FORM);
    assert.equal(rows.length, 5);
    assert.deepEqual(rows.map((row) => row.emaId), ["10990039", "10990133", "04290023", "ABC", "10990039"]);
    assert.equal(rows[0].email, "ana@example.com");
    assert.equal(rows[0].foodRestrictions, "");
    assert.equal(rows[1].lastName, "Horcajo, Jerez");
    assert.equal(rows[1].team, "#masti");
    assert.equal(rows[1].foodRestrictions, "Seafood and mollusks");
    assert.equal(rows[2].team, null);
    assert.equal(rows[2].foodRestrictions, "");
    assert.deepEqual(rows[3].problems, ["INVALID_EMA", "INVALID_EMAIL"]);
    assert.deepEqual(rows[4].problems, ["DUPLICATE_EMA"]);
    assert.equal(rows[0].line, 2);
    assert.equal(rows[0].registeredAt, Date.UTC(2026, 8, 15, 18, 28, 56));
  });

  it("needs the EMA column and at least one answer", () => {
    assert.throws(() => readFormRows("Name,Email\nA,a@b.c"), /EMA/);
    assert.throws(() => readFormRows("EMA number"), /no answers/);
  });
});
