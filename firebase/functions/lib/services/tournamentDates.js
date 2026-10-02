"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isValidIsoDate = isValidIsoDate;
exports.isValidIsoDateRange = isValidIsoDateRange;
exports.inclusiveDayCount = inclusiveDayCount;
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
function inclusiveDayCount(startDate, endDate) {
    if (!isValidIsoDate(startDate) || !isValidIsoDate(endDate))
        return 0;
    const start = Date.UTC(Number(startDate.slice(0, 4)), Number(startDate.slice(5, 7)) - 1, Number(startDate.slice(8, 10)));
    const end = Date.UTC(Number(endDate.slice(0, 4)), Number(endDate.slice(5, 7)) - 1, Number(endDate.slice(8, 10)));
    return Math.max(0, Math.round((end - start) / 86400000) + 1);
}
//# sourceMappingURL=tournamentDates.js.map