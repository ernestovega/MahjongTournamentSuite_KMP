import PDFDocument from "pdfkit";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

import { conflict, notFound } from "../api/httpError";
import { db, storage } from "../firebase";
import { listCountries } from "./countriesService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";

const CARD_WIDTH = 242.88;
const CARD_HEIGHT = 153;
const DEFAULT_PRIMARY_COLOR = "#02B16B";
const BACKGROUND_COLOR = "#FFFDF5";

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

export type IdCardPlayer = {
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
  players: IdCardPlayer[];
};

function normalizeHexColor(value: string): string {
  const color = value.trim().toUpperCase();
  return /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR;
}

function titleCase(value: string): string {
  return value.toLocaleLowerCase().replace(/(^|[\s'-])\p{L}/gu, (match) => match.toLocaleUpperCase());
}

function splitPlayerName(value: string): { givenName: string; surname: string } {
  const words = value.trim().split(/\s+/).filter(Boolean);
  if (words.length <= 1) return { givenName: titleCase(words[0] ?? "Player"), surname: "" };
  return {
    givenName: titleCase(words[0]),
    surname: words.slice(1).join(" ").toLocaleUpperCase(),
  };
}

function iso2CountryCode(value: string): string | null {
  const code = value.trim().toUpperCase();
  if (code === "EU" || code.length === 0) return null;
  if (/^[A-Z]{2}$/.test(code)) return code;
  return emaCountryCodeToIso2[code] ?? null;
}

function flagSvgFor(countryCode: string | undefined): string | null {
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

function drawCenteredText(
  doc: PDFKit.PDFDocument,
  text: string,
  width: number,
  height: number,
): void {
  const textHeight = doc.heightOfString(text, { width, align: "center", lineBreak: false });
  doc.text(text, 0, (height - textHeight) / 2, { width, align: "center", lineBreak: false });
}

function drawCountryFlag(doc: PDFKit.PDFDocument, countryCode: string | undefined): void {
  const svg = flagSvgFor(countryCode);
  if (svg != null) {
    doc.save();
    svgToPdf(doc, svg, 189, 8, { width: 24, height: 18, preserveAspectRatio: "xMidYMid meet" });
    doc.restore();
    return;
  }

  doc.font("CardApp").fontSize(7).fillColor("#FFFFFF");
  doc.text("Guest", 174, 12, { width: 54, align: "center", lineBreak: false });
}

function drawFront(
  doc: PDFKit.PDFDocument,
  model: IdCardsDocument,
  player: IdCardPlayer,
  associationLogo: PDFKit.Mixins.ImageSrc | null,
): void {
  const primary = normalizeHexColor(model.primaryColor);
  const { givenName, surname } = splitPlayerName(player.name);

  doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).fill(BACKGROUND_COLOR);
  doc.rect(0, 0, CARD_WIDTH, 76).fill(primary);
  const logoCenterX = 68.66;
  const logoCenterY = 71.39;
  doc.circle(logoCenterX, logoCenterY, 46.9).fill(primary);

  if (associationLogo != null) {
    const logoRadius = 32.5;
    doc.circle(logoCenterX, logoCenterY, 34.5).fill("#FFFFFF");
    try {
      doc.save();
      doc.circle(logoCenterX, logoCenterY, logoRadius).clip();
      doc.image(associationLogo, logoCenterX - logoRadius, logoCenterY - logoRadius, {
        cover: [logoRadius * 2, logoRadius * 2],
        align: "center",
        valign: "center",
      });
      doc.restore();
    } catch (_error) {
      doc.restore();
      // Keep the card usable if an old logo file has an unsupported format.
    }
  }

  drawCurvedText(
    doc,
    model.tournamentShortName,
    68.66,
    71.39,
    53,
    -90,
    Math.min(124, 54 + model.tournamentShortName.length * 7),
    model.tournamentShortName.length > 8 ? 17 : 20,
    "#FFFFFF",
  );
  drawCurvedText(doc, model.year, 68.66, 71.39, 52, 90, 94, 16, primary, true);

  doc.font("CardApp").fillColor("#FFFFFF");
  const teamText = player.teamName == null ? "" : player.teamName.trim();
  if (teamText.length > 0) {
    const teamSize = fitText(doc.font("CardApp"), teamText, 93, 10, 6);
    doc.fontSize(teamSize).text(teamText, 142, 31, { width: 94, align: "center", lineBreak: false });
  }

  doc.font("CardApp").fontSize(27).text(String(player.playerId), 151, 49, {
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

function drawBack(doc: PDFKit.PDFDocument, model: IdCardsDocument, player: IdCardPlayer): void {
  const primary = normalizeHexColor(model.primaryColor);
  doc.rect(0, 0, CARD_WIDTH, CARD_HEIGHT).fill(BACKGROUND_COLOR);

  doc.save();
  const backgroundNumber = String(player.playerId);
  const backgroundNumberSize = fitText(doc.font("CardApp"), backgroundNumber, 190, 132, 88);
  doc.opacity(0.28).fontSize(backgroundNumberSize).fillColor(primary);
  drawCenteredText(doc, backgroundNumber, CARD_WIDTH, CARD_HEIGHT);
  doc.restore();

  const x = 14;
  const y = 10;
  const width = CARD_WIDTH - 28;
  const headerHeight = 22;
  const roundCount = Math.max(1, model.numberOfRounds ?? player.tableNumbers.length);
  const tableNumbers = Array.from({ length: roundCount }, (_, index) => player.tableNumbers[index] ?? 0);
  const availableRowsHeight = CARD_HEIGHT - y - 10 - headerHeight;
  const rowHeight = availableRowsHeight / roundCount;
  const columns = [38, 42, 67, width - 38 - 42 - 67];
  const headers = ["Round", "Table", "Table points", "Score"];

  doc.lineWidth(0.65).strokeColor("#000000").fillColor("#000000").font("CardApp");
  doc.rect(x, y, width, headerHeight + rowHeight * roundCount).stroke();

  let columnX = x;
  columns.slice(0, -1).forEach((columnWidth) => {
    columnX += columnWidth;
    doc.moveTo(columnX, y).lineTo(columnX, y + headerHeight + rowHeight * roundCount).stroke();
  });
  doc.moveTo(x, y + headerHeight).lineTo(x + width, y + headerHeight).stroke();
  for (let index = 1; index < roundCount; index += 1) {
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
  tableNumbers.forEach((tableNumber, index) => {
    const rowY = y + headerHeight + rowHeight * index;
    doc.fontSize(rowFontSize);
    drawCellText(doc, String(index + 1), x, rowY, columns[0], rowHeight);
    drawCellText(doc, tableNumber > 0 ? String(tableNumber) : "-", x + columns[0], rowY, columns[1], rowHeight);
  });
}

export async function buildIdCardsPdf(model: IdCardsDocument): Promise<Buffer> {
  return new Promise<Buffer>((resolve, reject) => {
    const doc = new PDFDocument({
      autoFirstPage: false,
      compress: true,
      info: { Title: `${model.tournamentShortName} ID cards` },
    });
    const chunks: Buffer[] = [];
    doc.on("data", (chunk: Buffer) => chunks.push(chunk));
    doc.on("error", reject);
    doc.on("end", () => resolve(Buffer.concat(chunks)));
    doc.registerFont("CardApp", appFontPath);
    const reusableLogo = model.associationLogo == null
      ? null
      : (doc as unknown as { openImage(src: Buffer): PDFKit.Mixins.ImageSrc }).openImage(model.associationLogo);

    model.players.forEach((player) => {
      doc.addPage({ size: [CARD_WIDTH, CARD_HEIGHT], margin: 0 });
      drawFront(doc, model, player, reusableLogo);
      doc.addPage({ size: [CARD_WIDTH, CARD_HEIGHT], margin: 0 });
      drawBack(doc, model, player);
    });
    doc.end();
  });
}

export async function generateTournamentIdCards(tournamentId: string): Promise<Buffer> {
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
    return typeof slot.get("assignedEmaId") !== "string" && slot.get("nonMember") == null;
  });
  if (missingAssignments.length > 0) {
    throw conflict(`Assign every tournament player before creating ID cards. ${missingAssignments.length} assignments are missing.`);
  }

  const playerDocuments = await Promise.all(orderedSlots.map((slot) => {
    const emaId = slot.get("assignedEmaId");
    return typeof emaId === "string"
      ? db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()
      : Promise.resolve(null);
  }));
  if (playerDocuments.some((player) => player != null && !player.exists)) {
    throw conflict("One or more assigned EMA players no longer exist");
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
    const nonMember = slot.get("nonMember") as Record<string, unknown> | null | undefined;
    const countryCode = String(player?.get("country") ?? nonMember?.country ?? "").trim().toUpperCase();
    const rounds = tablesByPlayer.get(playerId) ?? new Map<number, number>();
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
    ? await storage.file(logoPath).download().then(([bytes]) => bytes).catch(() => null)
    : null;
  const eventStartDate = String(tournament.get("eventStartDate") ?? "");
  const storedShortName = String(tournament.get("shortName") ?? "").trim();
  const fallbackName = String(tournament.get("name") ?? "Tournament").trim().slice(0, 10);

  return buildIdCardsPdf({
    tournamentShortName: (storedShortName || fallbackName).slice(0, 10),
    year: /^\d{4}-/.test(eventStartDate) ? eventStartDate.slice(0, 4) : String(new Date().getUTCFullYear()),
    primaryColor: normalizeHexColor(String(tournament.get("primaryColor") ?? DEFAULT_PRIMARY_COLOR)),
    associationLogo,
    numberOfRounds: numRounds,
    players,
  });
}

const emaCountryCodeToIso2: Record<string, string> = {
  AUT: "AT", BEL: "BE", BLR: "BY", CHE: "CH", CZE: "CZ", DEU: "DE", DEN: "DK", DNK: "DK",
  ESP: "ES", FIN: "FI", FRA: "FR", GBR: "GB", GER: "DE", HUN: "HU", IRL: "IE", ITA: "IT",
  LAT: "LV", NED: "NL", NLD: "NL", NOR: "NO", POL: "PL", POR: "PT", PRT: "PT", ROU: "RO",
  RUS: "RU", SUI: "CH", SVK: "SK", SWE: "SE", UKR: "UA",
};
