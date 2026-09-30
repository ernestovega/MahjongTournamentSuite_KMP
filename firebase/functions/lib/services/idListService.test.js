"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const ExcelJS = require("exceljs");
const idListService_1 = require("./idListService");
(0, node_test_1.default)("ID list contains every value used by the ID cards", async () => {
    const bytes = await (0, idListService_1.buildIdList)({
        tournamentShortName: "MMC2026",
        year: "2026",
        primaryColor: "#02B16B",
        numberOfRounds: 2,
        players: [
            {
                playerId: 7,
                name: "María de la Cruz",
                country: "Spain",
                countryCode: "ESP",
                teamName: "Dragons",
                tableNumbers: [3, 8],
            },
            {
                playerId: 8,
                name: "Guest Player",
                country: "",
                countryCode: "EU",
                teamName: null,
                tableNumbers: [4, 1],
            },
        ],
    });
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(bytes);
    const sheet = workbook.getWorksheet("ID list");
    strict_1.default.ok(sheet);
    strict_1.default.deepEqual(sheet.getRow(1).values.slice(1), [...idListService_1.ID_LIST_BASE_HEADERS, "Round 1 table", "Round 2 table"]);
    strict_1.default.deepEqual(sheet.getRow(2).values.slice(1), [
        "MMC2026", "2026", 7, "María de la Cruz", "MARÍA", "DE LA CRUZ", "Spain", "", "Dragons", 3, 8,
    ]);
    strict_1.default.deepEqual(sheet.getRow(3).values.slice(1), ["MMC2026", "2026", 8, "Guest Player", "GUEST", "PLAYER", "", "", "", 4, 1]);
    strict_1.default.equal(sheet.getImages().length, 2);
    strict_1.default.equal(sheet.views[0].state, "frozen");
    strict_1.default.equal(sheet.autoFilter?.toString(), "A1:K1");
});
//# sourceMappingURL=idListService.test.js.map