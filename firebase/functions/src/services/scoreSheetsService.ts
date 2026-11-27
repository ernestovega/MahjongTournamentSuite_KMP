import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { conflict, notFound } from "../api/httpError";
import { db, storage } from "../firebase";
import { pdfKit } from "./lazyModules";
import { mainOrganizerLogoPath, organizersOf, readOrganizerIds } from "./organizationsService";
import { HANDS_PER_TABLE } from "./tableManagerService";

/**
 * The score sheets of a tournament, to print and fill in by hand at the tables: one A4 sheet with a row for each hand,
 * the score and the penalty of each seat, the running total, the table points and the signatures. The chicken hand
 * column is there only when the tournament counts chicken hands, and the last column is a box to tick once the hand
 * adds up to 0. The sheet is blank, or it has the round and the table written in, one sheet for each table of each
 * round. See docs/score-sheets.md.
 */

/** Blank: one sheet to copy. Tables: one sheet for each table of each round, with its round and table. */
export type ScoreSheetsMode = "blank" | "tables";

export type ScoreSheetsOptions = {
  mode: ScoreSheetsMode;
  /** The logo of the EMA beside the logo of the organizer. */
  emaLogo: boolean;
};

export function readScoreSheetsOptions(query: Record<string, unknown>): ScoreSheetsOptions {
  const ema = String(query.ema ?? "true").toLowerCase();
  return {
    mode: query.mode === "tables" ? "tables" : "blank",
    emaLogo: !(ema === "false" || ema === "0" || ema === "no"),
  };
}

export type ScoreSheetsDocument = {
  /** The short name of the tournament, in the top left corner. */
  tournamentName: string;
  /** The logo of the main organizer (PNG or JPEG), or null: then the initial of the organizer on its color. */
  logo: Buffer | null;
  organizerName: string;
  organizerColor: string;
  chickenHands: boolean;
  emaLogo: boolean;
  /** The sheets: an empty list for a blank one, or a round and table for each sheet. */
  sheets: { round: number; table: number }[];
};

const DEFAULT_ORGANIZER_COLOR = "#02B16B";
const A4_WIDTH = 595.28;
const A4_HEIGHT = 841.89;
/** 5 mm, as the margins of the original sheet. */
const MARGIN = 14.17;

const assetsDir = resolve(__dirname, "../assets");
const appFontPath = resolve(assetsDir, "go3v2.ttf");

type Images = { ema: Buffer; chicken: Buffer; winds: Buffer[] };
let images: Images | null = null;

/** The icons of the app and the logo of the EMA, copied to lib/assets by the build (scripts/syncIdCardAssets.cjs). */
function appImages(): Images {
  if (images == null) {
    const read = (name: string) => readFileSync(resolve(assetsDir, name));
    images = {
      ema: read("logo_ema.png"),
      chicken: read("icon_chicken.png"),
      winds: ["east", "south", "west", "north"].map((wind) => read(`icon_wind_${wind}.png`)),
    };
  }
  return images;
}

export async function loadScoreSheetsDocument(tournamentId: string, options: ScoreSheetsOptions): Promise<ScoreSheetsDocument> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const tableCount = Math.floor(Number(tournament.get("numPlayers") ?? 0) / 4);
  const roundCount = Math.max(0, Math.floor(Number(tournament.get("numRounds") ?? 0)));
  if (options.mode === "tables" && (tableCount < 1 || roundCount < 1)) {
    throw conflict("The tournament has no tables yet", { code: "NO_TABLES" });
  }
  const organizerIds = readOrganizerIds(tournament.get("organizerIds"));
  const mainOrganizer = (await organizersOf(organizerIds))[0];
  const logoPath = await mainOrganizerLogoPath(organizerIds);
  const logo = logoPath.length > 0 ? await storage.file(logoPath).download().then(([bytes]) => bytes).catch(() => null) : null;
  const name = String(tournament.get("name") ?? "").trim();
  const shortName = String(tournament.get("shortName") ?? "").trim();
  const color = String(mainOrganizer?.primaryColor ?? DEFAULT_ORGANIZER_COLOR).trim().toUpperCase();
  return {
    tournamentName: shortName.length > 0 ? shortName : name,
    logo,
    organizerName: mainOrganizer?.name ?? name,
    organizerColor: /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_ORGANIZER_COLOR,
    chickenHands: tournament.get("countChickenHands") !== false,
    emaLogo: options.emaLogo,
    sheets: options.mode === "tables" ? scoreSheetList(roundCount, tableCount) : [],
  };
}

