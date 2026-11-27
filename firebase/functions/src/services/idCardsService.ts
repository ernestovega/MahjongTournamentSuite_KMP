/// <reference types="pdfkit" />
import { readFileSync } from "node:fs";
import { mainOrganizerLogoPath, organizersOf, readOrganizerIds } from "./organizationsService";
import { dirname, join, resolve } from "node:path";

import { conflict, notFound } from "../api/httpError";
import { db, storage } from "../firebase";
import { listCountries } from "./countriesService";
import { threeLetterCountryCodeToIso2 } from "./countryCodes";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { readStoredRoundSchedules, type TournamentRoundSchedule } from "./tournamentSchedule";
import { pdfKit } from "./lazyModules";

export const CARD_WIDTH = 242.88;
export const CARD_HEIGHT = 153;
const DEFAULT_PRIMARY_COLOR = "#02B16B";
const BACKGROUND_COLOR = "#FFFDF5";
const POINTS_PER_MILLIMETER = 72 / 25.4;
const LOGO_CENTER_X = 68.66;
const LOGO_CENTER_Y = 71.39;
const LOGO_OUTER_RADIUS = 46.9;
const LOGO_WHITE_RADIUS = 34.5;
const PLAYER_DETAILS_CENTER_X = 176;
export const MISSING_FLAG_SVG = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 36">
  <rect width="48" height="36" rx="2" fill="#808080"/>
  <path fill="#FFFFFF" d="M17 11.5C17.6 7.8 20.4 5.8 24.4 5.8c4.5 0 7.6 2.5 7.6 6.2 0 2.9-1.6 4.6-4.2 6.2-2 1.3-2.7 2.2-2.7 4.2v.8h-4.6v-1.1c0-3.2 1.4-5 4.2-6.8 1.6-1 2.2-1.9 2.2-3 0-1.5-1.1-2.5-2.9-2.5-1.9 0-3.1 1.1-3.5 3.2L17 11.5zM20.5 26h4.9v4.9h-4.9z"/>
</svg>`;

/** The flag of a player without country (a guest): the globe 🌐 of Twemoji, as in the apps. */
export const GUEST_FLAG_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36"><path fill="#3B88C3" d="M18 0C8.059 0 0 8.059 0 18s8.059 18 18 18 18-8.059 18-18S27.941 0 18 0zM2.05 19h3.983c.092 2.506.522 4.871 1.229 7H4.158c-1.207-2.083-1.95-4.459-2.108-7zM19 8V2.081c2.747.436 5.162 2.655 6.799 5.919H19zm7.651 2c.754 2.083 1.219 4.46 1.317 7H19v-7h7.651zM17 2.081V8h-6.799C11.837 4.736 14.253 2.517 17 2.081zM17 10v7H8.032c.098-2.54.563-4.917 1.317-7H17zM6.034 17H2.05c.158-2.54.901-4.917 2.107-7h3.104c-.705 2.129-1.135 4.495-1.227 7zm1.998 2H17v7H9.349c-.754-2.083-1.219-4.459-1.317-7zM17 28v5.919c-2.747-.437-5.163-2.655-6.799-5.919H17zm2 5.919V28h6.8c-1.637 3.264-4.053 5.482-6.8 5.919zM19 26v-7h8.969c-.099 2.541-.563 4.917-1.317 7H19zm10.967-7h3.982c-.157 2.541-.9 4.917-2.107 7h-3.104c.706-2.129 1.136-4.494 1.229-7zm0-2c-.093-2.505-.523-4.871-1.229-7h3.104c1.207 2.083 1.95 4.46 2.107 7h-3.982zm.512-9h-2.503c-.717-1.604-1.606-3.015-2.619-4.199C27.346 4.833 29.089 6.267 30.479 8zM10.643 3.801C9.629 4.985 8.74 6.396 8.023 8H5.521c1.39-1.733 3.133-3.166 5.122-4.199zM5.521 28h2.503c.716 1.604 1.605 3.015 2.619 4.198C8.654 31.166 6.911 29.733 5.521 28zm19.836 4.198c1.014-1.184 1.902-2.594 2.619-4.198h2.503c-1.39 1.733-3.133 3.166-5.122 4.198z"/></svg>`;

