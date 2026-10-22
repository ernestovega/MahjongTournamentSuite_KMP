"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DEFAULT_MATCH_EXTRA_MINUTES = void 0;
exports.isValidTimeZone = isValidTimeZone;
exports.normalizeTimeZone = normalizeTimeZone;
exports.readStoredTimeZone = readStoredTimeZone;
exports.normalizeMatchExtraMinutes = normalizeMatchExtraMinutes;
exports.readStoredMatchExtraMinutes = readStoredMatchExtraMinutes;
exports.zonedEpochMs = zonedEpochMs;
exports.roundStartMs = roundStartMs;
exports.matchPhaseAt = matchPhaseAt;
exports.matchTimeOfTournament = matchTimeOfTournament;
exports.tournamentMatchPhase = tournamentMatchPhase;
const httpError_1 = require("../api/httpError");
const tournamentSchedule_1 = require("./tournamentSchedule");
/** Minutes after the end time in which the players can record the hand in play, with the mode FINISH_HAND. */
exports.DEFAULT_MATCH_EXTRA_MINUTES = 15;
const MAX_MATCH_EXTRA_MINUTES = 120;
function isValidTimeZone(value) {
    try {
        new Intl.DateTimeFormat("en-US", { timeZone: value });
        return true;
    }
    catch (_error) {
        return false;
    }
}
/** Validates an IANA time zone such as "Europe/Madrid". An empty value means that the tournament has none. */
function normalizeTimeZone(value) {
    if (value == null)
        return null;
    const text = String(value).trim();
    if (text === "")
        return null;
    if (!isValidTimeZone(text))
        throw (0, httpError_1.badRequest)("timeZone must be an IANA time zone, for example Europe/Madrid");
    return text;
}
/** Reads a stored time zone. An invalid value gives null. */
function readStoredTimeZone(value) {
    try {
        return normalizeTimeZone(value);
    }
    catch (_error) {
        return null;
    }
}
/** Validates the extra minutes of the mode FINISH_HAND. A missing value gives the default. */
function normalizeMatchExtraMinutes(value) {
    if (value == null)
        return exports.DEFAULT_MATCH_EXTRA_MINUTES;
    const minutes = Number(value);
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > MAX_MATCH_EXTRA_MINUTES) {
        throw (0, httpError_1.badRequest)(`matchExtraMinutes must be a whole number from 1 to ${MAX_MATCH_EXTRA_MINUTES}`);
    }
    return minutes;
}
function readStoredMatchExtraMinutes(value) {
    try {
        return normalizeMatchExtraMinutes(value);
    }
    catch (_error) {
        return exports.DEFAULT_MATCH_EXTRA_MINUTES;
    }
}
/** Offset of a time zone from UTC at an instant, in milliseconds. */
function zoneOffsetMs(epochMs, timeZone) {
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
    const value = (type) => Number(parts.find((part) => part.type === type)?.value);
    const asUtc = Date.UTC(value("year"), value("month") - 1, value("day"), value("hour"), value("minute"), value("second"));
    return asUtc - Math.floor(epochMs / 1000) * 1000;
}
/** The instant of a local date and time in a time zone. Null when the date or the time is not valid. */
function zonedEpochMs(date, time, timeZone) {
    const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
    const timeMatch = /^(\d{2}):(\d{2})$/.exec(time);
    if (!dateMatch || !timeMatch)
        return null;
    const guess = Date.UTC(Number(dateMatch[1]), Number(dateMatch[2]) - 1, Number(dateMatch[3]), Number(timeMatch[1]), Number(timeMatch[2]));
    // The offset can differ at the guess and at the result, near a change of summer time. Two passes are enough.
    const first = guess - zoneOffsetMs(guess, timeZone);
    return guess - zoneOffsetMs(first, timeZone);
}
/** Start of a round as an instant. Null when the tournament has no time zone or the round has no date. */
function roundStartMs(schedule, timeZone) {
    if (timeZone == null || schedule?.date == null || schedule.date === "")
        return null;
    return zonedEpochMs(schedule.date, schedule.startTime ?? "00:00", timeZone);
}
/**
 * The phase of the matches of a round at an instant.
 * Null when the server cannot know it: the tournament has no time zone or the round has no date.
 */
function matchPhaseAt(schedule, rules, nowMs) {
    const start = roundStartMs(schedule, rules.timeZone);
    if (start == null)
        return null;
    if (nowMs < start)
        return "NOT_STARTED";
    const end = start + rules.durationMinutes * 60000;
    if (nowMs < end)
        return "PLAYING";
    const extra = rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60000 : 0;
    return nowMs < end + extra ? "EXTRA_TIME" : "OVER";
}
/** The match time rules and the schedule of a round, read from a stored tournament document. */
function matchTimeOfTournament(tournament, roundId) {
    const schedules = Array.isArray(tournament.roundSchedules) ? tournament.roundSchedules : [];
    const row = schedules.find((item) => Number(item.roundId) === roundId);
    return {
        rules: {
            timeZone: readStoredTimeZone(tournament.timeZone),
            durationMinutes: (0, tournamentSchedule_1.readStoredMatchDuration)(tournament.matchDurationMinutes),
            endMode: (0, tournamentSchedule_1.readStoredMatchEndMode)(tournament.matchEndMode),
            extraMinutes: readStoredMatchExtraMinutes(tournament.matchExtraMinutes),
        },
        schedule: row == null
            ? undefined
            : {
                date: typeof row.date === "string" ? row.date : null,
                startTime: typeof row.startTime === "string" ? row.startTime : null,
            },
    };
}
/** The phase of the matches of a round. Null when the server cannot know it. */
function tournamentMatchPhase(tournament, roundId, nowMs) {
    const { rules, schedule } = matchTimeOfTournament(tournament, roundId);
    return matchPhaseAt(schedule, rules, nowMs);
}
//# sourceMappingURL=matchTime.js.map