/** Every table of round 1, then every table of round 2, and so on: the order in which they are handed out. */
export function scoreSheetList(roundCount: number, tableCount: number): { round: number; table: number }[] {
  const sheets: { round: number; table: number }[] = [];
  for (let round = 1; round <= roundCount; round += 1) {
    for (let table = 1; table <= tableCount; table += 1) sheets.push({ round, table });
  }
  return sheets;
}

/**
 * The columns of the sheet from left to right, in the proportions of the original spreadsheet: #, winner, loser, hand
 * points, chicken hand (only when the tournament counts them), score and penalty of each of the four seats, and Sum 0.
 */
export function scoreSheetColumns(chickenHands: boolean, width = A4_WIDTH - MARGIN * 2): ScoreSheetColumns {
  // Without the chicken column its room goes to the ids and the points, so the seats stay where they are.
  const id = chickenHands ? 8 : 8 + 5 / 3;
  const units = { number: 3.25, id, points: id, chicken: chickenHands ? 5 : 0, score: 8.88, penalty: 4.63, check: 5 };
  const total = units.number + units.id * 2 + units.points + units.chicken + (units.score + units.penalty) * 4 + units.check;
  const scale = width / total;
  return {
    number: units.number * scale,
    winner: units.id * scale,
    loser: units.id * scale,
    points: units.points * scale,
    chicken: units.chicken * scale,
    score: units.score * scale,
    penalty: units.penalty * scale,
    check: units.check * scale,
  };
}

export type ScoreSheetColumns = {
  number: number; winner: number; loser: number; points: number; chicken: number; score: number; penalty: number; check: number;
};

const WINDS = ["East", "South", "West", "North"];
const BLACK = "#000000";
const GREY = "#555555";
const THIN = 0.6;
const THICK = 1.6;

/** The gong of the Timer of the app (GongIcon.kt), in its 24 x 24 grid: the round. */
function drawGong(doc: PDFKit.PDFDocument, x: number, y: number, size: number): void {
  doc.save().translate(x, y).scale(size / 24).fillColor(BLACK).strokeColor(BLACK);
  doc.rect(1.5, 2, 21, 1.6).rect(3, 3.6, 1.4, 17.4).rect(19.6, 3.6, 1.4, 17.4).rect(1.5, 21, 4.5, 1.4).rect(18, 21, 4.5, 1.4).fill();
  doc.lineWidth(0.9).lineCap("round").moveTo(8.6, 3.6).lineTo(10, 7.4).moveTo(15.4, 3.6).lineTo(14, 7.4).stroke();
  for (const r of [6.4, 5, 4.4, 1.9, 1.1]) doc.circle(12, 13.2, r);
  doc.fill("even-odd");
  doc.restore();
}

/** The table of the Tables tab of the app (Material "table_restaurant"): the table. */
function drawTable(doc: PDFKit.PDFDocument, x: number, y: number, size: number): void {
  doc.save().translate(x, y).scale(size / 24).fillColor(BLACK);
  doc.path(
    "M21.96,9.73l-1.43,-5C20.41,4.3 20.02,4 19.57,4H4.43C3.98,4 3.59,4.3 3.47,4.73l-1.43,5C1.86,10.36 2.33,11 2.99,11H5.2l-1.2,9h2"
      + "l0.67,-5h10.67l0.66,5h2l-1.2,-9h2.21C21.67,11 22.14,10.36 21.96,9.73zM6.93,13l0.27,-2h9.6l0.27,2H6.93z",
  ).fill("even-odd");
  doc.restore();
}

/** An empty box to tick by hand. */
function drawCheckbox(doc: PDFKit.PDFDocument, centerX: number, centerY: number, size: number): void {
  doc.save().lineWidth(0.8).strokeColor(BLACK).roundedRect(centerX - size / 2, centerY - size / 2, size, size, size * 0.18).stroke().restore();
}

