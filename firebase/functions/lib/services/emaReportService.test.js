"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const ExcelJS = require("exceljs");
const emaReportService_1 = require("./emaReportService");
(0, node_test_1.default)("EMA report keeps exact columns and typed Excel values", async () => {
    const bytes = await (0, emaReportService_1.buildEmaReport)({
        tournamentName: "Madrid MCR 2025 (5th)",
        participantCount: 100,
        startDate: "2025-02-01",
        endDate: "2025-02-02",
        hostCountry: "ESP",
        hostCity: "Madrid",
        shortName: "MMC2025",
        rows: [{
                place: 1,
                firstName: "ANTON",
                lastName: "KÖSTERS",
                emaId: "8010039",
                tablePoints: 24,
                score: 312,
                isEmaMember: true,
                country: "NED",
            }],
    });
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(bytes);
    const sheet = workbook.getWorksheet("MCR template");
    strict_1.default.ok(sheet);
    const headerValues = sheet.getRow(1).values;
    strict_1.default.deepEqual(headerValues.slice(1), [...emaReportService_1.EMA_REPORT_HEADERS]);
    strict_1.default.equal(sheet.getCell("F2").value, "08010039");
    strict_1.default.equal(sheet.getCell("F2").numFmt, "@");
    strict_1.default.equal(sheet.getCell("D2").value, "Anton");
    strict_1.default.equal(sheet.getCell("E2").value, "KÖSTERS");
    strict_1.default.ok(sheet.getCell("K2").value instanceof Date);
    strict_1.default.equal(sheet.getCell("K2").numFmt, "d/m/yyyy");
    strict_1.default.equal(sheet.getCell("O2").value, "Chinese official");
    strict_1.default.equal(sheet.getCell("R2").value, "NO");
});
//# sourceMappingURL=emaReportService.test.js.map