import { badRequest } from "../api/httpError";
import { clockDurationMs, clockElapsedMs, readStoredRoundClocks, type RoundClock } from "./roundClock";
import { readStoredMatchDuration, readStoredMatchEndMode, type MatchEndMode } from "./tournamentSchedule";

/** Minutes after the end time in which the players can record the hand in play, with the mode FINISH_HAND. */
export const DEFAULT_MATCH_EXTRA_MINUTES = 15;
const MAX_MATCH_EXTRA_MINUTES = 120;

/** Where a match is in time. */
export type MatchPhase = "NOT_STARTED" | "PLAYING" | "EXTRA_TIME" | "OVER";

export function isValidTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value });
    return true;
  } catch (_error) {
    return false;
  }
}

/**
 * The zones that the admin app offers: "UTC" or a region name such as "Europe/Madrid".
 * The technical zones "Etc/..." and "SystemV/..." are not offered.
 */
export function isSelectableTimeZone(value: string): boolean {
  if (!isValidTimeZone(value)) return false;
  return value === "UTC" || (/^[A-Za-z_]+(\/[A-Za-z0-9_+-]+)+$/.test(value) && !value.startsWith("Etc/") && !value.startsWith("SystemV/"));
}

/** Validates a time zone that the admin app offers. An empty value means that the tournament has none. */
export function normalizeTimeZone(value: unknown): string | null {
  if (value == null) return null;
  const text = String(value).trim();
  if (text === "") return null;
  if (!isSelectableTimeZone(text)) throw badRequest("timeZone must be a region time zone, for example Europe/Madrid");
  return text;
}

/** Reads a stored time zone. An invalid value gives null. */
export function readStoredTimeZone(value: unknown): string | null {
  try {
    return normalizeTimeZone(value);
  } catch (_error) {
    return null;
  }
}

/** Validates the extra minutes of the mode FINISH_HAND. A missing value gives the default. */
export function normalizeMatchExtraMinutes(value: unknown): number {
  if (value == null) return DEFAULT_MATCH_EXTRA_MINUTES;
  const minutes = Number(value);
  if (!Number.isInteger(minutes) || minutes < 1 || minutes > MAX_MATCH_EXTRA_MINUTES) {
    throw badRequest(`matchExtraMinutes must be a whole number from 1 to ${MAX_MATCH_EXTRA_MINUTES}`);
  }
  return minutes;
}

export function readStoredMatchExtraMinutes(value: unknown): number {
  try {
    return normalizeMatchExtraMinutes(value);
  } catch (_error) {
    return DEFAULT_MATCH_EXTRA_MINUTES;
  }
}

/** Offset of a time zone from UTC at an instant, in milliseconds. */
function zoneOffsetMs(epochMs: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(new Date(epochMs));
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value);
  const asUtc = Date.UTC(value("year"), value("month") - 1, value("day"), value("hour"), value("minute"), value("second"));
  return asUtc - Math.floor(epochMs / 1000) * 1000;
}

/** The instant of a local date and time in a time zone. Null when the date or the time is not valid. */
export function zonedEpochMs(date: string, time: string, timeZone: string): number | null {
  const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const timeMatch = /^(\d{2}):(\d{2})$/.exec(time);
  if (!dateMatch || !timeMatch) return null;
  const guess = Date.UTC(Number(dateMatch[1]), Number(dateMatch[2]) - 1, Number(dateMatch[3]), Number(timeMatch[1]), Number(timeMatch[2]));
  // The offset can differ at the guess and at the result, near a change of summer time. Two passes are enough.
  const first = guess - zoneOffsetMs(guess, timeZone);
  return guess - zoneOffsetMs(first, timeZone);
}

export type MatchTimeRules = {
  timeZone: string | null;
  durationMinutes: number;
  endMode: MatchEndMode;
  extraMinutes: number;
};

/** Minutes before the planned start of a round in which the players can set the seats of their table. */
export const SEATING_LEAD_MINUTES = 10;

/** Planned start of a round as an instant. Null when the tournament has no time zone or the round has no date. */
export function roundStartMs(
  schedule: { date: string | null; startTime: string | null } | undefined,
  timeZone: string | null,
): number | null {
  if (timeZone == null || schedule?.date == null || schedule.date === "") return null;
  return zonedEpochMs(schedule.date, schedule.startTime ?? "00:00", timeZone);
}

/**
 * The phase of the matches of a round at an instant. The match time counts from the Play of the Timer, so a round
 * without a clock has not started, whatever its planned time. A paused clock keeps its phase.
 */
export function matchPhaseAt(clock: RoundClock | null, rules: MatchTimeRules, nowMs: number): MatchPhase {
  if (clock == null) return "NOT_STARTED";
  const elapsed = clockElapsedMs(clock, nowMs);
  const duration = clockDurationMs(clock, rules.durationMinutes);
  if (elapsed < duration) return "PLAYING";
  const extra = rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60_000 : 0;
  return elapsed < duration + extra ? "EXTRA_TIME" : "OVER";
}

type Data = Record<string, unknown>;

export type RoundMatchTime = {
  rules: MatchTimeRules;
  schedule: { date: string | null; startTime: string | null } | undefined;
  clock: RoundClock | null;
};

/** The match time rules, the planned schedule and the clock of a round, read from a stored tournament document. */
export function matchTimeOfTournament(tournament: Data, roundId: number): RoundMatchTime {
  const schedules = Array.isArray(tournament.roundSchedules) ? tournament.roundSchedules as Data[] : [];
  const row = schedules.find((item) => Number(item.roundId) === roundId);
  return {
    rules: {
      timeZone: readStoredTimeZone(tournament.timeZone),
      durationMinutes: readStoredMatchDuration(tournament.matchDurationMinutes),
      endMode: readStoredMatchEndMode(tournament.matchEndMode),
      extraMinutes: readStoredMatchExtraMinutes(tournament.matchExtraMinutes),
    },
    schedule: row == null
      ? undefined
      : {
        date: typeof row.date === "string" ? row.date : null,
        startTime: typeof row.startTime === "string" ? row.startTime : null,
      },
    clock: readStoredRoundClocks(tournament).get(roundId) ?? null,
  };
}

/** The phase of the matches of a round. */
export function tournamentMatchPhase(tournament: Data, roundId: number, nowMs: number): MatchPhase {
  const { rules, clock } = matchTimeOfTournament(tournament, roundId);
  return matchPhaseAt(clock, rules, nowMs);
}

/**
 * True when the players of a round can set the seats of their tables: from [SEATING_LEAD_MINUTES] before the planned
 * start, or as soon as the round started, until the match time is over. A round without a planned time is always open.
 * The first hand of a table closes its seats; the caller checks that.
 */
export function seatingIsOpen(tournament: Data, roundId: number, nowMs: number): boolean {
  const { rules, schedule, clock } = matchTimeOfTournament(tournament, roundId);
  const phase = matchPhaseAt(clock, rules, nowMs);
  if (phase === "OVER") return false;
  if (phase !== "NOT_STARTED") return true;
  const planned = roundStartMs(schedule, rules.timeZone);
  return planned == null || nowMs >= planned - SEATING_LEAD_MINUTES * 60_000;
}
