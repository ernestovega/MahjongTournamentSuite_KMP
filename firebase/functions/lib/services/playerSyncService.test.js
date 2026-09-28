"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const playerSyncService_1 = require("./playerSyncService");
(0, node_test_1.default)("tournament rows provide separate source-case name parts and padded EMA IDs", () => {
    const html = `<div class="TCTT_ligne">
    <p class="contenuCelluleC">1</p>
    <p class="contenuCelluleC"><a href="../Players/08010039.html">08010039</a></p>
    <p class="contenuCelluleG">K&Ouml;STERS</p>
    <p class="contenuCelluleG">ANTON</p>
    <p class="contenuCelluleC"><a href="../Country/NED_Information.html"><img src="../Img/flag/16/nl.png"/></a></p>
  </div>`;
    strict_1.default.deepEqual((0, playerSyncService_1.parseTournamentPlayerNames)(html), [{
            emaId: "08010039",
            sourceEmaId: "08010039",
            firstName: "ANTON",
            lastName: "KÖSTERS",
            country: "NED",
        }]);
});
//# sourceMappingURL=playerSyncService.test.js.map