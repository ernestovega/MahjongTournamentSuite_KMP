"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isValidIsoDate = isValidIsoDate;
exports.isValidIsoDateRange = isValidIsoDateRange;
function isValidIsoDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value))
        return false;
    const [year, month, day] = value.split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}
function isValidIsoDateRange(startDate, endDate) {
    return isValidIsoDate(startDate) && isValidIsoDate(endDate) && startDate <= endDate;
}
//# sourceMappingURL=tournamentDates.js.map