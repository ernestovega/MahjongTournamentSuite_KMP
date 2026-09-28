"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EMA_REPORT_HEADERS = void 0;
exports.buildEmaReport = buildEmaReport;
exports.generateEmaReport = generateEmaReport;
const ExcelJS = require("exceljs");
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const mers_1 = require("./mers");
const playersService_1 = require("./playersService");
exports.EMA_REPORT_HEADERS = [
    "Tournament name",
    "Number of participants",
    "Place",
    "Player's first name",
    "Player's last name",
    "EMA number",
    "Table points",
    "Score",
    "EMA member",
    "Country",
    "Date",
    "Countrycourt",
    "city",
    "mers",
    "shortname",
    "rules",
    "period",
    "NbDays",
    "Extra",
];
function dateFromIso(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!match)
        throw (0, httpError_1.badRequest)("Tournament date must use yyyy-MM-dd");
    return new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 12));
}
function periodText(startDate, endDate) {
    const start = dateFromIso(startDate);
    const end = dateFromIso(endDate);
    const months = ["Jan.", "Feb.", "Mar.", "Apr.", "May", "Jun.", "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec."];
    const startDay = start.getUTCDate();
    const endDay = end.getUTCDate();
    const startMonth = months[start.getUTCMonth()];
    const endMonth = months[end.getUTCMonth()];
    const startYear = start.getUTCFullYear();
    const endYear = end.getUTCFullYear();
    if (startDate === endDate)
        return `${startDay} ${startMonth} ${startYear}`;
    if (startYear === endYear && start.getUTCMonth() === end.getUTCMonth()) {
        return `${startDay}-${endDay} ${startMonth} ${startYear}`;
    }
    if (startYear === endYear)
        return `${startDay} ${startMonth}-${endDay} ${endMonth} ${startYear}`;
    return `${startDay} ${startMonth} ${startYear}-${endDay} ${endMonth} ${endYear}`;
}
async function buildEmaReport(input) {
    if (!/^[A-Z]{3}$/.test(input.hostCountry.trim().toUpperCase()) || input.hostCity.trim().length === 0) {
        throw (0, httpError_1.badRequest)("Host country and host city are required before EMA export");
    }
    if (input.rows.some((row) => row.firstName.trim().length === 0 || row.lastName.trim().length === 0)) {
        throw (0, httpError_1.badRequest)("Every EMA report player needs a first name and last name");
    }
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet("MCR template", { views: [{ state: "frozen", ySplit: 1 }] });
    sheet.addRow([...exports.EMA_REPORT_HEADERS]);
    const eventDate = dateFromIso(input.startDate);
    const period = periodText(input.startDate, input.endDate);
    const days = (0, mers_1.inclusiveDayCount)(input.startDate, input.endDate);
    input.rows.slice().sort((left, right) => left.place - right.place).forEach((row) => {
        sheet.addRow([
            input.tournamentName,
            input.participantCount,
            row.place,
            row.firstName,
            row.lastName,
            row.emaId == null ? "" : (0, playersService_1.validateEmaId)(row.emaId),
            row.tablePoints,
            row.score,
            row.isEmaMember ? "YES" : "NO",
            row.country,
            eventDate,
            input.hostCountry,
            input.hostCity,
            input.mers,
            input.shortName,
            "Chinese official",
            period,
            days,
            "NO",
        ]);
    });
    const header = sheet.getRow(1);
    header.font = { bold: true };
    header.alignment = { horizontal: "center", vertical: "middle", wrapText: true };
    header.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFD9E1F2" } };
    sheet.eachRow((row) => row.eachCell((cell) => {
        cell.border = {
            top: { style: "thin" }, left: { style: "thin" },
            bottom: { style: "thin" }, right: { style: "thin" },
        };
    }));
    sheet.getColumn(6).numFmt = "@";
    sheet.getColumn(11).numFmt = "D/M/YYYY";
    sheet.columns.forEach((column, index) => {
        const longest = Math.max(exports.EMA_REPORT_HEADERS[index].length, ...input.rows.map((row) => {
            if (index === 3)
                return row.firstName.length;
            if (index === 4)
                return row.lastName.length;
            return 0;
        }));
        column.width = Math.min(32, Math.max(11, longest + 2));
    });
    return Buffer.from(await workbook.xlsx.writeBuffer());
}
async function generateEmaReport(params) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const [tournament, slots] = await Promise.all([tournamentRef.get(), tournamentRef.collection("players").get()]);
    if (!tournament.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const slotsById = new Map(slots.docs.map((slot) => [Number(slot.get("id") ?? slot.id), slot]));
    const emaIds = [...new Set(params.rows.map((row) => slotsById.get(row.playerId)?.get("assignedEmaId"))
            .filter((value) => typeof value === "string" && value.length > 0))];
    const registry = await Promise.all(emaIds.map((emaId) => firebase_1.db.collection(playersService_1.EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()));
    const playersById = new Map(registry.filter((player) => player.exists).map((player) => [player.id, player]));
    const reportRows = params.rows.map((ranking) => {
        const slot = slotsById.get(ranking.playerId);
        if (!slot)
            throw (0, httpError_1.badRequest)("Ranking contains an unknown tournament player", { playerId: ranking.playerId });
        const emaId = typeof slot.get("assignedEmaId") === "string" ? String(slot.get("assignedEmaId")) : null;
        if (emaId != null) {
            const player = playersById.get(emaId);
            if (!player)
                throw (0, httpError_1.badRequest)("Assigned EMA player does not exist", { playerId: ranking.playerId, emaId });
            return {
                ...ranking,
                emaId,
                firstName: String(player.get("firstName") ?? ""),
                lastName: String(player.get("lastName") ?? ""),
                country: String(player.get("country") ?? ""),
                isEmaMember: true,
            };
        }
        const nonMember = slot.get("nonMember");
        if (!nonMember)
            throw (0, httpError_1.badRequest)("Every ranked player must have an EMA or non-member assignment", { playerId: ranking.playerId });
        return {
            ...ranking,
            emaId: null,
            firstName: String(nonMember.firstName ?? ""),
            lastName: String(nonMember.lastName ?? ""),
            country: String(nonMember.country ?? ""),
            isEmaMember: false,
        };
    });
    return buildEmaReport({
        tournamentName: String(tournament.get("name") ?? ""),
        participantCount: Number(tournament.get("numPlayers") ?? params.rows.length),
        startDate: String(tournament.get("eventStartDate") ?? ""),
        endDate: String(tournament.get("eventEndDate") ?? ""),
        hostCountry: String(tournament.get("hostCountry") ?? ""),
        hostCity: String(tournament.get("hostCity") ?? ""),
        mers: Number(tournament.get("mers") ?? 0),
        shortName: String(tournament.get("shortName") ?? ""),
        rows: reportRows,
    });
}
//# sourceMappingURL=emaReportService.js.map