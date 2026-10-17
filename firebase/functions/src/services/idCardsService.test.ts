import assert from "node:assert/strict";
import test from "node:test";

import {
  buildIdCardBackRows,
  buildIdCardProofPdf,
  buildIdCardsPdf,
  CARD_HEIGHT,
  CARD_WIDTH,
  idCardSheetCardOrigin,
  idCardSheetGrid,
  type IdCardSheetOptions,
  iso2CountryCode,
  SHEET_MARGIN,
  sheetPageSize,
} from "./idCardsService";

test("supports the European flag proof code without changing the EU guest code", () => {
  assert.equal(iso2CountryCode("EUR"), "EU");
  assert.equal(iso2CountryCode("EU"), null);
});

test("creates one front and one back page for each player", async () => {
  const pdf = await buildIdCardsPdf({
    tournamentShortName: "6th MMC",
    year: "2026",
    primaryColor: "#7C3AED",
    numberOfRounds: 7,
    roundSchedules: [
      { roundId: 1, date: "2026-04-17", startTime: "09:30" },
      { roundId: 2, date: "2026-04-17", startTime: "13:30" },
      { roundId: 3, date: "2026-04-18", startTime: null },
    ],
    players: [
      {
        playerId: 21,
        name: "ÁLVARO DE LA TORRE",
        country: "Spain",
        countryCode: "ESP",
        teamName: "Dragons",
        tableNumbers: [11, 4, 21, 19, 7, 12, 3],
      },
    ],
  });

  assert.equal(pdf.subarray(0, 5).toString("ascii"), "%PDF-");
  const pageObjects = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
  assert.equal(pageObjects.length, 2);
});

test("keeps one row for each round and marks the first round of each new day", () => {
  assert.deepEqual(
    buildIdCardBackRows(
      4,
      [
        { roundId: 1, date: "2026-04-17", startTime: "09:30" },
        { roundId: 2, date: "2026-04-17", startTime: "13:30" },
        { roundId: 3, date: "2026-04-18", startTime: "09:00" },
        { roundId: 4, date: null, startTime: null },
      ],
      [11, 4, 21, 19],
    ),
    [
      { roundId: 1, date: "2026-04-17", startTime: "09:30", tableNumber: 11, startsNewDay: false },
      { roundId: 2, date: "2026-04-17", startTime: "13:30", tableNumber: 4, startsNewDay: false },
      { roundId: 3, date: "2026-04-18", startTime: "09:00", tableNumber: 21, startsNewDay: true },
      { roundId: 4, date: null, startTime: null, tableNumber: 19, startsNewDay: false },
    ],
  );
});

test("creates a proof PDF with one front and one back page", async () => {
  const pdf = await buildIdCardProofPdf({
    shortName: "6th MMC",
    year: "2026",
    primaryColor: "#02B16B",
  });

  assert.equal(pdf.subarray(0, 5).toString("ascii"), "%PDF-");
  const pageObjects = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
  assert.equal(pageObjects.length, 2);
});

test("fits 10 real size cards on an A4 sheet and fewer when the cards are larger", () => {
  assert.deepEqual(idCardSheetGrid({ cardsPerSheet: 10, scalePercent: 100, cutMarks: true, landscape: false, doubleSided: true }), {
    columns: 2,
    rows: 5,
    scale: 1,
  });
  assert.deepEqual(idCardSheetGrid({ cardsPerSheet: 4, scalePercent: 100, cutMarks: true, landscape: false, doubleSided: true }), {
    columns: 2,
    rows: 2,
    scale: 1,
  });
  const large = idCardSheetGrid({ cardsPerSheet: 10, scalePercent: 150, cutMarks: true, landscape: false, doubleSided: true });
  assert.deepEqual(large, { columns: 1, rows: 3, scale: 1.5 });
  assert.equal(idCardSheetGrid({ cardsPerSheet: 1, scalePercent: 400, cutMarks: true, landscape: false, doubleSided: true }), null);
  assert.deepEqual(idCardSheetGrid({ cardsPerSheet: 20, scalePercent: 100, cutMarks: true, landscape: true, doubleSided: true }), {
    columns: 3,
    rows: 3,
    scale: 1,
  });
  assert.deepEqual(idCardSheetGrid({ cardsPerSheet: 1, scalePercent: 330, cutMarks: true, landscape: true, doubleSided: true }), {
    columns: 1,
    rows: 1,
    scale: 3.3,
  });
});

test("builds A4 sheets with one front page and one back page for each group of cards", async () => {
  const player = (playerId: number) => ({
    playerId,
    name: "Ana Pérez",
    country: "Spain",
    countryCode: "ESP",
    teamName: null,
    tableNumbers: [1, 2],
  });
  const pdf = await buildIdCardsPdf(
    {
      tournamentShortName: "6th MMC",
      year: "2026",
      primaryColor: "#7C3AED",
      numberOfRounds: 2,
      players: [1, 2, 3, 4, 5].map(player),
    },
    { cardsPerSheet: 4, scalePercent: 100, cutMarks: true, landscape: false, doubleSided: true },
  );
  const pages = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
  assert.equal(pages.length, 4);
});

