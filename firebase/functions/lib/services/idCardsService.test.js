"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const idCardsService_1 = require("./idCardsService");
(0, node_test_1.default)("supports the European flag proof code without changing the EU guest code", () => {
    strict_1.default.equal((0, idCardsService_1.iso2CountryCode)("EUR"), "EU");
    strict_1.default.equal((0, idCardsService_1.iso2CountryCode)("EU"), null);
});
(0, node_test_1.default)("creates one front and one back page for each player", async () => {
    const pdf = await (0, idCardsService_1.buildIdCardsPdf)({
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
    strict_1.default.equal(pdf.subarray(0, 5).toString("ascii"), "%PDF-");
    const pageObjects = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
    strict_1.default.equal(pageObjects.length, 2);
});
(0, node_test_1.default)("adds one date row before the rounds of each configured day", () => {
    strict_1.default.deepEqual((0, idCardsService_1.buildIdCardBackRows)(4, [
        { roundId: 1, date: "2026-04-17", startTime: "09:30" },
        { roundId: 2, date: "2026-04-17", startTime: "13:30" },
        { roundId: 3, date: "2026-04-18", startTime: "09:00" },
        { roundId: 4, date: null, startTime: null },
    ], [11, 4, 21, 19]), [
        { kind: "date", date: "2026-04-17" },
        { kind: "round", roundId: 1, startTime: "09:30", tableNumber: 11 },
        { kind: "round", roundId: 2, startTime: "13:30", tableNumber: 4 },
        { kind: "date", date: "2026-04-18" },
        { kind: "round", roundId: 3, startTime: "09:00", tableNumber: 21 },
        { kind: "round", roundId: 4, startTime: null, tableNumber: 19 },
    ]);
});
(0, node_test_1.default)("creates a proof PDF with one front and one back page", async () => {
    const pdf = await (0, idCardsService_1.buildIdCardProofPdf)({
        shortName: "6th MMC",
        year: "2026",
        primaryColor: "#02B16B",
    });
    strict_1.default.equal(pdf.subarray(0, 5).toString("ascii"), "%PDF-");
    const pageObjects = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
    strict_1.default.equal(pageObjects.length, 2);
});
//# sourceMappingURL=idCardsService.test.js.map