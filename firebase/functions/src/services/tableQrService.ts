import { resolve } from "node:path";

import { notFound, conflict } from "../api/httpError";
import { db } from "../firebase";
import { type Language } from "./i18n";
import { pdfKit } from "./lazyModules";
import { organizersOf, readOrganizerIds } from "./organizationsService";

/**
 * The cards of the tables of a tournament, to print and put on each table: the number of the table, big, so it names
 * the table, and the QR code of the tournament. A player scans it and the player app opens My tables and the table of
 * the player in play or next. See docs/table-qr.md.
 */

/** The last segment of the link, as in the player app (MY_TABLE_PATH_WORD). */
export const MY_TABLE_PATH_WORD = "my-table";

const DEFAULT_PRIMARY_COLOR = "#02B16B";
const A4_WIDTH = 595.28;
const A4_HEIGHT = 841.89;

/** Cards on each A4 sheet: one (the whole sheet), two (A5 halves, landscape) or four (A6 quarters). */
export type CardsPerSheet = 1 | 2 | 4;

export function readCardsPerSheet(value: unknown): CardsPerSheet {
  const number = Number(value);
  return number === 1 || number === 2 ? number : 4;
}

/** The size of a card and the grid of the sheet. */
export function sheetGrid(perSheet: CardsPerSheet): { columns: number; rows: number; width: number; height: number } {
  if (perSheet === 1) return { columns: 1, rows: 1, width: A4_WIDTH, height: A4_HEIGHT };
  if (perSheet === 2) return { columns: 1, rows: 2, width: A4_WIDTH, height: A4_HEIGHT / 2 };
  return { columns: 2, rows: 2, width: A4_WIDTH / 2, height: A4_HEIGHT / 2 };
}

const appFontPath = resolve(__dirname, "../assets/go3v2.ttf");

export type TableQrTexts = {
  /** The word over the number, such as "Table". */
  table: string;
  /** One sentence beside the code. */
  hint: string;
};

const TEXTS: Record<Language, TableQrTexts> = {
  en: { table: "Table", hint: "Scan the code to go to your table." },
  es: { table: "Mesa", hint: "Escanea el código para ir a tu mesa." },
  fr: { table: "Table", hint: "Scannez le code pour aller à votre table." },
  it: { table: "Tavolo", hint: "Scansiona il codice per andare al tuo tavolo." },
  de: { table: "Tisch", hint: "Scanne den Code, um zu deinem Tisch zu gehen." },
  sv: { table: "Bord", hint: "Skanna koden för att gå till ditt bord." },
  nl: { table: "Tafel", hint: "Scan de code om naar je tafel te gaan." },
  ru: { table: "Стол", hint: "Отсканируйте код, чтобы перейти к своему столу." },
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
  primaryColor: string;
  /** The same code on every card: it is the code of the tournament. */
  link: string;
  tableCount: number;
  texts: TableQrTexts;
  perSheet: CardsPerSheet;
};

export async function loadTableQrDocument(tournamentId: string, language: Language, perSheet: CardsPerSheet): Promise<TableQrDocument> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const tableCount = Math.floor(Number(tournament.get("numPlayers") ?? 0) / 4);
  if (tableCount < 1) throw conflict("The tournament has no tables yet");
  const slug = String((tournament.get("registration") as Record<string, unknown> | undefined)?.slug ?? "");
  const mainOrganizer = (await organizersOf(readOrganizerIds(tournament.get("organizerIds"))))[0];
  const color = String(mainOrganizer?.primaryColor ?? DEFAULT_PRIMARY_COLOR).trim().toUpperCase();
  return {
    tournamentName: String(tournament.get("name") ?? "").trim(),
    primaryColor: /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR,
    link: myTableQrLink(playerAppUrl(), tournamentId, slug),
    tableCount,
    texts: tableQrTexts(language),
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
  doc.save().rect(left, top, width, bandHeight).fill(model.primaryColor).restore();
  doc.font("CardApp").fillColor("#FFFFFF");
  const nameSize = fitFontSize(doc, model.tournamentName, width - padding * 2, 15 * scale, 7);
  doc.fontSize(nameSize);
  centeredLine(doc, model.tournamentName, left + padding, top + (bandHeight - nameSize) / 2 - 1, width - padding * 2);

  const bodyTop = top + bandHeight + padding * 0.6;
  const bodyHeight = height - bandHeight - padding * 1.6;
  const wide = width > height * 1.2;
  const numberText = String(tableId);
  const linkText = model.link.replace(/^https?:\/\//, "");

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
  centeredLine(doc, model.texts.table.toUpperCase(), numberBox.x, blockTop - (ascentRatio - capRatio) * labelSize, numberBox.w);
  doc.fontSize(numberSize);
  const digitsTop = blockTop + labelSize * capRatio + gap;
  centeredLine(doc, numberText, numberBox.x, digitsTop - (ascentRatio - capRatio) * numberSize, numberBox.w);

  // The code, with the sentence and the link under it.
  const codeBox = wide
    ? { x: left + padding * 2 + numberBox.w, y: bodyTop, w: (width - padding * 3) * 0.5, h: bodyHeight }
    : { x: left + padding, y: bodyTop + numberBox.h, w: width - padding * 2, h: bodyHeight - numberBox.h };
  const hintSize = Math.max(8, 10.5 * scale);
  const linkSize = Math.max(6, 8 * scale);
  const textHeight = hintSize * 2.6 + linkSize * 1.6;
  const qrSize = Math.max(60, Math.min(codeBox.w, codeBox.h - textHeight));
  const qrTop = codeBox.y + Math.max(0, (codeBox.h - qrSize - textHeight) / 2);
  drawQr(doc, modules, codeBox.x + (codeBox.w - qrSize) / 2, qrTop, qrSize);
  doc.font(sentenceFont(model.texts.hint)).fontSize(hintSize).fillColor("#000000")
    .text(model.texts.hint, codeBox.x, qrTop + qrSize + hintSize * 0.6, { width: codeBox.w, align: "center", height: 100000 });
  doc.font("Helvetica").fontSize(fitFontSize(doc.font("Helvetica"), linkText, codeBox.w, linkSize, 5)).fillColor("#555555");
  centeredLine(doc, linkText, codeBox.x, qrTop + qrSize + hintSize * 3, codeBox.w);
}

/** Dashed lines between the cards, to cut them. */
function drawCutLines(doc: PDFKit.PDFDocument, grid: ReturnType<typeof sheetGrid>): void {
  doc.save().lineWidth(0.5).strokeColor("#999999").dash(4, { space: 4 });
  for (let column = 1; column < grid.columns; column += 1) doc.moveTo(grid.width * column, 0).lineTo(grid.width * column, A4_HEIGHT).stroke();
  for (let row = 1; row < grid.rows; row += 1) doc.moveTo(0, grid.height * row).lineTo(A4_WIDTH, grid.height * row).stroke();
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
      doc.addPage({ size: [A4_WIDTH, A4_HEIGHT], margin: 0 });
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

export async function generateTableQrCodes(tournamentId: string, language: Language, perSheet: CardsPerSheet): Promise<Buffer> {
  return buildTableQrPdf(await loadTableQrDocument(tournamentId, language, perSheet));
}
