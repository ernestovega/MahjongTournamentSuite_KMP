import { resolve } from "node:path";

import { notFound, conflict } from "../api/httpError";
import { db } from "../firebase";
import { type Language } from "./i18n";
import { pdfKit } from "./lazyModules";
import { organizersOf, readOrganizerIds } from "./organizationsService";

/**
 * The QR codes of the physical tables of a tournament, to print and put on each table. A player scans the code of
 * the table and the player app opens that table in the round in play. See docs/table-qr.md.
 */

/** The word before the number of a table in the link, as in the player app (TABLE_PATH_WORD). */
export const TABLE_PATH_WORD = "table";

const DEFAULT_PRIMARY_COLOR = "#02B16B";
const A4_WIDTH = 595.28;
const A4_HEIGHT = 841.89;
/** Four cards on each A4 page, two columns and two rows: each card is an A6. */
const COLUMNS = 2;
const ROWS = 2;
const CARD_WIDTH = A4_WIDTH / COLUMNS;
const CARD_HEIGHT = A4_HEIGHT / ROWS;

const appFontPath = resolve(__dirname, "../assets/go3v2.ttf");

export type TableQrTexts = {
  /** The word over the number, such as "Table". */
  table: string;
  /** One sentence under the code. */
  hint: string;
};

const TEXTS: Record<Language, TableQrTexts> = {
  en: { table: "Table", hint: "Scan the code to see the hands and the results of this table." },
  es: { table: "Mesa", hint: "Escanea el código para ver las manos y los resultados de esta mesa." },
  fr: { table: "Table", hint: "Scannez le code pour voir les mains et les résultats de cette table." },
  it: { table: "Tavolo", hint: "Scansiona il codice per vedere le mani e i risultati di questo tavolo." },
  de: { table: "Tisch", hint: "Scanne den Code, um die Hände und die Ergebnisse dieses Tisches zu sehen." },
  sv: { table: "Bord", hint: "Skanna koden för att se händerna och resultaten vid det här bordet." },
  nl: { table: "Tafel", hint: "Scan de code om de handen en de uitslagen van deze tafel te zien." },
  ru: { table: "Стол", hint: "Отсканируйте код, чтобы увидеть руки и результаты этого стола." },
};

export function tableQrTexts(language: Language): TableQrTexts {
  return TEXTS[language] ?? TEXTS.en;
}

/** The web of the players. Staging has its own address in the environment of the functions. */
function playerAppUrl(): string {
  return (process.env.PLAYER_APP_URL ?? "https://mts.mahjongmadrid.com").replace(/\/$/, "");
}

/**
 * The link of the QR of a table: the public link of the tournament when it has one (`/6thmmc/table/5`), or its id
 * (`/t/<id>/table/5`). The player app reads both (registrationLinkOf).
 */
export function tableQrLink(site: string, tournamentId: string, slug: string, tableId: number): string {
  const base = site.replace(/\/$/, "");
  const tournamentPath = slug.trim().length > 0 ? encodeURIComponent(slug.trim()) : `t/${encodeURIComponent(tournamentId)}`;
  return `${base}/${tournamentPath}/${TABLE_PATH_WORD}/${tableId}`;
}

export type TableQrDocument = {
  tournamentName: string;
  primaryColor: string;
  tables: { tableId: number; link: string }[];
  texts: TableQrTexts;
};

