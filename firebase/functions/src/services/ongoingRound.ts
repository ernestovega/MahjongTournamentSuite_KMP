import { matchTimeOfTournament, roundStartMs, zonedEpochMs } from "./matchTime";
import type { RoundClock } from "./roundClock";

type Data = Record<string, unknown>;

type Slot = { date: string; startTime: string };

/**
 * What it takes to leave an ongoing tournament ready to play its next round: the first round after the last round
 * with hands starts now, so the organizer only has to press Play in the Timer.
 */
export type OngoingRoundPlan = {
  /** The round that waits for the Play. */
  roundId: number;
  /** Its planned start before the change. */
  previousStart: Slot;
  /** Milliseconds that every planned time moves. */
  shiftMs: number;
  roundSchedules: Array<{ roundId: number } & Slot>;
  agendaItems: Array<{ title: string; date: string; startTime: string | null }>;
  eventStartDate: string;
  eventEndDate: string;
  /** The clocks of the rounds before [roundId]: each one is over now. */
  clocks: Map<number, RoundClock>;
  /** The rounds from [roundId] on that have a clock today. The change removes it, so they have not started. */
  clearedClocks: number[];
};

const pad2 = (value: number): string => String(value).padStart(2, "0");

/** The local date and time of an instant in a time zone, as the schedule stores them. */
export function zonedSlot(epochMs: number, timeZone: string): Slot {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).formatToParts(new Date(epochMs));
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value);
  return {
    date: `${value("year")}-${pad2(value("month"))}-${pad2(value("day"))}`,
    startTime: `${pad2(value("hour"))}:${pad2(value("minute"))}`,
  };
}

/** The round that waits for the Play: the one after the last round with hands. */
export function nextRoundWithoutHands(roundsWithHands: Iterable<number>): number {
  let last = 0;
  for (const roundId of roundsWithHands) last = Math.max(last, roundId);
  return last + 1;
}

/**
 * Moves the whole schedule of [tournament] (rounds, agenda and event dates) by the same time, so that the first round
 * after the last round with hands starts at [nowMs], to the minute. The rounds keep their order and the time between
 * them. The rounds before it get a clock that is over, and the rounds from it on lose their clock, so the Timer opens
 * at that round and its Play starts it.
 */
export function planOngoingRound(tournament: Data, roundsWithHands: Iterable<number>, nowMs: number): OngoingRoundPlan {
  if (tournament.isCompleted === true) throw new Error("The tournament is completed");
  const numRounds = Number(tournament.numRounds ?? 0);
  const roundId = nextRoundWithoutHands(roundsWithHands);
  if (roundId > numRounds) {
    throw new Error(`Every round up to ${numRounds} has hands. Seed the tournament again to get empty rounds.`);
  }
  const timeZone = matchTimeOfTournament(tournament, 1).rules.timeZone;
  if (timeZone == null) throw new Error("The tournament has no time zone");

  const plannedMs = (round: number): number => {
    const start = roundStartMs(matchTimeOfTournament(tournament, round).schedule, timeZone);
    if (start == null) throw new Error(`Round ${round} has no date and start time`);
    return start;
  };
  const startMs = Math.floor(nowMs / 60_000) * 60_000;
  const shiftMs = startMs - plannedMs(roundId);
  const moved = (epochMs: number) => zonedSlot(epochMs + shiftMs, timeZone);

  const roundSchedules = Array.from({ length: numRounds }, (_, index) => ({ roundId: index + 1, ...moved(plannedMs(index + 1)) }));

  const agenda = Array.isArray(tournament.agendaItems) ? tournament.agendaItems as Data[] : [];
  const agendaItems = agenda.map((item) => {
    const title = String(item.title ?? "");
    const date = typeof item.date === "string" ? item.date : "";
    const time = typeof item.startTime === "string" && item.startTime !== "" ? item.startTime : null;
    const at = zonedEpochMs(date, time ?? "12:00", timeZone);
    if (at == null) return { title, date, startTime: time };
    const slot = moved(at);
    return { title, date: slot.date, startTime: time == null ? null : slot.startTime };
  });

  // The event keeps its days around the rounds, and always holds every round.
  const movedDate = (value: unknown): string | null => {
    const at = typeof value === "string" ? zonedEpochMs(value, "12:00", timeZone) : null;
    return at == null ? null : moved(at).date;
  };
  const roundDates = roundSchedules.map((row) => row.date).sort();
  const eventStartDate = [movedDate(tournament.eventStartDate), roundDates[0]]
    .filter((date): date is string => date != null).sort()[0];
  const eventEndDate = [movedDate(tournament.eventEndDate), roundDates[roundDates.length - 1]]
    .filter((date): date is string => date != null).sort().reverse()[0];

  // A round before it ends before now, extra time included, so the server closes its tables and takes no more hands.
  const { rules } = matchTimeOfTournament(tournament, 1);
  const extraMs = rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60_000 : 0;
  const latestStartMs = startMs - rules.durationMinutes * 60_000 - extraMs - 60_000;
  const clocks = new Map<number, RoundClock>();
  for (let round = 1; round < roundId; round += 1) {
    clocks.set(round, { startedAtMs: Math.min(plannedMs(round) + shiftMs, latestStartMs), pausedAtMs: null, pausedMs: 0, adjustMs: 0 });
  }
  const stored = tournament.roundClocks != null && typeof tournament.roundClocks === "object" ? tournament.roundClocks as Data : {};
  const clearedClocks = Object.keys(stored).map(Number).filter((round) => Number.isInteger(round) && round >= roundId).sort((a, b) => a - b);

  return {
    roundId,
    previousStart: zonedSlot(plannedMs(roundId), timeZone),
    shiftMs,
    roundSchedules,
    agendaItems,
    eventStartDate,
    eventEndDate,
    clocks,
    clearedClocks,
  };
}
