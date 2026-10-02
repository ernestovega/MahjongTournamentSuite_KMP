import { badRequest } from "../api/httpError";
import { isValidIsoDate } from "./tournamentDates";

export type TournamentRoundSchedule = {
  roundId: number;
  date: string | null;
  startTime: string | null;
};

export type TournamentAgendaItem = {
  title: string;
  date: string | null;
  startTime: string | null;
  endTime: string | null;
};

const LOCAL_TIME_PATTERN = /^(?:[01]\d|2[0-3]):[0-5]\d$/;

function optionalString(value: unknown): string | null {
  if (value == null) return null;
  const normalized = String(value).trim();
  return normalized.length > 0 ? normalized : null;
}

function normalizedDate(value: unknown, field: string, startDate: string, endDate: string): string | null {
  const date = optionalString(value);
  if (date == null) return null;
  if (!isValidIsoDate(date)) throw badRequest(`${field} must use yyyy-MM-dd`);
  if (date < startDate || date > endDate) throw badRequest(`${field} must be inside the tournament date range`);
  return date;
}

function normalizedTime(value: unknown, field: string): string | null {
  const time = optionalString(value);
  if (time == null) return null;
  if (!LOCAL_TIME_PATTERN.test(time)) throw badRequest(`${field} must use HH:mm`);
  return time;
}

export function normalizeRoundSchedules(
  value: unknown,
  numRounds: number,
  eventStartDate: string,
  eventEndDate: string,
): TournamentRoundSchedule[] {
  const rows = value == null ? [] : value;
  if (!Array.isArray(rows)) throw badRequest("roundSchedules must be an array");
  if (rows.length > numRounds) throw badRequest("roundSchedules contains too many rows");

  const byRound = new Map<number, TournamentRoundSchedule>();
  rows.forEach((value, index) => {
    if (value == null || typeof value !== "object") {
      throw badRequest("Invalid round schedule", { index });
    }
    const row = value as Record<string, unknown>;
    const roundId = Number(row.roundId);
    if (!Number.isInteger(roundId) || roundId < 1 || roundId > numRounds) {
      throw badRequest("Invalid round schedule id", { index, value: row.roundId });
    }
    if (byRound.has(roundId)) throw badRequest("Round schedule ids must be unique", { roundId });
    byRound.set(roundId, {
      roundId,
      date: normalizedDate(row.date, `roundSchedules[${index}].date`, eventStartDate, eventEndDate),
      startTime: normalizedTime(row.startTime, `roundSchedules[${index}].startTime`),
    });
  });

  return Array.from({ length: numRounds }, (_, index) => {
    const roundId = index + 1;
    return byRound.get(roundId) ?? { roundId, date: null, startTime: null };
  });
}

export function normalizeAgendaItems(
  value: unknown,
  eventStartDate: string,
  eventEndDate: string,
): TournamentAgendaItem[] {
  const rows = value == null ? [] : value;
  if (!Array.isArray(rows)) throw badRequest("agendaItems must be an array");
  if (rows.length > 100) throw badRequest("agendaItems must contain at most 100 rows");

  return rows.flatMap((value, index): TournamentAgendaItem[] => {
    if (value == null || typeof value !== "object") {
      throw badRequest("Invalid agenda item", { index });
    }
    const row = value as Record<string, unknown>;
    const title = String(row.title ?? "").trim();
    if (title.length === 0) return [];
    if (title.length > 120) throw badRequest(`agendaItems[${index}].title must contain at most 120 characters`);

    const startTime = normalizedTime(row.startTime, `agendaItems[${index}].startTime`);
    const endTime = normalizedTime(row.endTime, `agendaItems[${index}].endTime`);
    if (startTime != null && endTime != null && endTime < startTime) {
      throw badRequest(`agendaItems[${index}].endTime must not be before its start time`);
    }
    return [{
      title,
      date: normalizedDate(row.date, `agendaItems[${index}].date`, eventStartDate, eventEndDate),
      startTime,
      endTime,
    }];
  });
}

export function readStoredRoundSchedules(
  value: unknown,
  numRounds: number,
  eventStartDate: string,
  eventEndDate: string,
): TournamentRoundSchedule[] {
  try {
    return normalizeRoundSchedules(value, numRounds, eventStartDate, eventEndDate);
  } catch (_error) {
    return Array.from({ length: numRounds }, (_, index) => ({
      roundId: index + 1,
      date: null,
      startTime: null,
    }));
  }
}

export function readStoredAgendaItems(
  value: unknown,
  eventStartDate: string,
  eventEndDate: string,
): TournamentAgendaItem[] {
  try {
    return normalizeAgendaItems(value, eventStartDate, eventEndDate);
  } catch (_error) {
    return [];
  }
}