export async function loadTableQrDocument(tournamentId: string, language: Language): Promise<TableQrDocument> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const numPlayers = Number(tournament.get("numPlayers") ?? 0);
  const tableCount = Math.floor(numPlayers / 4);
  if (tableCount < 1) throw conflict("The tournament has no tables yet");
  const slug = String((tournament.get("registration") as Record<string, unknown> | undefined)?.slug ?? "");
  const mainOrganizer = (await organizersOf(readOrganizerIds(tournament.get("organizerIds"))))[0];
  const color = String(mainOrganizer?.primaryColor ?? DEFAULT_PRIMARY_COLOR).trim().toUpperCase();
  const site = playerAppUrl();
  return {
    tournamentName: String(tournament.get("name") ?? "").trim(),
    primaryColor: /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR,
    tables: Array.from({ length: tableCount }, (_, index) => ({
      tableId: index + 1,
      link: tableQrLink(site, tournamentId, slug, index + 1),
    })),
    texts: tableQrTexts(language),
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

function drawCard(doc: PDFKit.PDFDocument, model: TableQrDocument, table: { tableId: number; link: string }, left: number, top: number): void {
  const padding = 22;
  const innerWidth = CARD_WIDTH - padding * 2;
  // A band with the color of the organizer and the name of the tournament in white.
  const bandHeight = 46;
  doc.save().rect(left, top, CARD_WIDTH, bandHeight).fill(model.primaryColor).restore();
  doc.font("CardApp").fillColor("#FFFFFF");
  const nameSize = fitFontSize(doc, model.tournamentName, innerWidth, 16, 8);
  doc.fontSize(nameSize).text(model.tournamentName, left + padding, top + (bandHeight - nameSize) / 2 - 1, {
    width: innerWidth, align: "center", lineBreak: false,
  });

  doc.fillColor("#000000");
  doc.fontSize(18).text(model.texts.table, left + padding, top + bandHeight + 14, { width: innerWidth, align: "center", lineBreak: false });
  doc.fontSize(64).text(String(table.tableId), left + padding, top + bandHeight + 34, { width: innerWidth, align: "center", lineBreak: false });

  const qrSize = 180;
  const qrTop = top + bandHeight + 112;
  drawQr(doc, qrModules(table.link), left + (CARD_WIDTH - qrSize) / 2, qrTop, qrSize);

  doc.font(sentenceFont(model.texts.hint)).fontSize(11).fillColor("#000000").text(model.texts.hint, left + padding, qrTop + qrSize + 12, { width: innerWidth, align: "center" });
  const linkText = table.link.replace(/^https?:\/\//, "");
  doc.font("Helvetica").fontSize(fitFontSize(doc.font("Helvetica"), linkText, innerWidth, 9, 5)).fillColor("#555555")
    .text(linkText, left + padding, top + CARD_HEIGHT - padding - 10, { width: innerWidth, align: "center", lineBreak: false });
}

/** Dashed lines between the cards, to cut them. */
function drawCutLines(doc: PDFKit.PDFDocument): void {
  doc.save().lineWidth(0.5).strokeColor("#999999").dash(4, { space: 4 });
  for (let column = 1; column < COLUMNS; column += 1) doc.moveTo(CARD_WIDTH * column, 0).lineTo(CARD_WIDTH * column, A4_HEIGHT).stroke();
  for (let row = 1; row < ROWS; row += 1) doc.moveTo(0, CARD_HEIGHT * row).lineTo(A4_WIDTH, CARD_HEIGHT * row).stroke();
  doc.undash().restore();
}

/** An A4 PDF with four cards on each page: the name of the tournament, the number of the table and its QR code. */
export function buildTableQrPdf(model: TableQrDocument): Promise<Buffer> {
  return new Promise<Buffer>((resolvePdf, reject) => {
    const doc = new (pdfKit())({
      autoFirstPage: false,
      compress: true,
      info: { Title: `${model.tournamentName} - ${model.texts.table} QR` },
    });
    const chunks: Buffer[] = [];
    doc.on("data", (chunk: Buffer) => chunks.push(chunk));
    doc.on("error", reject);
    doc.on("end", () => resolvePdf(Buffer.concat(chunks)));
    doc.registerFont("CardApp", appFontPath);
    const perPage = COLUMNS * ROWS;
    for (let start = 0; start < model.tables.length; start += perPage) {
      doc.addPage({ size: [A4_WIDTH, A4_HEIGHT], margin: 0 });
      model.tables.slice(start, start + perPage).forEach((table, index) => {
        const column = index % COLUMNS;
        const row = Math.floor(index / COLUMNS);
        drawCard(doc, model, table, column * CARD_WIDTH, row * CARD_HEIGHT);
      });
      drawCutLines(doc);
    }
    doc.end();
  });
}

export async function generateTableQrCodes(tournamentId: string, language: Language): Promise<Buffer> {
  return buildTableQrPdf(await loadTableQrDocument(tournamentId, language));
}