/** The flag of a country, the globe for a player without country, or the question mark for an unknown code. */
export function flagOrPlaceholderSvg(countryCode: string | undefined): string {
  if ((countryCode ?? "").trim().length === 0) return GUEST_FLAG_SVG;
  return flagSvgFor(countryCode) ?? MISSING_FLAG_SVG;
}

const appFontPath = resolve(__dirname, "../assets/go3v2.ttf");
const flagDirectory = dirname(require.resolve("flag-icons/flags/4x3/es.svg"));
const flagSvgCache = new Map<string, string | null>();
const svgToPdf = require("svg-to-pdfkit") as (
  doc: PDFKit.PDFDocument,
  svg: string,
  x: number,
  y: number,
  options: { width: number; height: number; preserveAspectRatio: string },
) => void;

/**
 * A player card, a blank card for a substitute (no name and no number, both written by hand) or a card for a member
 * of the staff (STAFF instead of the number, with or without name).
 */
export type IdCardKind = "player" | "substitute" | "staff";

export type IdCardPlayer = {
  kind?: IdCardKind;
  playerId: number;
  name: string;
  country: string;
  countryCode?: string;
  teamName: string | null;
  tableNumbers: number[];
};

export type IdCardsDocument = {
  tournamentShortName: string;
  year: string;
  primaryColor: string;
  associationLogo?: Buffer | null;
  numberOfRounds?: number;
  roundSchedules?: TournamentRoundSchedule[];
  players: IdCardPlayer[];
};

export type IdCardProofRequest = {
  shortName: string;
  year: string;
  primaryColor: string;
  associationLogoContentType?: string | null;
  associationLogoDataBase64?: string | null;
  associationLogoUrl?: string | null;
  roundSchedules?: unknown;
};

export type IdCardBackRow = {
  roundId: number;
  date: string | null;
  startTime: string | null;
  tableNumber: number;
  startsNewDay: boolean;
};

function normalizeHexColor(value: string): string {
  const color = value.trim().toUpperCase();
  return /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR;
}

