"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.SHEET_MARGIN = exports.MISSING_FLAG_SVG = exports.CARD_HEIGHT = exports.CARD_WIDTH = void 0;
exports.splitPlayerName = splitPlayerName;
exports.iso2CountryCode = iso2CountryCode;
exports.flagSvgFor = flagSvgFor;
exports.buildIdCardBackRows = buildIdCardBackRows;
exports.sheetPageSize = sheetPageSize;
exports.idCardSheetGrid = idCardSheetGrid;
exports.idCardSheetCardOrigin = idCardSheetCardOrigin;
exports.buildIdCardsPdf = buildIdCardsPdf;
exports.buildIdCardProofPdf = buildIdCardProofPdf;
exports.loadIdCardsDocument = loadIdCardsDocument;
exports.generateTournamentIdCards = generateTournamentIdCards;
const pdfkit_1 = __importDefault(require("pdfkit"));
const node_fs_1 = require("node:fs");
const node_path_1 = require("node:path");
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const countriesService_1 = require("./countriesService");
const playersService_1 = require("./playersService");
const tournamentSchedule_1 = require("./tournamentSchedule");
exports.CARD_WIDTH = 242.88;
exports.CARD_HEIGHT = 153;
const DEFAULT_PRIMARY_COLOR = "#02B16B";
const BACKGROUND_COLOR = "#FFFDF5";
const POINTS_PER_MILLIMETER = 72 / 25.4;
const LOGO_CENTER_X = 68.66;
const LOGO_CENTER_Y = 71.39;
const LOGO_OUTER_RADIUS = 46.9;
const LOGO_WHITE_RADIUS = 34.5;
const PLAYER_DETAILS_CENTER_X = 176;
exports.MISSING_FLAG_SVG = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 36">
  <rect width="48" height="36" rx="2" fill="#808080"/>
  <path fill="#FFFFFF" d="M17 11.5C17.6 7.8 20.4 5.8 24.4 5.8c4.5 0 7.6 2.5 7.6 6.2 0 2.9-1.6 4.6-4.2 6.2-2 1.3-2.7 2.2-2.7 4.2v.8h-4.6v-1.1c0-3.2 1.4-5 4.2-6.8 1.6-1 2.2-1.9 2.2-3 0-1.5-1.1-2.5-2.9-2.5-1.9 0-3.1 1.1-3.5 3.2L17 11.5zM20.5 26h4.9v4.9h-4.9z"/>
