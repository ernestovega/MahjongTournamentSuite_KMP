import { resolve } from "node:path";

import { notFound, conflict } from "../api/httpError";
import { db, storage } from "../firebase";
import { type Language } from "./i18n";
import { pdfKit } from "./lazyModules";
import { mainOrganizerLogoPath, organizersOf, readOrganizerIds } from "./organizationsService";

/**
 * The cards of the tables of a tournament, to print and put on each table: the number of the table, big, so it names
 * the table, and the QR code of the tournament. A player scans it and the player app opens My tables and the table of
 * the player in play or next. See docs/table-qr.md.
 */

/** The last segment of the link, as in the player app (MY_TABLE_PATH_WORD). */
export const MY_TABLE_PATH_WORD = "my-table";

const DEFAULT_ORGANIZER_COLOR = "#02B16B";
const A4_WIDTH = 595.28;
const A4_HEIGHT = 841.89;

/**
 * Cards on each A4 sheet. Every card is landscape, with the number on the left and the code on the right:
 * 1 = 297 x 210 mm, 2 = 210 x 148.5 mm, 4 = 148.5 x 105 mm, 6 = 148.5 x 70 mm, 8 = 105 x 74.25 mm. The sheet is
 * landscape for 1, 4 and 6 cards and portrait for 2 and 8.
 */
export type CardsPerSheet = 1 | 2 | 4 | 6 | 8;

export function readCardsPerSheet(value: unknown): CardsPerSheet {
  const number = Number(value);
  return number === 1 || number === 2 || number === 6 || number === 8 ? number : 4;
}

/** The size of a card and the grid of the sheet. */
export function sheetGrid(perSheet: CardsPerSheet): SheetGrid {
  if (perSheet === 1) return { pageWidth: A4_HEIGHT, pageHeight: A4_WIDTH, columns: 1, rows: 1, width: A4_HEIGHT, height: A4_WIDTH };
  if (perSheet === 2) return { pageWidth: A4_WIDTH, pageHeight: A4_HEIGHT, columns: 1, rows: 2, width: A4_WIDTH, height: A4_HEIGHT / 2 };
  if (perSheet === 6) return { pageWidth: A4_HEIGHT, pageHeight: A4_WIDTH, columns: 2, rows: 3, width: A4_HEIGHT / 2, height: A4_WIDTH / 3 };
  if (perSheet === 8) return { pageWidth: A4_WIDTH, pageHeight: A4_HEIGHT, columns: 2, rows: 4, width: A4_WIDTH / 2, height: A4_HEIGHT / 4 };
  return { pageWidth: A4_HEIGHT, pageHeight: A4_WIDTH, columns: 2, rows: 2, width: A4_HEIGHT / 2, height: A4_WIDTH / 2 };
}

export type SheetGrid = { pageWidth: number; pageHeight: number; columns: number; rows: number; width: number; height: number };

const appFontPath = resolve(__dirname, "../assets/go3v2.ttf");

export type TableQrTexts = {
  /** The word over the number, such as "Table". */
  table: string;
  /** One sentence beside the code. */
  hint: string;
  /** The line over the main site, for who prefers typing it. */
  openApp: string;
};

const TEXTS: Record<Language, TableQrTexts> = {
  en: { table: "Table", hint: "Scan the code to go to your table.", openApp: "Or just open the app:" },
  es: { table: "Mesa", hint: "Escanea el código para ir a tu mesa.", openApp: "O simplemente abre la app:" },
  fr: { table: "Table", hint: "Scannez le code pour aller à votre table.", openApp: "Ou ouvrez simplement l'app :" },
  it: { table: "Tavolo", hint: "Scansiona il codice per andare al tuo tavolo.", openApp: "Oppure apri semplicemente l'app:" },
  de: { table: "Tisch", hint: "Scanne den Code, um zu deinem Tisch zu gehen.", openApp: "Oder öffne einfach die App:" },
  sv: { table: "Bord", hint: "Skanna koden för att gå till ditt bord.", openApp: "Eller öppna bara appen:" },
  nl: { table: "Tafel", hint: "Scan de code om naar je tafel te gaan.", openApp: "Of open gewoon de app:" },
  ru: { table: "Стол", hint: "Отсканируйте код, чтобы перейти к своему столу.", openApp: "Или просто откройте приложение:" },
};

export function tableQrTexts(language: Language): TableQrTexts {
  return TEXTS[language] ?? TEXTS.en;
}