function titleCase(value: string): string {
  return value.toLocaleLowerCase().replace(/(^|[\s'-])\p{L}/gu, (match) => match.toLocaleUpperCase());
}

export function splitPlayerName(value: string): { givenName: string; surname: string } {
  const words = value.trim().split(/\s+/).filter(Boolean);
  if (words.length <= 1) return { givenName: titleCase(words[0] ?? "Player"), surname: "" };
  return {
    givenName: titleCase(words[0]),
    surname: words.slice(1).join(" ").toLocaleUpperCase(),
  };
}

export function iso2CountryCode(value: string): string | null {
  const code = value.trim().toUpperCase();
  if (code === "EU" || code.length === 0) return null;
  if (/^[A-Z]{2}$/.test(code)) return code;
  return threeLetterCountryCodeToIso2[code] ?? null;
}

export function flagSvgFor(countryCode: string | undefined): string | null {
  const code = iso2CountryCode(countryCode ?? "");
  if (code == null) return null;
  const cached = flagSvgCache.get(code);
  if (cached !== undefined) return cached;

  try {
    const svg = readFileSync(join(flagDirectory, `${code.toLowerCase()}.svg`), "utf8");
    flagSvgCache.set(code, svg);
    return svg;
  } catch (_error) {
    flagSvgCache.set(code, null);
    return null;
  }
}

function fitText(
  doc: PDFKit.PDFDocument,
  text: string,
  maxWidth: number,
  initialSize: number,
  minimumSize: number,
): number {
  let size = initialSize;
  while (size > minimumSize) {
    doc.fontSize(size);
    if (doc.widthOfString(text) <= maxWidth) break;
    size -= 0.5;
  }
  return size;
}

function drawCurvedText(
  doc: PDFKit.PDFDocument,
  text: string,
  centerX: number,
  centerY: number,
  radius: number,
  centerAngle: number,
  spread: number,
  fontSize: number,
  color: string,
  bottomArc: boolean = false,
): void {
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

/**
 * The logo, read once for every card. A logo file in a format that PDFKit does not read, such as an old WebP logo,
 * gives cards without a logo instead of no cards.
 */
function openLogo(doc: PDFKit.PDFDocument, logo: Buffer | null | undefined): PDFKit.Mixins.ImageSrc | null {
  if (logo == null) return null;
  try {
    return (doc as unknown as { openImage(src: Buffer): PDFKit.Mixins.ImageSrc }).openImage(logo);
  } catch (_error) {
    return null;
  }
}

function drawCenteredText(
  doc: PDFKit.PDFDocument,
  text: string,
  width: number,
  height: number,
  fontSize: number,
): void {
  type GlyphBounds = { minX: number; maxX: number };
  type FontLayout = {
    glyphs: Array<{ bbox: GlyphBounds }>;
    positions: Array<{ xAdvance: number; xOffset: number }>;
  };
  type InternalFont = {
    unitsPerEm: number;
    layout: (value: string) => FontLayout;
  };

  const internalFont = (doc as unknown as { _font?: { font?: InternalFont } })._font?.font;
  const layout = internalFont?.layout(text);
  let textX: number | null = null;
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
  } else {
    doc.text(text, textX, textY, { lineBreak: false });
  }
}

function drawCountryFlag(doc: PDFKit.PDFDocument, countryCode: string | undefined): void {
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
  svgToPdf(doc, flagOrPlaceholderSvg(countryCode), flagX, flagY, {
    width: flagWidth,
    height: flagHeight,
    preserveAspectRatio: "xMidYMid meet",
  });
  doc.restore();
}

function drawFront(
  doc: PDFKit.PDFDocument,
  model: IdCardsDocument,
  player: IdCardPlayer,
  associationLogo: PDFKit.Mixins.ImageSrc | null,
): void {
  const primary = normalizeHexColor(model.primaryColor);
  const kind = player.kind ?? "player";
  const hasName = player.name.trim().length > 0;
  const { givenName, surname } = hasName ? splitPlayerName(player.name) : { givenName: "", surname: "" };

  doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).fill(BACKGROUND_COLOR);
  doc.rect(0, 0, CARD_WIDTH, 76).fill(primary);
  doc.circle(LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_OUTER_RADIUS).fill(primary);

  if (associationLogo != null) {
    const logoRadius = 32.5;
    doc.circle(LOGO_CENTER_X, LOGO_CENTER_Y, LOGO_WHITE_RADIUS).fill("#FFFFFF");
    doc.save();
    doc.circle(LOGO_CENTER_X, LOGO_CENTER_Y, logoRadius).clip();
    doc.image(associationLogo, LOGO_CENTER_X - logoRadius, LOGO_CENTER_Y - logoRadius, {
      cover: [logoRadius * 2, logoRadius * 2],
      align: "center",
      valign: "center",
    });
    doc.restore();
  }

  const tournamentNameFontSize = model.tournamentShortName.length > 8 ? 22 : 27;
  drawCurvedText(
    doc,
    model.tournamentShortName,
    LOGO_CENTER_X,
    LOGO_CENTER_Y,
    LOGO_WHITE_RADIUS + tournamentNameFontSize * 0.45 - 0.625 * POINTS_PER_MILLIMETER,
    -90,
    Math.min(138, 56 + model.tournamentShortName.length * 8.2),
    tournamentNameFontSize,
    "#FFFFFF",
  );
  const yearFontSize = 29;
  drawCurvedText(
    doc,
    model.year,
    LOGO_CENTER_X,
    LOGO_CENTER_Y,
    LOGO_OUTER_RADIUS + yearFontSize * 0.2,
    90,
    50,
    yearFontSize,
    primary,
    true,
  );

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

  if (kind === "substitute") {
    // The number of the substitute is the one of the player who leaves, so it is written by hand on this line.
    drawWritingLine(doc, PLAYER_DETAILS_CENTER_X - 30, 66, 60, "#FFFFFF");
  } else {
    const numberText = kind === "staff" ? "STAFF" : String(player.playerId);
    // STAFF is wider than a number, so it is smaller and keeps the same vertical center inside the band.
    const numberSize = kind === "staff" ? fitText(doc.font("CardApp"), numberText, 90, 24, 16) : 33.75;
    doc.font("CardApp").fontSize(numberSize).text(
      numberText,
      PLAYER_DETAILS_CENTER_X - 47,
      49 + 0.5 * POINTS_PER_MILLIMETER - (33.75 - 27) / 2 + (33.75 - numberSize) / 2,
      {
        width: 94,
        align: "center",
        lineBreak: false,
      },
    );
  }

  if (hasName) {
    doc.font("CardApp").fillColor("#000000");
    const givenSize = fitText(doc, givenName, 118, 29, 15);
    doc.fontSize(givenSize).text(givenName, 116, 86, { width: 120, align: "center", lineBreak: false });

    doc.font("CardApp");
    const surnameSize = fitText(doc, surname, 116, 9, 6);
    doc.fontSize(surnameSize).text(surname, 118, 126, { width: 116, align: "center", lineBreak: false });
  } else {
    // A card without name has two lines to write the name and the surname by hand.
    drawWritingLine(doc, 124, 114, 104, "#9E9E9E");
    drawWritingLine(doc, 124, 136, 104, "#9E9E9E");
  }

  if (kind === "player") drawCountryFlag(doc, player.countryCode);
}

function drawWritingLine(doc: PDFKit.PDFDocument, x: number, y: number, width: number, color: string): void {
  doc.save().lineWidth(0.6).strokeColor(color).moveTo(x, y).lineTo(x + width, y).stroke().restore();
}

function drawCellText(
  doc: PDFKit.PDFDocument,
  text: string,
  x: number,
  y: number,
  width: number,
  height: number,
): void {
  const textHeight = doc.heightOfString(text, { width: width - 4, align: "center" });
  doc.text(text, x + 2, y + Math.max(1, (height - textHeight) / 2), {
    width: width - 4,
    align: "center",
    lineBreak: false,
  });
}

function displayCardDate(value: string): string {
  return `${value.slice(8, 10)}/${value.slice(5, 7)}`;
}

function displayStart(row: IdCardBackRow): string {
  if (row.startTime == null) return "-";
  return row.date == null ? row.startTime : `${displayCardDate(row.date)} - ${row.startTime}`;
}

export function buildIdCardBackRows(
  roundCount: number,
  roundSchedules: TournamentRoundSchedule[],
  tableNumbers: number[],
): IdCardBackRow[] {
  const scheduleByRound = new Map(roundSchedules.map((schedule) => [schedule.roundId, schedule]));
  const rows: IdCardBackRow[] = [];
  let currentDate: string | null = null;
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
    if (date != null) currentDate = date;
  }
  return rows;
}

function drawBack(doc: PDFKit.PDFDocument, model: IdCardsDocument, player: IdCardPlayer): void {
  const primary = normalizeHexColor(model.primaryColor);
  doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).fill(BACKGROUND_COLOR);

  const kind = player.kind ?? "player";
  if (kind !== "substitute") {
    doc.save();
    const backgroundNumber = kind === "staff" ? "STAFF" : String(player.playerId);
    const backgroundNumberSize = fitText(doc.font("CardApp"), backgroundNumber, 190, 132, kind === "staff" ? 40 : 88);
    doc.opacity(0.28).fontSize(backgroundNumberSize).fillColor(primary);
    drawCenteredText(doc, backgroundNumber, CARD_WIDTH, CARD_HEIGHT, backgroundNumberSize);
    doc.restore();
  }
  // The staff does not play, so its back has no table of rounds.
  if (kind === "staff") return;

  const x = 14;
  const y = 10;
  const width = CARD_WIDTH - 28;
  const headerHeight = 22;
  const roundCount = Math.max(1, model.numberOfRounds ?? player.tableNumbers.length);
  const tableNumbers = Array.from({ length: roundCount }, (_, index) => player.tableNumbers[index] ?? 0);
  const rows = buildIdCardBackRows(roundCount, model.roundSchedules ?? [], tableNumbers);
  const availableRowsHeight = CARD_HEIGHT - y - 10 - headerHeight;
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
    drawCellText(
      doc,
      row.tableNumber > 0 ? String(row.tableNumber) : kind === "substitute" ? "" : "-",
      x + columns[0] + columns[1],
      rowY,
      columns[2],
      rowHeight,
    );
  });
}