const EPSILON = 1e-6;

/** Every orientation, double-sided choice, card size and number of cards that a user can choose. */
function* sheetCombinations(): Generator<IdCardSheetOptions> {
  for (const landscape of [false, true]) {
    for (const doubleSided of [false, true]) {
      for (let scalePercent = 25; scalePercent <= 400; scalePercent += 2.5) {
        for (let cardsPerSheet = 1; cardsPerSheet <= 40; cardsPerSheet += 1) {
          yield { cardsPerSheet, scalePercent, landscape, doubleSided, cutMarks: true };
        }
      }
    }
  }
}

test("every sheet grid keeps all cards inside the printable area without empty rows", () => {
  let checked = 0;
  for (const options of sheetCombinations()) {
    const grid = idCardSheetGrid(options);
    if (grid == null) continue;
    checked += 1;
    const page = sheetPageSize(options.landscape);
    const cardWidth = CARD_WIDTH * grid.scale;
    const cardHeight = CARD_HEIGHT * grid.scale;
    const label = JSON.stringify(options);

    assert.ok(grid.columns >= 1 && grid.rows >= 1, label);
    assert.ok(cardWidth * grid.columns <= page.width - 2 * SHEET_MARGIN + EPSILON, `width ${label}`);
    assert.ok(cardHeight * grid.rows <= page.height - 2 * SHEET_MARGIN + EPSILON, `height ${label}`);

    // No more cards than requested, and no row without cards.
    const capacity = Math.floor((page.width - 2 * SHEET_MARGIN) / cardWidth) *
      Math.floor((page.height - 2 * SHEET_MARGIN) / cardHeight);
    const count = Math.min(options.cardsPerSheet, capacity);
    assert.ok(grid.columns * grid.rows >= count, `holds all ${label}`);
    assert.ok((grid.rows - 1) * grid.columns < count, `empty row ${label}`);
  }
  assert.ok(checked > 1000);
});

test("a card size above the largest size that fits has no grid", () => {
  for (const landscape of [false, true]) {
    const page = sheetPageSize(landscape);
    const largest = Math.min(
      (page.width - 2 * SHEET_MARGIN) / CARD_WIDTH,
      (page.height - 2 * SHEET_MARGIN) / CARD_HEIGHT,
    ) * 100;
    const options = { cardsPerSheet: 1, scalePercent: largest - 0.01, landscape, doubleSided: true, cutMarks: true };
    assert.deepEqual(idCardSheetGrid(options)?.columns, 1);
    assert.equal(idCardSheetGrid({ ...options, scalePercent: largest + 0.01 }), null);
  }
});

test("cards never overlap and every back sits behind its front in all sheet layouts", () => {
  for (const options of sheetCombinations()) {
    const grid = idCardSheetGrid(options);
    if (grid == null) continue;
    const page = sheetPageSize(options.landscape);
    const cardWidth = CARD_WIDTH * grid.scale;
    const cardHeight = CARD_HEIGHT * grid.scale;
    const label = JSON.stringify(options);
    const count = Math.min(options.cardsPerSheet, grid.columns * grid.rows);
    const frontCells = new Set<string>();
    const backCells = new Set<string>();

    for (let index = 0; index < count; index += 1) {
      const front = idCardSheetCardOrigin(options, grid, index, false);
      const back = idCardSheetCardOrigin(options, grid, index, true);
      for (const origin of [front, back]) {
        assert.ok(origin.x >= SHEET_MARGIN - EPSILON && origin.y >= SHEET_MARGIN - EPSILON, `start ${label}`);
        assert.ok(origin.x + cardWidth <= page.width - SHEET_MARGIN + EPSILON, `right ${label}`);
        assert.ok(origin.y + cardHeight <= page.height - SHEET_MARGIN + EPSILON, `bottom ${label}`);
      }
      frontCells.add(`${front.x.toFixed(3)},${front.y.toFixed(3)}`);
      backCells.add(`${back.x.toFixed(3)},${back.y.toFixed(3)}`);

      // Turning the sheet like a book page mirrors left and right around the middle of the page.
      const frontCenterX = front.x + cardWidth / 2;
      const backCenterX = back.x + cardWidth / 2;
      if (options.doubleSided) {
        assert.ok(Math.abs(frontCenterX + backCenterX - page.width) < 1e-3, `mirror ${label}`);
      } else {
        assert.ok(Math.abs(frontCenterX - backCenterX) < 1e-3, `same place ${label}`);
      }
      assert.ok(Math.abs(front.y - back.y) < 1e-3, `same row ${label}`);
    }
    assert.equal(frontCells.size, count, `front overlap ${label}`);
    assert.equal(backCells.size, count, `back overlap ${label}`);
  }
});