/** The web of the players. Staging has its own address in the environment of the functions. */
function playerAppUrl(): string {
  return (process.env.PLAYER_APP_URL ?? "https://mts.mahjongmadrid.com").replace(/\/$/, "");
}

/**
 * The link of the QR of a tournament: the public link when it has one (`/6thmmc/my-table`), or its id
 * (`/t/<id>/my-table`). The player app opens My tables and the table of the player in play or next (registrationLinkOf).
 */
export function myTableQrLink(site: string, tournamentId: string, slug: string): string {
  const base = site.replace(/\/$/, "");
  const tournamentPath = slug.trim().length > 0 ? encodeURIComponent(slug.trim()) : `t/${encodeURIComponent(tournamentId)}`;
  return `${base}/${tournamentPath}/${MY_TABLE_PATH_WORD}`;
}

export type TableQrDocument = {
  tournamentName: string;
  /** The logo of the main organizer (PNG or JPEG), in a corner of each card, or null. */
  logo: Buffer | null;
  /** Without a logo the corner shows a circle of this color with the first letter of [organizerName], like the app. */
  organizerName: string;
  organizerColor: string;
  /** The same code on every card: it is the code of the tournament. */
  link: string;
  tableCount: number;
  texts: TableQrTexts;
  perSheet: CardsPerSheet;
};

/** The cards are always in English: the tables of a tournament are the same for players of every language. */
export async function loadTableQrDocument(tournamentId: string, perSheet: CardsPerSheet): Promise<TableQrDocument> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const tableCount = Math.floor(Number(tournament.get("numPlayers") ?? 0) / 4);
  if (tableCount < 1) throw conflict("The tournament has no tables yet", { code: "NO_TABLES" });
  const slug = String((tournament.get("registration") as Record<string, unknown> | undefined)?.slug ?? "");
  const organizerIds = readOrganizerIds(tournament.get("organizerIds"));
  const mainOrganizer = (await organizersOf(organizerIds))[0];
  const logoPath = await mainOrganizerLogoPath(organizerIds);
  const logo = logoPath.length > 0 ? await storage.file(logoPath).download().then(([bytes]) => bytes).catch(() => null) : null;
  const tournamentName = String(tournament.get("name") ?? "").trim();
  const color = String(mainOrganizer?.primaryColor ?? DEFAULT_ORGANIZER_COLOR).trim().toUpperCase();
  return {
    tournamentName,
    logo,
    organizerName: mainOrganizer?.name ?? tournamentName,
    organizerColor: /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_ORGANIZER_COLOR,
    link: myTableQrLink(playerAppUrl(), tournamentId, slug),
    tableCount,
    texts: tableQrTexts("en"),
    perSheet,
  };
}

/** The dark modules of the QR code of [text], as rows of booleans. Medium error correction survives some wear. */
export function qrModules(text: string): boolean[][] {
  const qr = (require("qrcode") as typeof import("qrcode")).create(text, { errorCorrectionLevel: "M" });
  const size = qr.modules.size;
  return Array.from({ length: size }, (_, row) =>
    Array.from({ length: size }, (_, column) => qr.modules.get(row, column) === 1));
}

/** The largest font size from [max] down to [min] at which [text] fits in [width]. */
function fitFontSize(doc: PDFKit.PDFDocument, text: string, width: number, max: number, min: number): number {
  for (let size = max; size > min; size -= 0.5) {
    if (doc.fontSize(size).widthOfString(text) <= width) return size;
  }
  return min;
}

/** Helvetica reads better in a sentence. The font of the app has the letters that Helvetica lacks, such as Cyrillic. */
function sentenceFont(text: string): string {
  return /^[\u0000-\u00FF]*$/.test(text) ? "Helvetica" : "CardApp";
}

function drawQr(doc: PDFKit.PDFDocument, modules: boolean[][], x: number, y: number, size: number): void {
  const cell = size / modules.length;
  doc.save().fillColor("#000000");
  modules.forEach((row, rowIndex) => row.forEach((dark, columnIndex) => {
    // A small overlap avoids thin white lines between the squares in some viewers.
    if (dark) doc.rect(x + columnIndex * cell, y + rowIndex * cell, cell + 0.2, cell + 0.2);
  }));
  doc.fill().restore();
}

/**
 * One card: the name of the tournament on the color of the organizer, the number of the table as big as it fits, and
 * the code with one sentence and the link. A wide card puts the number on the left and the code on the right.
 */