const A4_WIDTH = 595.28;
const A4_HEIGHT = 841.89;
export const SHEET_MARGIN = 14;
const CUT_MARK_LENGTH = 8;
const CUT_MARK_OFFSET = 3;

export type IdCardSheetOptions = {
  /** Number of cards on one sheet side. The grid never holds more than fits at the chosen scale. */
  cardsPerSheet: number;
  /** Card size in percent of the real card size. It can have decimals. */
  scalePercent: number;
  cutMarks: boolean;
  /** Puts the A4 sheet in landscape orientation, so larger cards fit. */
  landscape: boolean;
  /** Mirrors the back sheet so each back is behind its front when printing on both sides. */
  doubleSided: boolean;
  /** Adds a back sheet after each front sheet. Without it the PDF only has the fronts. */
  backs?: boolean;
  /** The back of the players and the staff repeats the front instead of the table of rounds. Substitutes keep the table. */
  backSameAsFront?: boolean;
  /** Full sheets of blank cards for substitutes, after the cards of the players. */
  substituteSheets?: number;
  /** Full sheets of staff cards, after the cards of the substitutes. */
  staffSheets?: number;
  /** The names of the staff cards, in order. The other staff cards have no name. */
  staffNames?: string[];
};

export const MAX_EXTRA_SHEETS = 20;
export const MAX_STAFF_NAMES = 200;
export const MAX_STAFF_NAME_LENGTH = 60;

