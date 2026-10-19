"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const ExcelJS = require("exceljs");
const firebase_1 = require("../firebase");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const emaReportService_1 = require("./emaReportService");
(0, node_test_1.describe)("EMA report with a slot that has no person", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("leaves out the slot and keeps the places of the other players", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [] });
        const tournament = firebase_1.db.collection("tournaments").doc(id);
        await tournament.update({ name: "Test", numPlayers: 3, eventStartDate: "2026-10-16", eventEndDate: "2026-10-17", hostCountry: "ESP", hostCity: "Madrid", shortName: "TEST" });
        for (const [emaId, firstName] of [["10000001", "ANA"], ["10000002", "BEA"]]) {
            await firebase_1.db.collection("emaPlayerRegistry").doc(emaId).set({ firstName, lastName: "LOPEZ", country: "ESP" });
        }
        await tournament.collection("players").doc("1").set({ id: 1, assignedEmaId: "10000001", nonMember: null });
        await tournament.collection("players").doc("2").set({ id: 2, assignedEmaId: null, nonMember: null });
        await tournament.collection("players").doc("3").set({ id: 3, assignedEmaId: "10000002", nonMember: null });
        const bytes = await (0, emaReportService_1.generateEmaReport)({
            tournamentId: id,
            rows: [
                { playerId: 1, place: 1, tablePoints: 10, score: 100 },
                { playerId: 2, place: 2, tablePoints: 8, score: 50 },
                { playerId: 3, place: 3, tablePoints: 4, score: -20 },
            ],
        });
        const workbook = new ExcelJS.Workbook();
        await workbook.xlsx.load(bytes);
        const sheet = workbook.getWorksheet("MCR template");
        strict_1.default.ok(sheet);
        strict_1.default.equal(sheet.rowCount, 3);
        strict_1.default.equal(sheet.getCell("C2").value, 1);
        strict_1.default.equal(sheet.getCell("C3").value, 3);
    });
    (0, node_test_1.it)("leaves out a player that the head referee excluded after an illness, and keeps a disqualified player", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [] });
        const tournament = firebase_1.db.collection("tournaments").doc(id);
        await tournament.update({
            name: "Test", numPlayers: 3, eventStartDate: "2026-10-16", eventEndDate: "2026-10-17",
            hostCountry: "ESP", hostCity: "Madrid", shortName: "TEST",
        });
        for (const [emaId, firstName] of [["10000001", "ANA"], ["10000002", "BEA"], ["10000003", "CLARA"]]) {
            await firebase_1.db.collection("emaPlayerRegistry").doc(emaId).set({ firstName, lastName: "LOPEZ", country: "ESP" });
        }
        await tournament.collection("players").doc("1").set({
            id: 1, assignedEmaId: "10000001", nonMember: null,
            substitution: { reason: "ILLNESS", fromRound: 2, duringSession: false, excludedFromEma: true },
        });
        await tournament.collection("players").doc("2").set({
            id: 2, assignedEmaId: "10000002", nonMember: null,
            substitution: { reason: "DISQUALIFIED", fromRound: 2, duringSession: false, excludedFromEma: false },
        });
        await tournament.collection("players").doc("3").set({ id: 3, assignedEmaId: "10000003", nonMember: null });
        const bytes = await (0, emaReportService_1.generateEmaReport)({
            tournamentId: id,
            rows: [
                { playerId: 1, place: 1, tablePoints: 10, score: 100 },
                { playerId: 3, place: 2, tablePoints: 8, score: 50 },
                { playerId: 2, place: 3, tablePoints: 0, score: 0 },
            ],
        });
        const workbook = new ExcelJS.Workbook();
        await workbook.xlsx.load(bytes);
        const sheet = workbook.getWorksheet("MCR template");
        strict_1.default.ok(sheet);
        strict_1.default.equal(sheet.rowCount, 3);
        strict_1.default.equal(sheet.getCell("D2").value, "Clara");
        strict_1.default.equal(sheet.getCell("D3").value, "Bea");
        strict_1.default.equal(sheet.getCell("G3").value, 0);
    });
});
//# sourceMappingURL=emaReport.integration.test.js.map