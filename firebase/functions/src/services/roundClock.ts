import { badRequest, conflict } from "../api/httpError";

/**
 * The clock of one round. The Play button of the Timer starts it, so the match time counts from the real start and
 * not from the planned time. A round without a clock has not started.
 *
 * All instants are milliseconds since the epoch, taken from the clock of the server.
 */
export type RoundClock = {
  /** The instant of the start. */
  startedAtMs: number;
  /** The instant of the pause while the clock is paused, otherwise null. */
  pausedAtMs: number | null;
  /** Milliseconds of the pauses that are over. */
  pausedMs: number;
  /** Milliseconds added to (or, when negative, taken from) the match duration with "Set time". */
  adjustMs: number;
};

/** A clock as the apps receive it. */
export type RoundClockView = RoundClock & { roundId: number };

export type RoundClockAction =
  | { kind: "start"; agoMs: number }
  | { kind: "pause"; agoMs: number }
  | { kind: "resume"; agoMs: number }
  | { kind: "reset" }
  | { kind: "setRemaining"; remainingMs: number };

/** An action made offline reaches the server later. The server accepts this delay for a pause or a resume. */
export const MAX_ACTION_DELAY_MS = 10 * 60_000;
/** "Set time" accepts at most this remaining time. */
const MAX_REMAINING_MS = 12 * 60 * 60_000;

type Data = Record<string, unknown>;

const finite = (value: unknown): number | null => {
  const number = Number(value);
  return value != null && value !== "" && Number.isFinite(number) ? Math.round(number) : null;
};

/** Reads one stored clock. A damaged value gives null, so the round counts as not started. */
export function readStoredRoundClock(value: unknown): RoundClock | null {
  if (value == null || typeof value !== "object") return null;
  const data = value as Data;
  const startedAtMs = finite(data.startedAtMs);
  if (startedAtMs == null || startedAtMs <= 0) return null;
  const pausedAtMs = finite(data.pausedAtMs);
  return {
    startedAtMs,
    pausedAtMs: pausedAtMs != null && pausedAtMs >= startedAtMs ? pausedAtMs : null,
    pausedMs: Math.max(0, finite(data.pausedMs) ?? 0),
    adjustMs: finite(data.adjustMs) ?? 0,
  };
}

/** The clocks of a tournament document by round id. Rounds outside 1..numRounds are left out. */
export function readStoredRoundClocks(tournament: Data): Map<number, RoundClock> {
  const numRounds = Number(tournament.numRounds ?? 0);
  const raw = tournament.roundClocks != null && typeof tournament.roundClocks === "object" ? tournament.roundClocks as Data : {};
  const clocks = new Map<number, RoundClock>();
  for (const [key, value] of Object.entries(raw)) {
    const roundId = Number(key);
    if (!Number.isInteger(roundId) || roundId < 1 || (numRounds > 0 && roundId > numRounds)) continue;
    const clock = readStoredRoundClock(value);
    if (clock != null) clocks.set(roundId, clock);
  }
  return clocks;
}

/** The clocks sorted by round, as the apps receive them. */
export function roundClockViews(tournament: Data): RoundClockView[] {
  return [...readStoredRoundClocks(tournament).entries()]
    .sort(([a], [b]) => a - b)
    .map(([roundId, clock]) => ({ roundId, ...clock }));
}

/** Milliseconds of play at an instant. Pauses do not count. */
export function clockElapsedMs(clock: RoundClock, nowMs: number): number {
  const until = clock.pausedAtMs ?? nowMs;
  return Math.max(0, until - clock.startedAtMs - clock.pausedMs);
}

/** Milliseconds that the match lasts with the adjustment of "Set time". */
export function clockDurationMs(clock: RoundClock, durationMinutes: number): number {
  return Math.max(0, durationMinutes * 60_000 + clock.adjustMs);
}