/**
 * The blank substitute cards and the staff cards of the options. Each kind fills whole sheets: the staff gets more
 * sheets when its names do not fit on the chosen ones.
 */
export function idCardExtraCards(options: IdCardSheetOptions, perSheet: number): IdCardPlayer[][] {
  const blank = (kind: IdCardKind, name: string): IdCardPlayer => ({
    kind,
    playerId: 0,
    name,
    country: "",
    teamName: null,
    tableNumbers: [],
  });
  const substitutes = Array.from({ length: (options.substituteSheets ?? 0) * perSheet }, () => blank("substitute", ""));
  const names = (options.staffNames ?? []).map((name) => name.trim()).filter((name) => name.length > 0);
  const staffSheets = Math.max(options.staffSheets ?? 0, Math.ceil(names.length / perSheet));
  const staff = Array.from({ length: staffSheets * perSheet }, (_, index) => blank("staff", names[index] ?? ""));
  return [substitutes, staff].filter((cards) => cards.length > 0);
}

export function sheetPageSize(landscape: boolean): { width: number; height: number } {
  return landscape ? { width: A4_HEIGHT, height: A4_WIDTH } : { width: A4_WIDTH, height: A4_HEIGHT };
}

export type IdCardSheetGrid = { columns: number; rows: number; scale: number };

/** Returns the grid for the options, or null when not even one card fits on the sheet. */
export function idCardSheetGrid(options: IdCardSheetOptions): IdCardSheetGrid | null {
  const scale = options.scalePercent / 100;
  const { width, height } = sheetPageSize(options.landscape);
  const maxColumns = Math.floor((width - 2 * SHEET_MARGIN) / (CARD_WIDTH * scale));
  const maxRows = Math.floor((height - 2 * SHEET_MARGIN) / (CARD_HEIGHT * scale));
  if (maxColumns < 1 || maxRows < 1) return null;
  const count = Math.max(1, Math.min(options.cardsPerSheet, maxColumns * maxRows));
  const columns = Math.min(maxColumns, count);
  const rows = Math.ceil(count / columns);
  return { columns, rows, scale };
}