/** Writes one line centred in a box, without ever letting pdfkit start a new page. */
function centeredLine(doc: PDFKit.PDFDocument, text: string, x: number, y: number, width: number): void {
  const textWidth = doc.widthOfString(text);
  doc.text(text, x + Math.max(0, (width - textWidth) / 2), y, { lineBreak: false, height: 100000 });
}

function drawCard(
  doc: PDFKit.PDFDocument,
  model: TableQrDocument,
  modules: boolean[][],
  tableId: number,
  left: number,
  top: number,
  width: number,
  height: number,
): void {
  const scale = Math.min(width, height) / (A4_WIDTH / 2);
  const padding = 20 * scale;
  const bandHeight = 40 * scale;
  // The logo of the organizer in the top left corner, and the name of the tournament centred, clear of the logo.
  // A full padding around the logo, so it does not sit on the cut line (Ernesto 24-nov).
  const logoSize = bandHeight * 0.75;
  const logoX = left + padding;
  const logoY = top + padding;
  const drawInitial = () => {
    doc.save().circle(logoX + logoSize / 2, logoY + logoSize / 2, logoSize / 2).fill(model.organizerColor).restore();
    const initial = model.organizerName.trim().slice(0, 1).toUpperCase();
    if (initial.length > 0) {
      doc.font(sentenceFont(initial)).fontSize(logoSize * 0.5).fillColor("#FFFFFF");
      centeredLine(doc, initial, logoX, logoY + logoSize * 0.27, logoSize);
    }
  };
  if (model.logo == null) {
    drawInitial();
  } else {
    try {
      doc.save();
      doc.circle(logoX + logoSize / 2, logoY + logoSize / 2, logoSize / 2).clip();
      doc.image(model.logo, logoX, logoY, { cover: [logoSize, logoSize], align: "center", valign: "center" });
      doc.restore();
    } catch {
      // A logo in a format pdfkit cannot read shows the initial instead.
      doc.restore();
      drawInitial();
    }
  }
  // The name in grey, so the number stands out (Ernesto 24-nov).
  doc.font("CardApp").fillColor("#777777");
  const nameRoom = width - (padding * 1.5 + logoSize) * 2;
  const nameSize = fitFontSize(doc, model.tournamentName, nameRoom, 15 * scale, 7);
  doc.fontSize(nameSize);
  centeredLine(doc, model.tournamentName, left + padding * 1.5 + logoSize, logoY + (logoSize - nameSize) / 2, nameRoom);

  const bodyTop = top + bandHeight + padding * 0.6;
  const bodyHeight = height - bandHeight - padding * 1.6;
  const wide = width > height * 1.2;
  const numberText = String(tableId);
  // Only the host (mts.mahjongmadrid.com): easier to type than the full link, which the code already carries.
  const siteText = model.link.replace(/^https?:\/\//, "").replace(/\/.*$/, "");

  // The number: the word "Table" over it, and the number as big as the room allows.
  const numberBox = wide
    ? { x: left + padding, y: bodyTop, w: (width - padding * 3) * 0.5, h: bodyHeight }
    : { x: left + padding, y: bodyTop, w: width - padding * 2, h: bodyHeight * 0.42 };
  // pdfkit places a line by the top of its box, so the digits are measured by the font's cap height
  // to fill the room exactly: the word on top, a gap, and the digits under it.
  doc.font("CardApp").fillColor("#000000");
  const font = (doc as unknown as { _font: { ascender: number; capHeight?: number } })._font;
  const capRatio = (font.capHeight || 700) / 1000;
  const ascentRatio = font.ascender / 1000;
  const labelSize = Math.max(10, numberBox.h * 0.12);
  const gap = labelSize * 0.5;
  const room = numberBox.h - gap * (wide ? 0 : 1.5);
  const digitsRoom = room - labelSize * capRatio - gap;
  const numberSize = Math.min(fitFontSize(doc, numberText, numberBox.w * 0.9, digitsRoom / capRatio, 20), digitsRoom / capRatio);
  const block = labelSize * capRatio + gap + numberSize * capRatio;
  const blockTop = numberBox.y + Math.max(0, (room - block) / 2);
  doc.fontSize(labelSize);
  // The word a bit lower, closer to its number and further from the name (Ernesto 24-nov).
  const labelDrop = gap * 0.5;
  centeredLine(doc, model.texts.table.toUpperCase(), numberBox.x, blockTop + labelDrop - (ascentRatio - capRatio) * labelSize, numberBox.w);
  doc.fontSize(numberSize);
  const digitsTop = blockTop + labelSize * capRatio + gap;
  centeredLine(doc, numberText, numberBox.x, digitsTop - (ascentRatio - capRatio) * numberSize, numberBox.w);

  // The code, with the sentences and the main site over it.
  const codeBox = wide
    ? { x: left + padding * 2 + numberBox.w, y: bodyTop, w: (width - padding * 3) * 0.5, h: bodyHeight }
    : { x: left + padding, y: bodyTop + numberBox.h, w: width - padding * 2, h: bodyHeight - numberBox.h };
  // The sentence, then "Or just open the app:" and the main site, go over the code.
  const hintSize = Math.max(8, 10.5 * scale);
  const openSize = Math.max(6, 7.5 * scale);
  const siteSize = Math.max(6, 9 * scale);
  const hintHeight = doc.font(sentenceFont(model.texts.hint)).fontSize(hintSize).heightOfString(model.texts.hint, { width: codeBox.w });
  const textHeight = hintHeight + openSize * 0.6 + openSize * 1.2 + siteSize * 1.3 + hintSize * 0.6;
  const qrSize = Math.max(60, Math.min(codeBox.w, codeBox.h - textHeight));
  const codeTop = codeBox.y + Math.max(0, (codeBox.h - qrSize - textHeight) / 2);
  doc.fillColor("#000000").text(model.texts.hint, codeBox.x, codeTop, { width: codeBox.w, align: "center", height: 100000 });
  const openTop = codeTop + hintHeight + openSize * 0.6;
  doc.font(sentenceFont(model.texts.openApp)).fontSize(fitFontSize(doc.font(sentenceFont(model.texts.openApp)), model.texts.openApp, codeBox.w, openSize, 5)).fillColor("#555555");
  centeredLine(doc, model.texts.openApp, codeBox.x, openTop, codeBox.w);
  doc.font("Helvetica-Bold").fontSize(fitFontSize(doc.font("Helvetica-Bold"), siteText, codeBox.w, siteSize, 5)).fillColor("#000000");
  centeredLine(doc, siteText, codeBox.x, openTop + openSize * 1.2, codeBox.w);
  drawQr(doc, modules, codeBox.x + (codeBox.w - qrSize) / 2, codeTop + textHeight, qrSize);
}

/** Dashed lines between the cards, to cut them. */
function drawCutLines(doc: PDFKit.PDFDocument, grid: ReturnType<typeof sheetGrid>): void {
  doc.save().lineWidth(0.5).strokeColor("#999999").dash(4, { space: 4 });
  for (let column = 1; column < grid.columns; column += 1) doc.moveTo(grid.width * column, 0).lineTo(grid.width * column, grid.pageHeight).stroke();
  for (let row = 1; row < grid.rows; row += 1) doc.moveTo(0, grid.height * row).lineTo(grid.pageWidth, grid.height * row).stroke();
  doc.undash().restore();
}

/** An A4 PDF with a card for each table, [TableQrDocument.perSheet] on each sheet. */
export function buildTableQrPdf(model: TableQrDocument): Promise<Buffer> {
  return new Promise<Buffer>((resolvePdf, reject) => {
    const doc = new (pdfKit())({
      autoFirstPage: false,
      compress: true,
      info: { Title: `${model.tournamentName} - ${model.texts.table}` },
    });
    const chunks: Buffer[] = [];
    doc.on("data", (chunk: Buffer) => chunks.push(chunk));
    doc.on("error", reject);
    doc.on("end", () => resolvePdf(Buffer.concat(chunks)));
    doc.registerFont("CardApp", appFontPath);
    const grid = sheetGrid(model.perSheet);
    const modules = qrModules(model.link);
    for (let first = 1; first <= model.tableCount; first += model.perSheet) {
      doc.addPage({ size: [grid.pageWidth, grid.pageHeight], margin: 0 });
      for (let index = 0; index < model.perSheet && first + index <= model.tableCount; index += 1) {
        const column = index % grid.columns;
        const row = Math.floor(index / grid.columns);
        drawCard(doc, model, modules, first + index, column * grid.width, row * grid.height, grid.width, grid.height);
      }
      if (model.perSheet > 1) drawCutLines(doc, grid);
    }
    doc.end();
  });
}

export async function generateTableQrCodes(tournamentId: string, perSheet: CardsPerSheet): Promise<Buffer> {
  return buildTableQrPdf(await loadTableQrDocument(tournamentId, perSheet));
}
