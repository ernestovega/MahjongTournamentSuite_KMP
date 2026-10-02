"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.blocksAssignmentChangeAfterProgress = blocksAssignmentChangeAfterProgress;
exports.hasFourValidScores = hasFourValidScores;
exports.hasValidTablePoints = hasValidTablePoints;
exports.hasDuplicateEmaAssignments = hasDuplicateEmaAssignments;
function blocksAssignmentChangeAfterProgress(previousEmaId, nextEmaId, tournamentHasProgress) {
    return tournamentHasProgress && previousEmaId != null && previousEmaId !== nextEmaId;
}
function hasFourValidScores(values) {
    return hasFourValidNumbers(values, false);
}
function hasValidTablePoints(values) {
    if (!hasFourValidNumbers(values, true))
        return false;
    const total = values.reduce((sum, value) => sum + Number(String(value).replace(",", ".")), 0);
    return Math.abs(total - EXPECTED_TABLE_POINTS_TOTAL) < TABLE_POINTS_TOLERANCE;
}
function hasDuplicateEmaAssignments(values) {
    const assigned = values
        .map((value) => value?.trim() || null)
        .filter((value) => value != null);
    return new Set(assigned).size !== assigned.length;
}
function hasFourValidNumbers(values, allowDecimal) {
    const pattern = allowDecimal ? /^-?\d+(?:[,.]\d+)?$/ : /^-?\d+$/;
    return values.length === 4 && values.every((value) => pattern.test(String(value ?? "").trim()));
}
const EXPECTED_TABLE_POINTS_TOTAL = 7;
const TABLE_POINTS_TOLERANCE = 0.011;
//# sourceMappingURL=tournamentContentRules.js.map