/**
 * Returns where the top left corner of a card is on the sheet page, in points.
 * The sheet turns like a book page, so the columns of the back are mirrored when printing on both sides.
 */
export function idCardSheetCardOrigin(
  sheet: IdCardSheetOptions,
  grid: IdCardSheetGrid,
  index: number,
  back: boolean,
): { x: number; y: number } {
  const page = sheetPageSize(sheet.landscape);
  const left = (page.width - CARD_WIDTH * grid.scale * grid.columns) / 2;
  const top = (page.height - CARD_HEIGHT * grid.scale * grid.rows) / 2;
  const column = index % grid.columns;
  const row = Math.floor(index / grid.columns);
  const drawColumn = back && sheet.doubleSided ? grid.columns - 1 - column : column;
  return { x: left + drawColumn * CARD_WIDTH * grid.scale, y: top + row * CARD_HEIGHT * grid.scale };
}

function drawSheetCutMarks(doc: PDFKit.PDFDocument, left: number, top: number, grid: IdCardSheetGrid): void {
  const cardWidth = CARD_WIDTH * grid.scale;
  const cardHeight = CARD_HEIGHT * grid.scale;
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

function drawCardOnSheet(
  doc: PDFKit.PDFDocument,
  x: number,
  y: number,
  scale: number,
  draw: () => void,
): void {
  doc.save();
  doc.translate(x, y).scale(scale);
  doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).clip();
  draw();
  doc.restore();
}

export async function buildIdCardsPdf(model: IdCardsDocument, sheet?: IdCardSheetOptions): Promise<Buffer> {
  const grid = sheet == null ? null : idCardSheetGrid(sheet);
  if (sheet != null && grid == null) throw new Error("The card size does not fit on an A4 sheet.");
  return new Promise<Buffer>((resolve, reject) => {
    const doc = new (pdfKit())({
      autoFirstPage: false,
      compress: true,
      info: { Title: `${model.tournamentShortName} ID cards` },
    });
    const chunks: Buffer[] = [];
    doc.on("data", (chunk: Buffer) => chunks.push(chunk));
    doc.on("error", reject);
    doc.on("end", () => resolve(Buffer.concat(chunks)));
    doc.registerFont("CardApp", appFontPath);
    const reusableLogo = openLogo(doc, model.associationLogo);

    if (sheet != null && grid != null) {
      const perSheet = grid.columns * grid.rows;
      const page = sheetPageSize(sheet.landscape);
      const pageOptions = { size: [page.width, page.height] as [number, number], margin: 0 };
      const left = (page.width - CARD_WIDTH * grid.scale * grid.columns) / 2;
      const top = (page.height - CARD_HEIGHT * grid.scale * grid.rows) / 2;
      // Each kind of card starts on a new sheet, so the sheets of substitutes and of staff are kept apart.
      const kinds = [model.players, ...idCardExtraCards(sheet, perSheet)];
      kinds.forEach((cards) => {
        for (let start = 0; start < cards.length; start += perSheet) {
          const group = cards.slice(start, start + perSheet);
          doc.addPage(pageOptions);
          group.forEach((player, index) => {
            const { x, y } = idCardSheetCardOrigin(sheet, grid, index, false);
            drawCardOnSheet(doc, x, y, grid.scale, () => drawFront(doc, model, player, reusableLogo));
          });
          if (sheet.cutMarks) drawSheetCutMarks(doc, left, top, grid);

          if (sheet.backs === false) continue;
          doc.addPage(pageOptions);
          group.forEach((player, index) => {
            const { x, y } = idCardSheetCardOrigin(sheet, grid, index, true);
            const repeatFront = sheet.backSameAsFront === true && player.kind !== "substitute";
            drawCardOnSheet(doc, x, y, grid.scale, () => {
              if (repeatFront) drawFront(doc, model, player, reusableLogo);
              else drawBack(doc, model, player);
            });
          });
        }
      });
    } else {
      model.players.forEach((player) => {
        doc.addPage({ size: [CARD_WIDTH, CARD_HEIGHT], margin: 0 });
        drawFront(doc, model, player, reusableLogo);
        doc.addPage({ size: [CARD_WIDTH, CARD_HEIGHT], margin: 0 });
        drawBack(doc, model, player);
      });
    }
    doc.end();
  });
}

