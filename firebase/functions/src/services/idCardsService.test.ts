import assert from "node:assert/strict";
import test from "node:test";

import { buildIdCardsPdf } from "./idCardsService";

test("creates one front and one back page for each player", async () => {
  const pdf = await buildIdCardsPdf({
    tournamentShortName: "6th MMC",
    year: "2026",
    primaryColor: "#7C3AED",
    numberOfRounds: 7,
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