/** Helvetica reads best; the font of the app has the letters that Helvetica lacks, such as Cyrillic. */
function fontFor(text: string, bold: boolean): string {
  if (!/^[\u0000-ÿ]*$/.test(text)) return "SheetApp";
  return bold ? "Helvetica-Bold" : "Helvetica";
}

/** Writes [lines] centred in a box, one under the other, without ever letting pdfkit start a new page. */
function centeredLines(doc: PDFKit.PDFDocument, lines: string[], x: number, y: number, width: number, height: number, size: number): void {
  const lineHeight = size * 1.12;
  const top = y + (height - lineHeight * lines.length) / 2 + size * 0.1;
  lines.forEach((line, index) => {
    const lineWidth = doc.fontSize(size).widthOfString(line);
    doc.text(line, x + (width - lineWidth) / 2, top + index * lineHeight, { lineBreak: false, height: 100000 });
  });
}

/** Writes [text] ending at [right], centred on [centerY]. */
function rightAligned(doc: PDFKit.PDFDocument, text: string, right: number, centerY: number, size: number): void {
  const width = doc.fontSize(size).widthOfString(text);
  doc.text(text, right - width, centerY - size * 0.4, { lineBreak: false, height: 100000 });
}

/** The largest font size from [max] down to [min] at which every line of [lines] fits in [width]. */
function fitFontSize(doc: PDFKit.PDFDocument, lines: string[], width: number, max: number, min: number): number {
  for (let size = max; size > min; size -= 0.5) {
    if (lines.every((line) => doc.fontSize(size).widthOfString(line) <= width)) return size;
  }
  return min;
}

/** The short name in up to two lines, split at a space near the middle when it does not fit in one. */
export function titleLines(name: string): string[] {
  const words = name.trim().split(/\s+/).filter((word) => word.length > 0);
  if (words.length < 2) return [name.trim()];
  let best: string[] = [name.trim()];
  let bestLength = Number.MAX_SAFE_INTEGER;
  for (let split = 1; split < words.length; split += 1) {
    const lines = [words.slice(0, split).join(" "), words.slice(split).join(" ")];
    const longest = Math.max(...lines.map((line) => line.length));
    if (longest < bestLength) {
      bestLength = longest;
      best = lines;
    }
  }
  return best;
}

/** The logo of the organizer in a circle, as the app shows it, or its initial on its color. */
function drawOrganizerLogo(doc: PDFKit.PDFDocument, model: ScoreSheetsDocument, logo: SheetImage | null, x: number, y: number, size: number): void {
  const drawInitial = () => {
    doc.save().circle(x + size / 2, y + size / 2, size / 2).fill(model.organizerColor).restore();
    const initial = model.organizerName.trim().slice(0, 1).toUpperCase();
    if (initial.length > 0) {
      doc.font(fontFor(initial, true)).fillColor("#FFFFFF");
      centeredLines(doc, [initial], x, y, size, size, size * 0.5);
    }
  };
  if (logo == null) {
    drawInitial();
    return;
  }
  doc.save();
  doc.circle(x + size / 2, y + size / 2, size / 2).clip();
  doc.image(logo, x, y, { cover: [size, size], align: "center", valign: "center" });
  doc.restore();
}

/** An image read once for the whole PDF, so it is stored once however many sheets use it. */
// pdfkit draws an opened image as it draws bytes; its types list only bytes and paths.
type SheetImage = PDFKit.Mixins.ImageSrc;
type SheetImages = { ema: SheetImage; chicken: SheetImage; winds: SheetImage[]; logo: SheetImage | null };

/** pdfkit reads an image once with openImage, which its types do not list. */
function openImage(doc: PDFKit.PDFDocument, bytes: Buffer): SheetImage {
  return (doc as unknown as { openImage(source: Buffer): SheetImage }).openImage(bytes);
}