export async function buildIdCardProofPdf(request: IdCardProofRequest): Promise<Buffer> {
  let associationLogo: Buffer | null = null;
  if (request.associationLogoDataBase64) {
    associationLogo = Buffer.from(request.associationLogoDataBase64, "base64");
  } else if (request.associationLogoUrl) {
    const logoUrl = new URL(request.associationLogoUrl);
    if (
      logoUrl.protocol === "https:" &&
      (logoUrl.hostname === "firebasestorage.googleapis.com" || logoUrl.hostname === "storage.googleapis.com")
    ) {
      const response = await fetch(logoUrl);
      if (response.ok) associationLogo = Buffer.from(await response.arrayBuffer());
    }
  }

  const rawRoundSchedules = Array.isArray(request.roundSchedules) ? request.roundSchedules : [];
  const numberOfRounds = Math.max(
    1,
    ...rawRoundSchedules.map((value) => {
      if (value == null || typeof value !== "object") return 0;
      return Number((value as Record<string, unknown>).roundId) || 0;
    }),
  );
  const configuredDates = rawRoundSchedules.flatMap((value): string[] => {
    if (value == null || typeof value !== "object") return [];
    const date = String((value as Record<string, unknown>).date ?? "").trim();
    return /^\d{4}-\d{2}-\d{2}$/.test(date) ? [date] : [];
  });
  const sortedDates = configuredDates.slice().sort();
  const scheduleStartDate = sortedDates[0] ?? "0000-01-01";
  const scheduleEndDate = sortedDates[sortedDates.length - 1] ?? "9999-12-31";
  const roundSchedules = readStoredRoundSchedules(
    request.roundSchedules,
    numberOfRounds,
    scheduleStartDate,
    scheduleEndDate,
  );

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

export async function loadIdCardsDocument(tournamentId: string): Promise<IdCardsDocument> {
  const tournamentRef = db.collection("tournaments").doc(tournamentId);
  const [tournament, slots, teams, tables, countries] = await Promise.all([
    tournamentRef.get(),
    tournamentRef.collection("players").get(),
    tournamentRef.collection("teams").get(),
    tournamentRef.collection("tables").get(),
    listCountries(),
  ]);
  if (!tournament.exists) throw notFound("Tournament not found");

  const orderedSlots = slots.docs.slice().sort((left, right) => Number(left.get("id")) - Number(right.get("id")));
  const missingAssignments = orderedSlots.filter((slot) => {
    return typeof slot.get("assignedEmaId") !== "string";
  });
  if (missingAssignments.length > 0) {
    throw conflict(`Assign every tournament player before creating ID cards. ${missingAssignments.length} assignments are missing.`, { code: "ASSIGNMENTS_MISSING", missing: missingAssignments.length });
  }

  // Every slot has an EMA id here, because a missing one stopped the cards above.
  const playerDocuments = await Promise.all(orderedSlots.map((slot) => {
    return db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(String(slot.get("assignedEmaId"))).get();
  }));
  if (playerDocuments.some((player) => !player.exists)) {
    throw conflict("One or more assigned EMA players no longer exist", { code: "EMA_PLAYERS_GONE" });
  }

  const teamNames = new Map<number, string>();
  teams.docs.forEach((team) => {
    teamNames.set(Number(team.get("id") ?? team.id), String(team.get("name") ?? "").trim());
  });
  const countryNames = new Map(countries.map((country) => [country.code, country.name]));
  const tablesByPlayer = new Map<number, Map<number, number>>();
  tables.docs.forEach((table) => {
    const roundId = Number(table.get("roundId"));
    const tableId = Number(table.get("tableId"));
    const playerIds = Array.isArray(table.get("playerIds")) ? table.get("playerIds") as unknown[] : [];
    playerIds.forEach((rawPlayerId) => {
      const playerId = Number(rawPlayerId);
      const rounds = tablesByPlayer.get(playerId) ?? new Map<number, number>();
      rounds.set(roundId, tableId);
      tablesByPlayer.set(playerId, rounds);
    });
  });

  const numRounds = Number(tournament.get("numRounds") ?? 0);
  const players: IdCardPlayer[] = orderedSlots.map((slot, index) => {
    const player = playerDocuments[index];
    const playerId = Number(slot.get("id") ?? slot.id);
    const teamId = Number(slot.get("team") ?? 0);
    const countryCode = String(player.get("country") ?? "").trim().toUpperCase();
    const rounds = tablesByPlayer.get(playerId) ?? new Map<number, number>();
    return {
      playerId,
      name: `${String(player.get("firstName") ?? "")} ${String(player.get("lastName") ?? "")}`.trim(),
      country: countryNames.get(iso2CountryCode(countryCode) ?? "") ?? countryCode,
      countryCode,
      teamName: tournament.get("isTeams") === true ? teamNames.get(teamId) ?? `Team ${teamId}` : null,
      tableNumbers: Array.from({ length: numRounds }, (_, roundIndex) => rounds.get(roundIndex + 1) ?? 0),
    };
  });

  // The logo and the color of the cards are the ones of the main organizer of the tournament.
  const organizerIds = readOrganizerIds(tournament.get("organizerIds"));
  const mainOrganizer = (await organizersOf(organizerIds))[0];
  const logoPath = await mainOrganizerLogoPath(organizerIds);
  const associationLogo = logoPath.length > 0
    ? await storage.file(logoPath).download().then(([bytes]) => bytes).catch(() => null)
    : null;
  const eventStartDate = String(tournament.get("eventStartDate") ?? "");
  const eventEndDate = String(tournament.get("eventEndDate") ?? eventStartDate);
  const storedShortName = String(tournament.get("shortName") ?? "").trim();
  const fallbackName = String(tournament.get("name") ?? "Tournament").trim().slice(0, 10);

  return {
    tournamentShortName: (storedShortName || fallbackName).slice(0, 10),
    year: /^\d{4}-/.test(eventStartDate) ? eventStartDate.slice(0, 4) : String(new Date().getUTCFullYear()),
    primaryColor: normalizeHexColor(mainOrganizer?.primaryColor ?? DEFAULT_PRIMARY_COLOR),
    associationLogo,
    numberOfRounds: numRounds,
    roundSchedules: readStoredRoundSchedules(
      tournament.get("roundSchedules"),
      numRounds,
      eventStartDate || "0000-01-01",
      eventEndDate || "9999-12-31",
    ),
    players,
  };
}

function idCardStaffRef(tournamentId: string) {
  // A document of its own, so saving the names never touches the tournament or its revisions.
  return db.collection("tournaments").doc(tournamentId).collection("idCards").doc("staff");
}

/** The names of the staff cards that the admins saved for the tournament, in order. */
export async function readIdCardStaffNames(tournamentId: string): Promise<string[]> {
  const snapshot = await idCardStaffRef(tournamentId).get();
  const names = snapshot.get("names");
  return Array.isArray(names) ? names.filter((name): name is string => typeof name === "string") : [];
}

/** Keeps the names of the staff cards, so the next ID cards of the tournament start with them. */
export async function saveIdCardStaffNames(tournamentId: string, names: string[]): Promise<string[]> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const clean = names.map((name) => name.trim()).filter((name) => name.length > 0);
  await idCardStaffRef(tournamentId).set({ names: clean, updatedAt: new Date().toISOString() });
  return clean;
}

export async function generateTournamentIdCards(tournamentId: string, sheet?: IdCardSheetOptions): Promise<Buffer> {
  return buildIdCardsPdf(await loadIdCardsDocument(tournamentId), sheet);
}

