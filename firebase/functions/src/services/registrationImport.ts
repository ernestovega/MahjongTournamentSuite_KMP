import { badRequest } from "../api/httpError";
import { isValidEmail, normalizeFoodRestrictions, teamFromForm } from "./registrationSettings";

/**
 * Reads the answers of the old Google Form of a tournament (File > Download > CSV in Google Sheets), so the
 * registrations that people already sent move into the app. Pure: the route checks the EMA registry and writes.
 * The columns are found by their title, in English or Spanish, in any order.
 */
export type FormRow = {
  /** The line in the file, from 2 (line 1 holds the titles). */
  line: number;
  /** Milliseconds of the time of the answer, or null when the file has no time. */
  registeredAt: number | null;
  email: string;
  firstName: string;
  lastName: string;
  /** The EMA number with 8 digits, or the text as it came when it is not a number. */
  emaId: string;
  country: string;
  team: string | null;
  foodRestrictions: string;
  problems: string[];
};

const COLUMNS: Record<string, RegExp> = {
  time: /^(marca temporal|timestamp|fecha|date)/i,
  email: /(correo|e-?mail)/i,
  firstName: /^(first name|nombre|name)$/i,
  lastName: /^(last name|apellidos?|surname|family name)$/i,
  emaId: /(^|[^a-z])ema([^a-z]|$)/i,
  country: /^(country|pa[ií]s)/i,
  team: /^(team|equipo)/i,
  food: /(food|comida|alerg|allerg|diet|restric)/i,
};

/** Splits a CSV text into rows. Commas or semicolons, quotes with doubled quotes inside, CRLF or LF. */
export function parseCsv(text: string): string[][] {
  const source = text.replace(/^﻿/, "");
  const firstLine = source.split(/\r?\n/, 1)[0] ?? "";
  const separator = (firstLine.match(/;/g)?.length ?? 0) > (firstLine.match(/,/g)?.length ?? 0) ? ";" : ",";
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < source.length; index++) {
    const char = source[index];
    if (quoted) {
      if (char === "\"" && source[index + 1] === "\"") {
        field += "\"";
        index++;
      } else if (char === "\"") {
        quoted = false;
      } else {
        field += char;
      }
    } else if (char === "\"") {
      quoted = true;
    } else if (char === separator) {
      row.push(field);
      field = "";
    } else if (char === "\n" || char === "\r") {
      if (char === "\r" && source[index + 1] === "\n") index++;
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += char;
    }
  }
  if (field !== "" || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows.filter((cells) => cells.some((cell) => cell.trim() !== ""));
}

/** "10990039.0", "4290023" or " 04290023 " give "04290023"-style numbers of 8 digits. */
export function normalizeFormEmaId(value: string): string {
  const text = value.trim().replace(/\.0+$/, "");
  return /^\d{1,8}$/.test(text) ? text.padStart(8, "0") : text;
}

/** Reads "15/09/2026 18:28:56", "2026-09-15 18:28:56" or an ISO time. Local times are read as UTC. */
export function parseFormTime(value: string): number | null {
  const text = value.trim();
  const european = text.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/);
  if (european) {
    const [, day, month, year, hour, minute, second] = european;
    return Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour ?? 0), Number(minute ?? 0), Number(second ?? 0));
  }
  const iso = Date.parse(text.includes("T") || text.endsWith("Z") ? text : `${text.replace(" ", "T")}Z`);
  return Number.isNaN(iso) ? null : iso;
}

export const MAX_IMPORT_ROWS = 500;

/** Reads the CSV text of the form, or the cells of the form as an Excel file gives them. */
export function readFormRows(input: string | string[][]): FormRow[] {
  const rows = (typeof input === "string" ? parseCsv(input) : input)
    .filter((cells) => cells.some((cell) => cell.trim() !== ""));
  if (rows.length < 2) throw badRequest("The file has no answers", { code: "EMPTY_FILE" });
  if (rows.length - 1 > MAX_IMPORT_ROWS) throw badRequest(`At most ${MAX_IMPORT_ROWS} rows`, { code: "TOO_MANY_ROWS" });
  const titles = rows[0].map((title) => title.trim());
  const column = (key: keyof typeof COLUMNS) => titles.findIndex((title) => COLUMNS[key].test(title));
  const indexes = {
    time: column("time"), email: column("email"), firstName: column("firstName"), lastName: column("lastName"),
    emaId: column("emaId"), country: column("country"), team: column("team"), food: column("food"),
  };
  if (indexes.emaId < 0) throw badRequest("The file has no column with the EMA number", { code: "NO_EMA_COLUMN" });
  const cell = (cells: string[], index: number) => (index < 0 ? "" : (cells[index] ?? "").trim());
  const seenEma = new Set<string>();
  return rows.slice(1).map((cells, offset) => {
    const problems: string[] = [];
    const emaId = normalizeFormEmaId(cell(cells, indexes.emaId));
    if (!/^\d{8}$/.test(emaId)) problems.push("INVALID_EMA");
    else if (seenEma.has(emaId)) problems.push("DUPLICATE_EMA");
    seenEma.add(emaId);
    const email = cell(cells, indexes.email).toLowerCase();
    if (email !== "" && !isValidEmail(email)) problems.push("INVALID_EMAIL");
    let food = "";
    try {
      food = normalizeFoodRestrictions(cell(cells, indexes.food));
    } catch {
      food = cell(cells, indexes.food).slice(0, 300);
    }
    return {
      line: offset + 2,
      registeredAt: indexes.time < 0 ? null : parseFormTime(cell(cells, indexes.time)),
      email,
      firstName: cell(cells, indexes.firstName),
      lastName: cell(cells, indexes.lastName),
      emaId,
      country: cell(cells, indexes.country),
      team: teamFromForm(cell(cells, indexes.team)),
      foodRestrictions: food,
      problems,
    };
  });
}
