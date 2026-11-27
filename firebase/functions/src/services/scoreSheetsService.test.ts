import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

import {
  buildScoreSheetsPdf,
  readScoreSheetsOptions,
  scoreSheetColumns,
  scoreSheetList,
  titleLines,
  type ScoreSheetsDocument,
} from "./scoreSheetsService";

const pageCount = (pdf: Buffer) => (pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? []).length;

const model = (changes: Partial<ScoreSheetsDocument> = {}): ScoreSheetsDocument => ({
  tournamentName: "6th MMC",
  logo: null,
  organizerName: "Mahjong Madrid",
  organizerColor: "#02B16B",
  chickenHands: true,
  emaLogo: true,
  sheets: [],
  ...changes,
});

test("the options are a blank sheet with the logo of the EMA by default", () => {
  assert.deepEqual(readScoreSheetsOptions({}), { mode: "blank", emaLogo: true });
  assert.deepEqual(readScoreSheetsOptions({ mode: "tables", ema: "false" }), { mode: "tables", emaLogo: false });
  assert.deepEqual(readScoreSheetsOptions({ mode: "other", ema: "0" }), { mode: "blank", emaLogo: false });
  assert.deepEqual(readScoreSheetsOptions({ ema: "no" }), { mode: "blank", emaLogo: false });
  assert.deepEqual(readScoreSheetsOptions({ ema: "TRUE" }), { mode: "blank", emaLogo: true });
});

test("the sheets go by round: every table of round 1, then every table of round 2", () => {
  assert.deepEqual(scoreSheetList(2, 2), [{ round: 1, table: 1 }, { round: 1, table: 2 }, { round: 2, table: 1 }, { round: 2, table: 2 }]);
  assert.deepEqual(scoreSheetList(0, 5), []);
});

test("the columns fill the width of the sheet, and without chicken hands the seats stay in place", () => {
  const sum = (c: ReturnType<typeof scoreSheetColumns>) => c.number + c.winner + c.loser + c.points + c.chicken + (c.score + c.penalty) * 4 + c.check;
  const withChicken = scoreSheetColumns(true, 500);
  const without = scoreSheetColumns(false, 500);
  assert.ok(Math.abs(sum(withChicken) - 500) < 1e-6);
  assert.ok(Math.abs(sum(without) - 500) < 1e-6);
  assert.equal(without.chicken, 0);
  assert.ok(without.winner > withChicken.winner);
  assert.ok(Math.abs(without.score - withChicken.score) < 1e-6);
});

test("a long short name goes in two balanced lines", () => {
  assert.deepEqual(titleLines("6th MMC"), ["6th", "MMC"]);
  assert.deepEqual(titleLines("OPEN"), ["OPEN"]);
  assert.deepEqual(titleLines("Madrid Riichi Open 2027"), ["Madrid Riichi", "Open 2027"]);
});

test("a blank PDF has one A4 page, and a PDF of the tables one page for each table of each round", async () => {
  const blank = await buildScoreSheetsPdf(model());
  assert.equal(blank.subarray(0, 5).toString(), "%PDF-");
  assert.equal(pageCount(blank), 1);
  assert.match(blank.toString("latin1"), /\/MediaBox \[0 0 595\.28 841\.89\]/);
  const tables = await buildScoreSheetsPdf(model({ sheets: scoreSheetList(3, 4) }));
  assert.equal(pageCount(tables), 12);
});

test("the sheet is made without chicken hands, without the EMA, with a bad logo or a name in Cyrillic", async () => {
  const cases: Partial<ScoreSheetsDocument>[] = [
    { chickenHands: false, emaLogo: false },
    { logo: Buffer.from("not an image") },
    { organizerName: "" },
    { tournamentName: "Открытый чемпионат", organizerName: "Клуб" },
  ];
  for (const changes of cases) {
    const pdf = await buildScoreSheetsPdf(model({ ...changes, sheets: [{ round: 1, table: 1 }] }));
    assert.equal(pageCount(pdf), 1, JSON.stringify(changes));
  }
});

test("the sheet draws a logo that pdfkit reads, and the smallest title size for a name that never fits", async () => {
  const logo = readFileSync(join(__dirname, "../assets/logo_ema.png"));
  const pdf = await buildScoreSheetsPdf(model({ logo, tournamentName: "X".repeat(300) }));
  assert.equal(pageCount(pdf), 1);
});