/** Milliseconds left until the end of the match time. Zero after the end. */
export function clockRemainingMs(clock: RoundClock, durationMinutes: number, nowMs: number): number {
  return Math.max(0, clockDurationMs(clock, durationMinutes) - clockElapsedMs(clock, nowMs));
}

/** The instant of the end of the match time, or null while the clock is paused. */
export function clockEndMs(clock: RoundClock, durationMinutes: number): number | null {
  if (clock.pausedAtMs != null) return null;
  return clock.startedAtMs + clock.pausedMs + clockDurationMs(clock, durationMinutes);
}

/** The instant of an action that the app made [agoMs] milliseconds before the request. */
function actionTime(nowMs: number, agoMs: number, maxAgoMs: number): number {
  if (!Number.isFinite(agoMs) || agoMs < 0) throw badRequest("The delay of the action must be zero or more");
  return nowMs - Math.min(agoMs, maxAgoMs);
}

/**
 * The clock after an action. Null means that the round has no clock any more (a reset).
 * Every action can be sent twice: a second Play, Pause or Resume changes nothing, so two Timers of the same round
 * never fight. The first Play wins.
 */
export function applyRoundClockAction(
  clock: RoundClock | null,
  action: RoundClockAction,
  durationMinutes: number,
  nowMs: number,
): RoundClock | null {
  switch (action.kind) {
    case "start": {
      if (clock != null) return clock;
      // "Started N minutes ago" can go back up to the whole match.
      const startedAtMs = actionTime(nowMs, action.agoMs, durationMinutes * 60_000);
      return { startedAtMs, pausedAtMs: null, pausedMs: 0, adjustMs: 0 };
    }
    case "pause": {
      if (clock == null) throw conflict("This round has not started yet");
      if (clock.pausedAtMs != null) return clock;
      const at = Math.max(clock.startedAtMs, actionTime(nowMs, action.agoMs, MAX_ACTION_DELAY_MS));
      return { ...clock, pausedAtMs: at };
    }
    case "resume": {
      if (clock == null) throw conflict("This round has not started yet");
      if (clock.pausedAtMs == null) return clock;
      const at = Math.max(clock.pausedAtMs, actionTime(nowMs, action.agoMs, MAX_ACTION_DELAY_MS));
      return { ...clock, pausedAtMs: null, pausedMs: clock.pausedMs + (at - clock.pausedAtMs) };
    }
    case "reset":
      return null;
    case "setRemaining": {
      if (clock == null) throw conflict("This round has not started yet");
      if (!Number.isFinite(action.remainingMs) || action.remainingMs < 0 || action.remainingMs > MAX_REMAINING_MS) {
        throw badRequest("The remaining time must be from 0 to 12 hours");
      }
      const remaining = Math.round(action.remainingMs);
      return { ...clock, adjustMs: clockElapsedMs(clock, nowMs) + remaining - durationMinutes * 60_000 };
    }
  }
}

/** Reads the action of a request body. */
export function parseRoundClockAction(body: unknown): RoundClockAction {
  const data = body != null && typeof body === "object" ? body as Data : {};
  const seconds = (field: string): number => {
    const value = data[field];
    if (value == null) return 0;
    const number = Number(value);
    if (!Number.isFinite(number) || number < 0) throw badRequest(`${field} must be zero or more seconds`);
    return number * 1000;
  };
  switch (data.action) {
    case "start":
      return { kind: "start", agoMs: seconds("agoSeconds") };
    case "pause":
      return { kind: "pause", agoMs: seconds("agoSeconds") };
    case "resume":
      return { kind: "resume", agoMs: seconds("agoSeconds") };
    case "reset":
      return { kind: "reset" };
    case "setRemaining": {
      if (data.remainingSeconds == null) throw badRequest("remainingSeconds is required");
      return { kind: "setRemaining", remainingMs: seconds("remainingSeconds") };
    }
    default:
      throw badRequest("action must be start, pause, resume, reset or setRemaining");
  }
}