</svg>`;
const appFontPath = (0, node_path_1.resolve)(__dirname, "../assets/go3v2.ttf");
const flagDirectory = (0, node_path_1.dirname)(require.resolve("flag-icons/flags/4x3/es.svg"));
const flagSvgCache = new Map();
const svgToPdf = require("svg-to-pdfkit");
function normalizeHexColor(value) {
    const color = value.trim().toUpperCase();
    return /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR;
}
function titleCase(value) {
    return value.toLocaleLowerCase().replace(/(^|[\s'-])\p{L}/gu, (match) => match.toLocaleUpperCase());
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
function iso2CountryCode(value) {
    const code = value.trim().toUpperCase();
    if (code === "EU" || code.length === 0)
        return null;
    if (/^[A-Z]{2}$/.test(code))
        return code;
    return emaCountryCodeToIso2[code] ?? null;
}
function flagSvgFor(countryCode) {
    const code = iso2CountryCode(countryCode ?? "");
    if (code == null)
        return null;
    const cached = flagSvgCache.get(code);
    if (cached !== undefined)
        return cached;
    try {
        const svg = (0, node_fs_1.readFileSync)((0, node_path_1.join)(flagDirectory, `${code.toLowerCase()}.svg`), "utf8");
        flagSvgCache.set(code, svg);
        return svg;
    }
    catch (_error) {
        flagSvgCache.set(code, null);
        return null;
    }
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
        doc.font("CardApp").fontSize(fontSize).fillColor(color);
        const width = doc.widthOfString(character);
        doc.text(character, -width / 2, -fontSize / 2, { lineBreak: false });
        doc.restore();
    });
}
function drawCenteredText(doc, text, width, height, fontSize) {
    const internalFont = doc._font?.font;
    const layout = internalFont?.layout(text);
    let textX = null;
    if (internalFont != null && layout != null && layout.glyphs.length === layout.positions.length) {
        let penX = 0;
        let inkMinX = Number.POSITIVE_INFINITY;
        let inkMaxX = Number.NEGATIVE_INFINITY;
        layout.glyphs.forEach((glyph, index) => {
            const position = layout.positions[index];
            const glyphX = penX + position.xOffset;
            inkMinX = Math.min(inkMinX, glyphX + glyph.bbox.minX);
            inkMaxX = Math.max(inkMaxX, glyphX + glyph.bbox.maxX);
            penX += position.xAdvance;
        });
        if (Number.isFinite(inkMinX) && Number.isFinite(inkMaxX) && inkMaxX > inkMinX) {
            const fontScale = fontSize / internalFont.unitsPerEm;
            const inkWidth = (inkMaxX - inkMinX) * fontScale;
            textX = (width - inkWidth) / 2 - inkMinX * fontScale;
        }
    }
    const textHeight = doc.heightOfString(text, { lineBreak: false });
    const textY = (height - textHeight) / 2;
    if (textX == null) {
        doc.text(text, 0, textY, { width, align: "center", lineBreak: false });
    }
    else {
        doc.text(text, textX, textY, { lineBreak: false });
    }
}
function drawCountryFlag(doc, countryCode) {
    const flagWidth = 24;
    const flagHeight = 18;
    const flagX = PLAYER_DETAILS_CENTER_X - flagWidth / 2;
    const flagY = 8;
    // A soft white glow around the four sides keeps flag colors that are close to the card color visible.
    // Each layer is a little larger and adds a little white, so the glow fades out from the flag edge.
    const glowLayers = 6;
    const glowSpread = 3.5;
    doc.save();
    doc.fillColor("#FFFFFF");
    for (let layer = glowLayers; layer >= 1; layer -= 1) {
        const grow = (glowSpread * layer) / glowLayers;
        doc.fillOpacity(0.08);
        doc.roundedRect(flagX - grow, flagY - grow, flagWidth + 2 * grow, flagHeight + 2 * grow, 1 + grow).fill();
    }
    doc.restore();
    doc.save();
    svgToPdf(doc, flagSvgFor(countryCode) ?? exports.MISSING_FLAG_SVG, flagX, flagY, {
        width: flagWidth,
        height: flagHeight,
        preserveAspectRatio: "xMidYMid meet",
    });
    doc.restore();
}
function drawFront(doc, model, player, associationLogo) {
    const primary = normalizeHexColor(model.primaryColor);
    const { givenName, surname } = splitPlayerName(player.name);
    doc.rect(0, 0, exports.CARD_WIDTH, exports.CARD_HEIGHT).fill(BACKGROUND_COLOR);
    doc.rect(0, 0, exports.CARD_WIDTH, 76).fill(primary);
    doc.circle(LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_OUTER_RADIUS).fill(primary);
    if (associationLogo != null) {
        const logoRadius = 32.5;
        doc.circle(LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_WHITE_RADIUS).fill("#FFFFFF");
        try {
            doc.save();
            doc.circle(LOGO_CENTER_X, LOGO_CENTER_Y, logoRadius).clip();
            doc.image(associationLogo, LOGO_CENTER_X - logoRadius, LOGO_CENTER_Y - logoRadius, {
                cover: [logoRadius * 2, logoRadius * 2],
                align: "center",
                valign: "center",
            });
            doc.restore();
        }
        catch (_error) {
            doc.restore();
            // Keep the card usable if an old logo file has an unsupported format.
        }
    }
    const tournamentNameFontSize = model.tournamentShortName.length > 8 ? 22 : 27;
    drawCurvedText(doc, model.tournamentShortName, LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_WHITE_RADIUS + tournamentNameFontSize * 0.45 - 0.625 * POINTS_PER_MILLIMETER, -90, Math.min(138, 56 + model.tournamentShortName.length * 8.2), tournamentNameFontSize, "#FFFFFF");
    const yearFontSize = 29;
    drawCurvedText(doc, model.year, LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_OUTER_RADIUS + yearFontSize * 0.2, 90, 50, yearFontSize, primary, true);
    doc.font("CardApp").fillColor("#FFFFFF");
    const teamText = player.teamName == null ? "" : player.teamName.trim();
    if (teamText.length > 0) {
        const teamSize = fitText(doc.font("CardApp"), teamText, 93, 10, 6);
        doc.fontSize(teamSize).text(teamText, PLAYER_DETAILS_CENTER_X - 47, 31, {
            width: 94,
            align: "center",
            lineBreak: false,
        });
    }
    doc.font("CardApp").fontSize(33.75).text(String(player.playerId), PLAYER_DETAILS_CENTER_X - 38, 49 + 0.5 * POINTS_PER_MILLIMETER - (33.75 - 27) / 2, {
        width: 76,
        align: "center",
        lineBreak: false,
    });
    doc.font("CardApp").fillColor("#000000");
    const givenSize = fitText(doc, givenName, 118, 29, 15);
    doc.fontSize(givenSize).text(givenName, 116, 86, { width: 120, align: "center", lineBreak: false });
    doc.font("CardApp");
    const surnameSize = fitText(doc, surname, 116, 9, 6);
    doc.fontSize(surnameSize).text(surname, 118, 126, { width: 116, align: "center", lineBreak: false });
    drawCountryFlag(doc, player.countryCode);
}
function drawCellText(doc, text, x, y, width, height) {
    const textHeight = doc.heightOfString(text, { width: width - 4, align: "center" });
    doc.text(text, x + 2, y + Math.max(1, (height - textHeight) / 2), {
        width: width - 4,
        align: "center",
        lineBreak: false,
    });
}
function displayCardDate(value) {
    return `${value.slice(8, 10)}/${value.slice(5, 7)}`;
}
function displayStart(row) {
    if (row.startTime == null)
        return "-";
    return row.date == null ? row.startTime : `${displayCardDate(row.date)} - ${row.startTime}`;
}
function buildIdCardBackRows(roundCount, roundSchedules, tableNumbers) {
    const scheduleByRound = new Map(roundSchedules.map((schedule) => [schedule.roundId, schedule]));
    const rows = [];
    let currentDate = null;
    for (let index = 0; index < roundCount; index += 1) {
        const roundId = index + 1;
        const schedule = scheduleByRound.get(roundId);
        const date = schedule?.date ?? null;
        rows.push({
            roundId,
            date,
            startTime: schedule?.startTime ?? null,
            tableNumber: tableNumbers[index] ?? 0,
            startsNewDay: index > 0 && date != null && currentDate != null && date !== currentDate,
        });
        if (date != null)
            currentDate = date;
    }
    return rows;
}
function drawBack(doc, model, player) {
    const primary = normalizeHexColor(model.primaryColor);
    doc.rect(0, 0, exports.CARD_WIDTH, exports.CARD_HEIGHT).fill(BACKGROUND_COLOR);
    doc.save();
    const backgroundNumber = String(player.playerId);
    const backgroundNumberSize = fitText(doc.font("CardApp"), backgroundNumber, 190, 132, 88);
    doc.opacity(0.28).fontSize(backgroundNumberSize).fillColor(primary);
    drawCenteredText(doc, backgroundNumber, exports.CARD_WIDTH, exports.CARD_HEIGHT, backgroundNumberSize);
    doc.restore();
    const x = 14;
    const y = 10;
    const width = exports.CARD_WIDTH - 28;
    const headerHeight = 22;
    const roundCount = Math.max(1, model.numberOfRounds ?? player.tableNumbers.length);
    const tableNumbers = Array.from({ length: roundCount }, (_, index) => player.tableNumbers[index] ?? 0);
    const rows = buildIdCardBackRows(roundCount, model.roundSchedules ?? [], tableNumbers);
    const availableRowsHeight = exports.CARD_HEIGHT - y - 10 - headerHeight;
    const rowHeight = availableRowsHeight / Math.max(1, rows.length);
    const columns = [28, 56, 32, 58, width - 28 - 56 - 32 - 58];
    const headers = ["Round", "Start", "Table", "Table points", "Score"];
    doc.lineWidth(0.65).strokeColor("#000000").fillColor("#000000").font("CardApp");
    doc.rect(x, y, width, headerHeight + rowHeight * rows.length).stroke();
    let columnX = x;
    columns.slice(0, -1).forEach((columnWidth) => {
        columnX += columnWidth;
        doc.moveTo(columnX, y).lineTo(columnX, y + headerHeight).stroke();
    });
    doc.moveTo(x, y + headerHeight).lineTo(x + width, y + headerHeight).stroke();
    for (let index = 1; index < rows.length; index += 1) {
        const rowY = y + headerHeight + rowHeight * index;
        doc.lineWidth(rows[index].startsNewDay ? 1.6 : 0.65);
        doc.moveTo(x, rowY).lineTo(x + width, rowY).stroke();
    }
    doc.lineWidth(0.65);
    columnX = x;
    doc.fontSize(5.6);
    headers.forEach((header, index) => {
        drawCellText(doc, header, columnX, y, columns[index], headerHeight);
        columnX += columns[index];
    });
    const rowFontSize = Math.max(4.5, Math.min(8, rowHeight * 0.55));
    rows.forEach((row, index) => {
        const rowY = y + headerHeight + rowHeight * index;
        doc.fontSize(rowFontSize);
        let cellX = x;
        columns.slice(0, -1).forEach((columnWidth) => {
            cellX += columnWidth;
            doc.moveTo(cellX, rowY).lineTo(cellX, rowY + rowHeight).stroke();
        });
        drawCellText(doc, String(row.roundId), x, rowY, columns[0], rowHeight);
        drawCellText(doc, displayStart(row), x + columns[0], rowY, columns[1], rowHeight);
        drawCellText(doc, row.tableNumber > 0 ? String(row.tableNumber) : "-", x + columns[0] + columns[1], rowY, columns[2], rowHeight);
    });
}
const A4_WIDTH = 595.28;
const A4_HEIGHT = 841.89;
exports.SHEET_MARGIN = 14;
const CUT_MARK_LENGTH = 8;
const CUT_MARK_OFFSET = 3;
function sheetPageSize(landscape) {
    return landscape ? { width: A4_HEIGHT, height: A4_WIDTH } : { width: A4_WIDTH, height: A4_HEIGHT };
}
/** Returns the grid for the options, or null when not even one card fits on the sheet. */
function idCardSheetGrid(options) {
    const scale = options.scalePercent / 100;
    const { width, height } = sheetPageSize(options.landscape);
    const maxColumns = Math.floor((width - 2 * exports.SHEET_MARGIN) / (exports.CARD_WIDTH * scale));
    const maxRows = Math.floor((height - 2 * exports.SHEET_MARGIN) / (exports.CARD_HEIGHT * scale));
    if (maxColumns < 1 || maxRows < 1)
        return null;
    const count = Math.max(1, Math.min(options.cardsPerSheet, maxColumns * maxRows));
    const columns = Math.min(maxColumns, count);
    const rows = Math.ceil(count / columns);
    return { columns, rows, scale };
}
/**
 * Returns where the top left corner of a card is on the sheet page, in points.
 * The sheet turns like a book page, so the columns of the back are mirrored when printing on both sides.
 */
function idCardSheetCardOrigin(sheet, grid, index, back) {
    const page = sheetPageSize(sheet.landscape);
    const left = (page.width - exports.CARD_WIDTH * grid.scale * grid.columns) / 2;
    const top = (page.height - exports.CARD_HEIGHT * grid.scale * grid.rows) / 2;
    const column = index % grid.columns;
    const row = Math.floor(index / grid.columns);
    const drawColumn = back && sheet.doubleSided ? grid.columns - 1 - column : column;
    return { x: left + drawColumn * exports.CARD_WIDTH * grid.scale, y: top + row * exports.CARD_HEIGHT * grid.scale };
}
function drawSheetCutMarks(doc, left, top, grid) {
    const cardWidth = exports.CARD_WIDTH * grid.scale;
    const cardHeight = exports.CARD_HEIGHT * grid.scale;
    const right = left + cardWidth * grid.columns;
    const bottom = top + cardHeight * grid.rows;
    doc.save().lineWidth(0.4).strokeColor("#000000");
    for (let column = 0; column <= grid.columns; column += 1) {
        const x = left + cardWidth * column;
        doc.moveTo(x, top - CUT_MARK_OFFSET).lineTo(x, top - CUT_MARK_OFFSET - CUT_MARK_LENGTH).stroke();
        doc.moveTo(x, bottom + CUT_MARK_OFFSET).lineTo(x, bottom + CUT_MARK_OFFSET + CUT_MARK_LENGTH).stroke();
    }
    for (let row = 0; row <= grid.rows; row += 1) {
        const y = top + cardHeight * row;
        doc.moveTo(left - CUT_MARK_OFFSET, y).lineTo(left - CUT_MARK_OFFSET - CUT_MARK_LENGTH, y).stroke();
        doc.moveTo(right + CUT_MARK_OFFSET, y).lineTo(right + CUT_MARK_OFFSET + CUT_MARK_LENGTH, y).stroke();
    }
    doc.restore();
}
function drawCardOnSheet(doc, x, y, scale, draw) {
    doc.save();
    doc.translate(x, y).scale(scale);
    doc.rect(0, 0, exports.CARD_WIDTH, exports.CARD_HEIGHT).clip();
    draw();
    doc.restore();
}
async function buildIdCardsPdf(model, sheet) {
    const grid = sheet == null ? null : idCardSheetGrid(sheet);
    if (sheet != null && grid == null)
        throw new Error("The card size does not fit on an A4 sheet.");
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
        doc.registerFont("CardApp", appFontPath);
        const reusableLogo = model.associationLogo == null
            ? null
            : doc.openImage(model.associationLogo);
        if (sheet != null && grid != null) {
            const perSheet = grid.columns * grid.rows;
            const page = sheetPageSize(sheet.landscape);
            const pageOptions = { size: [page.width, page.height], margin: 0 };
            const left = (page.width - exports.CARD_WIDTH * grid.scale * grid.columns) / 2;
            const top = (page.height - exports.CARD_HEIGHT * grid.scale * grid.rows) / 2;
            for (let start = 0; start < model.players.length; start += perSheet) {
                const group = model.players.slice(start, start + perSheet);
                doc.addPage(pageOptions);
                group.forEach((player, index) => {
                    const { x, y } = idCardSheetCardOrigin(sheet, grid, index, false);
                    drawCardOnSheet(doc, x, y, grid.scale, () => drawFront(doc, model, player, reusableLogo));
                });
                if (sheet.cutMarks)
                    drawSheetCutMarks(doc, left, top, grid);
                doc.addPage(pageOptions);
                group.forEach((player, index) => {
                    const { x, y } = idCardSheetCardOrigin(sheet, grid, index, true);
                    drawCardOnSheet(doc, x, y, grid.scale, () => drawBack(doc, model, player));
                });
            }
        }
        else {
            model.players.forEach((player) => {
                doc.addPage({ size: [exports.CARD_WIDTH, exports.CARD_HEIGHT], margin: 0 });
                drawFront(doc, model, player, reusableLogo);
                doc.addPage({ size: [exports.CARD_WIDTH, exports.CARD_HEIGHT], margin: 0 });
                drawBack(doc, model, player);
            });
        }
        doc.end();
    });
}
async function buildIdCardProofPdf(request) {
    let associationLogo = null;
    if (request.associationLogoDataBase64) {
        associationLogo = Buffer.from(request.associationLogoDataBase64, "base64");
    }
    else if (request.associationLogoUrl) {
        const logoUrl = new URL(request.associationLogoUrl);
        if (logoUrl.protocol === "https:" &&
            (logoUrl.hostname === "firebasestorage.googleapis.com" || logoUrl.hostname === "storage.googleapis.com")) {
            const response = await fetch(logoUrl);
            if (response.ok)
                associationLogo = Buffer.from(await response.arrayBuffer());
        }
    }
    const rawRoundSchedules = Array.isArray(request.roundSchedules) ? request.roundSchedules : [];
    const numberOfRounds = Math.max(1, ...rawRoundSchedules.map((value) => {
        if (value == null || typeof value !== "object")
            return 0;
        return Number(value.roundId) || 0;
    }));
    const configuredDates = rawRoundSchedules.flatMap((value) => {
        if (value == null || typeof value !== "object")
            return [];
        const date = String(value.date ?? "").trim();
        return /^\d{4}-\d{2}-\d{2}$/.test(date) ? [date] : [];
    });
    const sortedDates = configuredDates.slice().sort();
    const scheduleStartDate = sortedDates[0] ?? "0000-01-01";
    const scheduleEndDate = sortedDates[sortedDates.length - 1] ?? "9999-12-31";
    const roundSchedules = (0, tournamentSchedule_1.readStoredRoundSchedules)(request.roundSchedules, numberOfRounds, scheduleStartDate, scheduleEndDate);
    const pdf = await buildIdCardsPdf({
        tournamentShortName: request.shortName,
        year: request.year,
        primaryColor: request.primaryColor,
        associationLogo,
        numberOfRounds,
        roundSchedules,
        players: [{
                playerId: 21,
                name: "Ernesto Vega de la Iglesia",
                country: "Europe",
                countryCode: "EUR",
                teamName: "Mahjong Madrid",
                tableNumbers: Array.from({ length: numberOfRounds }, (_, index) => index + 1),
            }],
    });
    return pdf;
}
async function loadIdCardsDocument(tournamentId) {
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
    const missingAssignments = orderedSlots.filter((slot) => {
        return typeof slot.get("assignedEmaId") !== "string" && slot.get("nonMember") == null;
    });
    if (missingAssignments.length > 0) {
        throw (0, httpError_1.conflict)(`Assign every tournament player before creating ID cards. ${missingAssignments.length} assignments are missing.`);
    }
    const playerDocuments = await Promise.all(orderedSlots.map((slot) => {
        const emaId = slot.get("assignedEmaId");
        return typeof emaId === "string"
            ? firebase_1.db.collection(playersService_1.EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()
            : Promise.resolve(null);
    }));
    if (playerDocuments.some((player) => player != null && !player.exists)) {
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
        const nonMember = slot.get("nonMember");
        const countryCode = String(player?.get("country") ?? nonMember?.country ?? "").trim().toUpperCase();
        const rounds = tablesByPlayer.get(playerId) ?? new Map();
        return {
            playerId,
            name: player == null
                ? `${String(nonMember?.firstName ?? "")} ${String(nonMember?.lastName ?? "")}`.trim()
                : `${String(player.get("firstName") ?? "")} ${String(player.get("lastName") ?? "")}`.trim(),
            country: countryNames.get(iso2CountryCode(countryCode) ?? "") ?? countryCode,
            countryCode,
            teamName: tournament.get("isTeams") === true ? teamNames.get(teamId) ?? `Team ${teamId}` : null,
            tableNumbers: Array.from({ length: numRounds }, (_, roundIndex) => rounds.get(roundIndex + 1) ?? 0),
        };
    });
    const logoPath = String(tournament.get("associationLogoPath") ?? "").trim();
    const associationLogo = logoPath.length > 0
        ? await firebase_1.storage.file(logoPath).download().then(([bytes]) => bytes).catch(() => null)
        : null;
    const eventStartDate = String(tournament.get("eventStartDate") ?? "");
    const eventEndDate = String(tournament.get("eventEndDate") ?? eventStartDate);
    const storedShortName = String(tournament.get("shortName") ?? "").trim();
    const fallbackName = String(tournament.get("name") ?? "Tournament").trim().slice(0, 10);
    return {
        tournamentShortName: (storedShortName || fallbackName).slice(0, 10),
        year: /^\d{4}-/.test(eventStartDate) ? eventStartDate.slice(0, 4) : String(new Date().getUTCFullYear()),
        primaryColor: normalizeHexColor(String(tournament.get("primaryColor") ?? DEFAULT_PRIMARY_COLOR)),
        associationLogo,
        numberOfRounds: numRounds,
        roundSchedules: (0, tournamentSchedule_1.readStoredRoundSchedules)(tournament.get("roundSchedules"), numRounds, eventStartDate || "0000-01-01", eventEndDate || "9999-12-31"),
        players,
    };
}
async function generateTournamentIdCards(tournamentId, sheet) {
    return buildIdCardsPdf(await loadIdCardsDocument(tournamentId), sheet);
}
const emaCountryCodeToIso2 = {
    AUT: "AT", BEL: "BE", BLR: "BY", CHE: "CH", CZE: "CZ", DEU: "DE", DEN: "DK", DNK: "DK", EUR: "EU",
    ESP: "ES", FIN: "FI", FRA: "FR", GBR: "GB", GER: "DE", HUN: "HU", IRL: "IE", ITA: "IT",
    LAT: "LV", NED: "NL", NLD: "NL", NOR: "NO", POL: "PL", POR: "PT", PRT: "PT", ROU: "RO",
    RUS: "RU", SUI: "CH", SVK: "SK", SWE: "SE", UKR: "UA",
};
//# sourceMappingURL=idCardsService.js.map