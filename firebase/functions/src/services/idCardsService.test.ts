import assert from "node:assert/strict";
import test from "node:test";

import { buildIdCardBackRows, buildIdCardProofPdf, buildIdCardsPdf, iso2CountryCode } from "./idCardsService";

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

test("adds one date row before the rounds of each configured day", () => {
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
      { kind: "date", date: "2026-04-17" },
      { kind: "round", roundId: 1, startTime: "09:30", tableNumber: 11 },
      { kind: "round", roundId: 2, startTime: "13:30", tableNumber: 4 },
      { kind: "date", date: "2026-04-18" },
      { kind: "round", roundId: 3, startTime: "09:00", tableNumber: 21 },
      { kind: "round", roundId: 4, startTime: null, tableNumber: 19 },
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
