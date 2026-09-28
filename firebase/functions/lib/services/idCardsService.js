"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildIdCardsPdf = buildIdCardsPdf;
exports.generateTournamentIdCards = generateTournamentIdCards;
const pdfkit_1 = __importDefault(require("pdfkit"));
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const countriesService_1 = require("./countriesService");
const playersService_1 = require("./playersService");
const CARD_WIDTH = 242.88;
const CARD_HEIGHT = 153;
const DEFAULT_PRIMARY_COLOR = "#02B16B";
const BACKGROUND_COLOR = "#FFFDF5";
const bodyFontPath = require.resolve("roboto-font/fonts/Roboto/roboto-regular-webfont.ttf");
const titleFontPath = require.resolve("roboto-font/fonts/Roboto_condensed/robotocondensed-bold-webfont.ttf");
function normalizeHexColor(value) {
    const color = value.trim().toUpperCase();
    return /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR;
}
function titleCase(value) {
    return value.toLocaleLowerCase().replace(/(^|[\s'-])\p{L}/gu, (match) => match.toLocaleUpperCase());
}
function valueOrFallback(value, fallback) {
    return value.trim().length === 0 ? fallback : value;
}
function splitPlayerName(value) {
    const words = value.trim().split(/\s+/).filter(Boolean);
    if (words.length <= 1)
        return { givenName: titleCase(words[0] ?? "Player"), surname: "" };
    return {
        givenName: titleCase(words[0]),
        surname: words.slice(1).join(" ").toLocaleUpperCase(),
    };
}
function fitText(doc, text, maxWidth, initialSize, minimumSize) {
    let size = initialSize;
    while (size > minimumSize) {
        doc.fontSize(size);
        if (doc.widthOfString(text) <= maxWidth)
            break;
        size -= 0.5;
    }
    return size;
}
function drawCurvedText(doc, text, centerX, centerY, radius, centerAngle, spread, fontSize, color, bottomArc = false) {
    const characters = [...text];
    const start = bottomArc ? centerAngle + spread / 2 : centerAngle - spread / 2;
    const step = characters.length > 1 ? spread / (characters.length - 1) : 0;
    characters.forEach((character, index) => {
        const degrees = bottomArc ? start - (step * index) : start + (step * index);
        const radians = degrees * Math.PI / 180;
        const x = centerX + radius * Math.cos(radians);
        const y = centerY + radius * Math.sin(radians);
        const rotation = bottomArc ? degrees - 90 : degrees + 90;
        doc.save();
        doc.translate(x, y);
        doc.rotate(rotation);
        doc.font("CardTitle").fontSize(fontSize).fillColor(color);
        const width = doc.widthOfString(character);
        doc.text(character, -width / 2, -fontSize / 2, { lineBreak: false });
        doc.restore();
    });
}
function drawFront(doc, model, player, associationLogo) {
    const primary = normalizeHexColor(model.primaryColor);
    const { givenName, surname } = splitPlayerName(player.name);
    doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).fill(BACKGROUND_COLOR);
    doc.rect(0, 0, CARD_WIDTH, 76).fill(primary);
    doc.circle(68.66, 71.39, 46.9).fill(primary);
    if (associationLogo != null) {
        doc.circle(68.66, 70.5, 34.5).fill("#FFFFFF");
        try {
            doc.save();
            doc.circle(68.66, 70.5, 32).clip();
            doc.image(associationLogo, 38.66, 40.5, { fit: [60, 60], align: "center", valign: "center" });
            doc.restore();
        }
        catch (_error) {
            doc.restore();
            // Keep the card usable if an old logo file has an unsupported format.
        }
    }
    drawCurvedText(doc, model.tournamentShortName, 68.66, 71.39, 53, -90, Math.min(124, 54 + model.tournamentShortName.length * 7), model.tournamentShortName.length > 8 ? 17 : 20, "#FFFFFF");
    drawCurvedText(doc, model.year, 68.66, 71.39, 52, 90, 94, 16, primary, true);
    doc.font("CardBody").fillColor("#FFFFFF");
    const countryText = valueOrFallback(player.country, "Guest");
    const countrySize = fitText(doc, countryText, 64, 7, 5);
    doc.fontSize(countrySize).text(countryText, 162, 10, { width: 68, align: "center", lineBreak: false });
    const teamText = player.teamName == null ? "" : player.teamName.trim();
    if (teamText.length > 0) {
        const teamSize = fitText(doc.font("CardTitle"), teamText, 93, 10, 6);
        doc.fontSize(teamSize).text(teamText, 142, 31, { width: 94, align: "center", lineBreak: false });
    }
    doc.font("CardTitle").fontSize(27).text(String(player.playerId), 151, 49, {
        width: 76,
        align: "center",
        lineBreak: false,
    });
    doc.font("CardTitle").fillColor("#000000");
    const givenSize = fitText(doc, givenName, 118, 29, 15);
    doc.fontSize(givenSize).text(givenName, 116, 86, { width: 120, align: "center", lineBreak: false });
    doc.font("CardBody");
    const surnameSize = fitText(doc, surname, 116, 9, 6);
    doc.fontSize(surnameSize).text(surname, 118, 126, { width: 116, align: "center", lineBreak: false });
}
function drawCellText(doc, text, x, y, width, height) {
    const textHeight = doc.heightOfString(text, { width: width - 4, align: "center" });
    doc.text(text, x + 2, y + Math.max(1, (height - textHeight) / 2), {
        width: width - 4,
        align: "center",
        lineBreak: false,
    });
}
function drawBack(doc, model, player) {
    const primary = normalizeHexColor(model.primaryColor);
    doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).fill(BACKGROUND_COLOR);
    doc.save();
    doc.opacity(0.28).font("CardTitle").fontSize(88).fillColor(primary);
    doc.text(String(player.playerId), 45, 35, {
        width: 153,
        height: 100,
        align: "center",
        lineBreak: false,
    });
    doc.restore();
    const x = 14;
    const y = 10;
    const width = CARD_WIDTH - 28;
    const headerHeight = 22;
    const availableRowsHeight = CARD_HEIGHT - y - 10 - headerHeight;
    const rowHeight = availableRowsHeight / Math.max(1, player.tableNumbers.length);
    const columns = [38, 42, 67, width - 38 - 42 - 67];
    const headers = ["Round", "Table", "Table points", "Score"];
    doc.lineWidth(0.65).strokeColor("#000000").fillColor("#000000").font("CardBody");
    doc.rect(x, y, width, headerHeight + rowHeight * player.tableNumbers.length).stroke();
    let columnX = x;
    columns.slice(0, -1).forEach((columnWidth) => {
        columnX += columnWidth;
        doc.moveTo(columnX, y).lineTo(columnX, y + headerHeight + rowHeight * player.tableNumbers.length).stroke();
    });
    doc.moveTo(x, y + headerHeight).lineTo(x + width, y + headerHeight).stroke();
    for (let index = 1; index < player.tableNumbers.length; index += 1) {
        const rowY = y + headerHeight + rowHeight * index;
        doc.moveTo(x, rowY).lineTo(x + width, rowY).stroke();
    }
    columnX = x;
    doc.fontSize(6.2);
    headers.forEach((header, index) => {
        drawCellText(doc, header, columnX, y, columns[index], headerHeight);
        columnX += columns[index];
    });
    const rowFontSize = Math.max(4.5, Math.min(8, rowHeight * 0.55));
    player.tableNumbers.forEach((tableNumber, index) => {
        const rowY = y + headerHeight + rowHeight * index;
        doc.fontSize(rowFontSize);
        drawCellText(doc, String(index + 1), x, rowY, columns[0], rowHeight);
        drawCellText(doc, tableNumber > 0 ? String(tableNumber) : "-", x + columns[0], rowY, columns[1], rowHeight);
    });
}
async function buildIdCardsPdf(model) {
    return new Promise((resolve, reject) => {
        const doc = new pdfkit_1.default({
            autoFirstPage: false,
            compress: true,
            info: { Title: `${model.tournamentShortName} ID cards` },
        });
        const chunks = [];
        doc.on("data", (chunk) => chunks.push(chunk));
        doc.on("error", reject);
        doc.on("end", () => resolve(Buffer.concat(chunks)));
        doc.registerFont("CardBody", bodyFontPath);
        doc.registerFont("CardTitle", titleFontPath);
        const reusableLogo = model.associationLogo == null
            ? null
            : doc.openImage(model.associationLogo);
        model.players.forEach((player) => {
            doc.addPage({ size: [CARD_WIDTH, CARD_HEIGHT], margin: 0 });
            drawFront(doc, model, player, reusableLogo);
            doc.addPage({ size: [CARD_WIDTH, CARD_HEIGHT], margin: 0 });
            drawBack(doc, model, player);
        });
        doc.end();
    });
}
async function generateTournamentIdCards(tournamentId) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(tournamentId);
    const [tournament, slots, teams, tables, countries] = await Promise.all([
        tournamentRef.get(),
        tournamentRef.collection("players").get(),
        tournamentRef.collection("teams").get(),
        tournamentRef.collection("tables").get(),
        (0, countriesService_1.listCountries)(),
    ]);
    if (!tournament.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const orderedSlots = slots.docs.slice().sort((left, right) => Number(left.get("id")) - Number(right.get("id")));
    const missingAssignments = orderedSlots.filter((slot) => typeof slot.get("assignedEmaId") !== "string");
    if (missingAssignments.length > 0) {
        throw (0, httpError_1.conflict)(`Assign every tournament player before creating ID cards. ${missingAssignments.length} assignments are missing.`);
    }
    const playerRefs = orderedSlots.map((slot) => {
        return firebase_1.db.collection(playersService_1.EMA_PLAYER_REGISTRY_COLLECTION).doc(String(slot.get("assignedEmaId")));
    });
    const playerDocuments = playerRefs.length > 0 ? await firebase_1.db.getAll(...playerRefs) : [];
    if (playerDocuments.some((player) => !player.exists)) {
        throw (0, httpError_1.conflict)("One or more assigned EMA players no longer exist");
    }
    const teamNames = new Map();
    teams.docs.forEach((team) => {
        teamNames.set(Number(team.get("id") ?? team.id), String(team.get("name") ?? "").trim());
    });
    const countryNames = new Map(countries.map((country) => [country.code, country.name]));
    const tablesByPlayer = new Map();
    tables.docs.forEach((table) => {
        const roundId = Number(table.get("roundId"));
        const tableId = Number(table.get("tableId"));
        const playerIds = Array.isArray(table.get("playerIds")) ? table.get("playerIds") : [];
        playerIds.forEach((rawPlayerId) => {
            const playerId = Number(rawPlayerId);
            const rounds = tablesByPlayer.get(playerId) ?? new Map();
            rounds.set(roundId, tableId);
            tablesByPlayer.set(playerId, rounds);
        });
    });
    const numRounds = Number(tournament.get("numRounds") ?? 0);
    const players = orderedSlots.map((slot, index) => {
        const player = playerDocuments[index];
        const playerId = Number(slot.get("id") ?? slot.id);
        const teamId = Number(slot.get("team") ?? 0);
        const countryCode = String(player.get("country") ?? "").trim().toUpperCase();
        const rounds = tablesByPlayer.get(playerId) ?? new Map();
        return {
            playerId,
            name: String(player.get("name") ?? `Player ${playerId}`),
            country: countryNames.get(countryCode) ?? countryCode,
            teamName: tournament.get("isTeams") === true ? teamNames.get(teamId) ?? `Team ${teamId}` : null,
            tableNumbers: Array.from({ length: numRounds }, (_, roundIndex) => rounds.get(roundIndex + 1) ?? 0),
        };
    });
    const logoPath = String(tournament.get("associationLogoPath") ?? "").trim();
    const associationLogo = logoPath.length > 0
        ? await firebase_1.storage.file(logoPath).download().then(([bytes]) => bytes).catch(() => null)
        : null;
    const eventStartDate = String(tournament.get("eventStartDate") ?? "");
    const storedShortName = String(tournament.get("shortName") ?? "").trim();
    const fallbackName = String(tournament.get("name") ?? "Tournament").trim().slice(0, 10);
    return buildIdCardsPdf({
        tournamentShortName: (storedShortName || fallbackName).slice(0, 10),
        year: /^\d{4}-/.test(eventStartDate) ? eventStartDate.slice(0, 4) : String(new Date().getUTCFullYear()),
        primaryColor: normalizeHexColor(String(tournament.get("primaryColor") ?? DEFAULT_PRIMARY_COLOR)),
        associationLogo,
        players,
    });
}
//# sourceMappingURL=idCardsService.js.map