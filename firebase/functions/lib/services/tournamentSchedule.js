"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizeRoundSchedules = normalizeRoundSchedules;
exports.normalizeAgendaItems = normalizeAgendaItems;
exports.readStoredRoundSchedules = readStoredRoundSchedules;
exports.readStoredAgendaItems = readStoredAgendaItems;
const httpError_1 = require("../api/httpError");
const tournamentDates_1 = require("./tournamentDates");
const LOCAL_TIME_PATTERN = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
function optionalString(value) {
    if (value == null)
        return null;
    const normalized = String(value).trim();
    return normalized.length > 0 ? normalized : null;
}
function normalizedDate(value, field, startDate, endDate) {
    const date = optionalString(value);
    if (date == null)
        return null;
    if (!(0, tournamentDates_1.isValidIsoDate)(date))
        throw (0, httpError_1.badRequest)(`${field} must use yyyy-MM-dd`);
    if (date < startDate || date > endDate)
        throw (0, httpError_1.badRequest)(`${field} must be inside the tournament date range`);
    return date;
}
function normalizedTime(value, field) {
    const time = optionalString(value);
    if (time == null)
        return null;
    if (!LOCAL_TIME_PATTERN.test(time))
        throw (0, httpError_1.badRequest)(`${field} must use HH:mm`);
    return time;
}
function normalizeRoundSchedules(value, numRounds, eventStartDate, eventEndDate) {
    const rows = value == null ? [] : value;
    if (!Array.isArray(rows))
        throw (0, httpError_1.badRequest)("roundSchedules must be an array");
    if (rows.length > numRounds)
        throw (0, httpError_1.badRequest)("roundSchedules contains too many rows");
    const byRound = new Map();
    rows.forEach((value, index) => {
        if (value == null || typeof value !== "object") {
            throw (0, httpError_1.badRequest)("Invalid round schedule", { index });
        }
        const row = value;
        const roundId = Number(row.roundId);
        if (!Number.isInteger(roundId) || roundId < 1 || roundId > numRounds) {
            throw (0, httpError_1.badRequest)("Invalid round schedule id", { index, value: row.roundId });
        }
        if (byRound.has(roundId))
            throw (0, httpError_1.badRequest)("Round schedule ids must be unique", { roundId });
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
function normalizeAgendaItems(value, eventStartDate, eventEndDate) {
    const rows = value == null ? [] : value;
    if (!Array.isArray(rows))
        throw (0, httpError_1.badRequest)("agendaItems must be an array");
    if (rows.length > 100)
        throw (0, httpError_1.badRequest)("agendaItems must contain at most 100 rows");
    return rows.flatMap((value, index) => {
        if (value == null || typeof value !== "object") {
            throw (0, httpError_1.badRequest)("Invalid agenda item", { index });
        }
        const row = value;
        const title = String(row.title ?? "").trim();
        if (title.length === 0)
            return [];
        if (title.length > 120)
            throw (0, httpError_1.badRequest)(`agendaItems[${index}].title must contain at most 120 characters`);
        const startTime = normalizedTime(row.startTime, `agendaItems[${index}].startTime`);
        const endTime = normalizedTime(row.endTime, `agendaItems[${index}].endTime`);
        if (startTime != null && endTime != null && endTime < startTime) {
            throw (0, httpError_1.badRequest)(`agendaItems[${index}].endTime must not be before its start time`);
        }
        return [{
                title,
                date: normalizedDate(row.date, `agendaItems[${index}].date`, eventStartDate, eventEndDate),
                startTime,
                endTime,
            }];
    });
}
function readStoredRoundSchedules(value, numRounds, eventStartDate, eventEndDate) {
    try {
        return normalizeRoundSchedules(value, numRounds, eventStartDate, eventEndDate);
    }
    catch (_error) {
        return Array.from({ length: numRounds }, (_, index) => ({
            roundId: index + 1,
            date: null,
            startTime: null,
        }));
    }
}
function readStoredAgendaItems(value, eventStartDate, eventEndDate) {
    try {
        return normalizeAgendaItems(value, eventStartDate, eventEndDate);
    }
    catch (_error) {
        return [];
    }
}
//# sourceMappingURL=tournamentSchedule.js.map