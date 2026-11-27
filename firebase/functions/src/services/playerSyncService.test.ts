import assert from "node:assert/strict";
import test from "node:test";

import { parseTournamentPlayerNames } from "./playerSyncService";

test("tournament rows provide separate source-case name parts and padded EMA IDs", () => {
  const html = `<div class="TCTT_ligne">
    <p class="contenuCelluleC">1</p>
    <p class="contenuCelluleC"><a href="../Players/08010039.html">08010039</a></p>
    <p class="contenuCelluleG">K&Ouml;STERS</p>
    <p class="contenuCelluleG">ANTON</p>
    <p class="contenuCelluleC"><a href="../Country/NED_Information.html"><img src="../Img/flag/16/nl.png"/></a></p>
  </div>`;
  assert.deepEqual(parseTournamentPlayerNames(html), [{
    emaId: "08010039",
    sourceEmaId: "08010039",
    firstName: "ANTON",
    lastName: "KÖSTERS",
    country: "NED",
  }]);
});

test("skips result rows without the id cell or the name cells, and reads a row without a country", () => {
  const html = [
    `<div class="TCTT_ligne"><p>Rank</p><p>Name</p></div>`,
    `<div class="TCTT_ligne"><p><a href="../Players/04290001.html">other</a></p><p>RUIZ</p><p>ANA</p></div>`,
    `<div class="TCTT_ligne"><p><a href="../Players/04290002.html">04290002</a></p><p>RUIZ</p></div>`,
    `<div class="TCTT_ligne"><p><a href="../Players/04290003.html">04290003</a></p><p>RUIZ</p><p>BEA</p></div>`,
  ].join("");
  assert.deepEqual(parseTournamentPlayerNames(html).map((player) => [player.emaId, player.country]), [["04290003", ""]]);
});
