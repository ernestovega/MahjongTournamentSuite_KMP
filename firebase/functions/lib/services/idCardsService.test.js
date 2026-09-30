"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const idCardsService_1 = require("./idCardsService");
(0, node_test_1.default)("creates one front and one back page for each player", async () => {
    const pdf = await (0, idCardsService_1.buildIdCardsPdf)({
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
    strict_1.default.equal(pdf.subarray(0, 5).toString("ascii"), "%PDF-");
    const pageObjects = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
    strict_1.default.equal(pageObjects.length, 2);
});
//# sourceMappingURL=idCardsService.test.js.map