function openSheetImages(doc: PDFKit.PDFDocument, model: ScoreSheetsDocument): SheetImages {
  const art = appImages();
  let logo: SheetImage | null = null;
  if (model.logo != null) {
    try {
      logo = openImage(doc, model.logo);
    } catch {
      // A logo in a format pdfkit cannot read shows the initial instead.
      logo = null;
    }
  }
  return { ema: openImage(doc, art.ema), chicken: openImage(doc, art.chicken), winds: art.winds.map((wind) => openImage(doc, wind)), logo };
}

function drawSheet(doc: PDFKit.PDFDocument, model: ScoreSheetsDocument, art: SheetImages, sheet: { round: number; table: number } | null): void {
  const columns = scoreSheetColumns(model.chickenHands);
  const left = MARGIN;
  const seatWidth = columns.score + columns.penalty;
  const handsLeftWidth = columns.number + columns.winner + columns.loser + columns.points + columns.chicken;
  const seatsLeft = left + handsLeftWidth;
  const checkLeft = seatsLeft + seatWidth * 4;
  const right = checkLeft + columns.check;

  // The header: the short name, the round and the table, and the wind, name and id of each seat.
  const windRow = 24;
  const nameRow = 19;
  const top = MARGIN;
  const headerBottom = top + windRow + nameRow * 2;

  // The short name, as big as it fits in the left part of the header.
  // Measured on the columns with the chicken hands, so the header is the same on every sheet.
  const withChicken = scoreSheetColumns(true);
  const titleWidth = withChicken.number + withChicken.winner + withChicken.loser * 0.55;
  const lines = titleLines(model.tournamentName);
  doc.font(fontFor(model.tournamentName, true)).fillColor(BLACK);
  const titleSize = fitFontSize(doc, lines, titleWidth - 4, lines.length > 1 ? 17 : 22, 7);
  centeredLines(doc, lines, left, top, titleWidth, headerBottom - top - 4, titleSize);

  // The round (the gong) and the table, each with a box, written in when the sheet is for a table.
  const boxWidth = 34;
  const boxHeight = 20;
  const iconSize = 17;
  const boxesLeft = left + titleWidth + 4;
  const boxTops = [top + 6, top + 6 + boxHeight + 8];
  const values = sheet == null ? [null, null] : [sheet.round, sheet.table];
  [drawGong, drawTable].forEach((draw, index) => {
    const boxTop = boxTops[index];
    draw(doc, boxesLeft, boxTop + (boxHeight - iconSize) / 2, iconSize);
    const boxLeft = boxesLeft + iconSize + 4;
    doc.save().lineWidth(1.2).strokeColor(BLACK).rect(boxLeft, boxTop, boxWidth, boxHeight).stroke().restore();
    const value = values[index];
    if (value != null) {
      doc.font("Helvetica-Bold").fillColor(BLACK);
      centeredLines(doc, [String(value)], boxLeft, boxTop, boxWidth, boxHeight, 14);
    }
  });

  // The winds over the seats, with the icons of the app, and the name and id of each player under them.
  doc.font("Helvetica-Bold").fillColor(BLACK);
  WINDS.forEach((wind, seat) => {
    const seatLeft = seatsLeft + seatWidth * seat;
    const icon = 15;
    const textWidth = doc.fontSize(10).widthOfString(wind);
    const groupLeft = seatLeft + (seatWidth - icon - 4 - textWidth) / 2;
    doc.image(art.winds[seat], groupLeft, top + (windRow - icon) / 2 - 1, { width: icon, height: icon });
    doc.text(wind, groupLeft + icon + 4, top + windRow / 2 - 5, { lineBreak: false, height: 100000 });
  });
  doc.font("Helvetica-Bold").fillColor(BLACK);
  rightAligned(doc, "Name:", seatsLeft - 3, top + windRow + nameRow / 2, 9);
  rightAligned(doc, "Id:", seatsLeft - 3, top + windRow + nameRow * 1.5, 9);
  doc.save().lineWidth(THIN).strokeColor(BLACK);
  for (let seat = 0; seat < 4; seat += 1) {
    doc.rect(seatsLeft + seatWidth * seat, top + windRow, seatWidth, nameRow * 2);
    doc.moveTo(seatsLeft + seatWidth * seat, top + windRow + nameRow).lineTo(seatsLeft + seatWidth * (seat + 1), top + windRow + nameRow);
  }
  doc.stroke();
  doc.lineWidth(THICK).rect(seatsLeft, top + windRow, seatWidth * 4, nameRow * 2).stroke();
  for (let seat = 1; seat < 4; seat += 1) doc.moveTo(seatsLeft + seatWidth * seat, top + windRow).lineTo(seatsLeft + seatWidth * seat, top + windRow + nameRow * 2);
  doc.stroke().restore();

  // The titles of the columns.
  const titlesTop = headerBottom + 5;
  const titlesHeight = 30;
  const handsTop = titlesTop + titlesHeight;
  const footerHeight = 22 + 30;
  const footerGap = 6;
  const handsHeight = A4_HEIGHT - MARGIN - footerHeight - footerGap - handsTop;
  // The first hand has no running total, so it has one line; every other hand has two: the hand and the total.
  const lineHeight = handsHeight / (HANDS_PER_TABLE * 2 - 1);
  const handTops = Array.from({ length: HANDS_PER_TABLE }, (_, index) => handsTop + (index === 0 ? 0 : lineHeight * (index * 2 - 1)));
  const handsBottom = handsTop + handsHeight;
  const handHeight = (index: number) => (index === 0 ? lineHeight : lineHeight * 2);

  type Column = { left: number; width: number };
  const lefts: Column[] = [];
  let x = left;
  for (const width of [columns.number, columns.winner, columns.loser, columns.points, columns.chicken]) {
    lefts.push({ left: x, width });
    x += width;
  }
  const [numberColumn, winnerColumn, loserColumn, pointsColumn, chickenColumn] = lefts;

  doc.font("Helvetica-Bold").fillColor(BLACK);
  centeredLines(doc, ["#"], numberColumn.left, titlesTop, numberColumn.width, titlesHeight, 9.5);
  centeredLines(doc, ["Winner", "Id"], winnerColumn.left, titlesTop, winnerColumn.width, titlesHeight, 9);
  centeredLines(doc, ["Loser", "Id"], loserColumn.left, titlesTop, loserColumn.width, titlesHeight, 9);
  centeredLines(doc, ["Hand", "Points"], pointsColumn.left, titlesTop, pointsColumn.width, titlesHeight, 9);
  if (model.chickenHands) {
    const icon = Math.min(chickenColumn.width - 6, titlesHeight - 6);
    doc.image(art.chicken, chickenColumn.left + (chickenColumn.width - icon) / 2, titlesTop + (titlesHeight - icon) / 2, { width: icon, height: icon });
  }
  for (let seat = 0; seat < 4; seat += 1) {
    const seatLeft = seatsLeft + seatWidth * seat;
    centeredLines(doc, ["Score"], seatLeft, titlesTop, columns.score, titlesHeight, 9);
    centeredLines(doc, ["Pty."], seatLeft + columns.score, titlesTop, columns.penalty, titlesHeight, 9);
  }
  doc.font("Helvetica-Bold");
  centeredLines(doc, ["Sum 0"], checkLeft, titlesTop, columns.check, titlesHeight, fitFontSize(doc, ["Sum 0"], columns.check - 3, 9, 5));

  // The hands.
  const checkboxSize = Math.min(10, lineHeight * 0.55);
  doc.font("Helvetica-Bold").fillColor(BLACK);
  handTops.forEach((handTop, index) => {
    const height = handHeight(index);
    centeredLines(doc, [String(index + 1)], numberColumn.left, handTop, numberColumn.width, height, 9.5);
    if (model.chickenHands) drawCheckbox(doc, chickenColumn.left + chickenColumn.width / 2, handTop + height / 2, checkboxSize);
    drawCheckbox(doc, checkLeft + columns.check / 2, handTop + height / 2, checkboxSize);
  });

  doc.save().strokeColor(BLACK).lineWidth(THIN);
  // The thin lines: the columns of the hands, and in each seat the line under the hand and between score and penalty.
  for (const column of lefts.slice(1)) {
    if (column.width > 0) doc.moveTo(column.left, titlesTop).lineTo(column.left, handsBottom);
  }
  doc.moveTo(checkLeft, titlesTop).lineTo(checkLeft, handsBottom);
  handTops.forEach((handTop, index) => {
    for (let seat = 0; seat < 4; seat += 1) {
      const scoreRight = seatsLeft + seatWidth * seat + columns.score;
      doc.moveTo(scoreRight, handTop).lineTo(scoreRight, handTop + lineHeight);
    }
    if (index > 0) doc.moveTo(seatsLeft, handTop + lineHeight).lineTo(checkLeft, handTop + lineHeight);
  });
  for (let seat = 0; seat < 4; seat += 1) {
    const scoreRight = seatsLeft + seatWidth * seat + columns.score;
    doc.moveTo(scoreRight, titlesTop).lineTo(scoreRight, handsTop);
  }
  doc.stroke();
  // The lines between the hands; thicker after every fourth hand, when the prevailing wind changes.
  handTops.forEach((handTop, index) => {
    if (index === 0) return;
    doc.lineWidth(index % 4 === 0 ? THICK : 1).moveTo(left, handTop).lineTo(right, handTop).stroke();
  });
  // The thick lines: the frame, the line under the titles and the lines between the seats.
  doc.lineWidth(THICK);
  doc.rect(left, titlesTop, right - left, handsBottom - titlesTop).stroke();
  doc.moveTo(left, handsTop).lineTo(right, handsTop).stroke();
  for (let seat = 0; seat <= 4; seat += 1) doc.moveTo(seatsLeft + seatWidth * seat, titlesTop).lineTo(seatsLeft + seatWidth * seat, handsBottom);
  doc.stroke().restore();

  // The table points and the signatures, under the seats.
  const footerTop = handsBottom + footerGap;
  const rows = [{ label: "Table points:", height: 22 }, { label: "Signatures:", height: 30 }];
  let rowTop = footerTop;
  doc.font("Helvetica-Bold").fillColor(BLACK);
  for (const row of rows) {
    rightAligned(doc, row.label, seatsLeft - 3, rowTop + row.height / 2, 9.5);
    rowTop += row.height;
  }
  doc.save().strokeColor(BLACK).lineWidth(THICK).rect(seatsLeft, footerTop, seatWidth * 4, footerHeight);
  for (let seat = 1; seat < 4; seat += 1) doc.moveTo(seatsLeft + seatWidth * seat, footerTop).lineTo(seatsLeft + seatWidth * seat, footerTop + footerHeight);
  doc.stroke().lineWidth(THIN).moveTo(seatsLeft, footerTop + rows[0].height).lineTo(seatsLeft + seatWidth * 4, footerTop + rows[0].height).stroke().restore();

  // The logos: the organizer always, and the EMA when chosen.
  const logoSize = footerHeight - 4;
  const logoTop = footerTop + 2;
  drawOrganizerLogo(doc, model, art.logo, left + 2, logoTop, logoSize);
  if (model.emaLogo) doc.image(art.ema, left + 2 + logoSize + 8, logoTop, { width: logoSize, height: logoSize });
}

/** An A4 PDF: one blank sheet, or one sheet for each table of each round with its round and table. */
export function buildScoreSheetsPdf(model: ScoreSheetsDocument): Promise<Buffer> {
  return new Promise<Buffer>((resolvePdf, reject) => {
    const doc = new (pdfKit())({
      autoFirstPage: false,
      compress: true,
      info: { Title: `${model.tournamentName} - Score sheets` },
    });
    const chunks: Buffer[] = [];
    doc.on("data", (chunk: Buffer) => chunks.push(chunk));
    doc.on("error", reject);
    doc.on("end", () => resolvePdf(Buffer.concat(chunks)));
    doc.registerFont("SheetApp", appFontPath);
    const images = openSheetImages(doc, model);
    const sheets = model.sheets.length > 0 ? model.sheets : [null];
    for (const sheet of sheets) {
      doc.addPage({ size: [A4_WIDTH, A4_HEIGHT], margin: 0 });
      drawSheet(doc, model, images, sheet);
    }
    doc.end();
  });
}

export async function generateScoreSheets(tournamentId: string, options: ScoreSheetsOptions): Promise<Buffer> {
  return buildScoreSheetsPdf(await loadScoreSheetsDocument(tournamentId, options));
}
