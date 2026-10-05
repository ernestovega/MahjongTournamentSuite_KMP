"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.blocksAssignmentChangeAfterProgress = blocksAssignmentChangeAfterProgress;
exports.hasFourValidScores = hasFourValidScores;
exports.hasValidTablePoints = hasValidTablePoints;
exports.calculateTableCompletionStatus = calculateTableCompletionStatus;
exports.calculateBestHandScore = calculateBestHandScore;
exports.countChickenHands = countChickenHands;
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
/**
 * Empty: no data. Incomplete: data but no valid totals or missing seats. Partial: seats assigned
 * and valid manual totals. Completed: seats assigned, hands mode and valid calculated totals.
 * The hands mode needs no hands, because the totals can fall back to the manual scores.
 */
function calculateTableCompletionStatus(input) {
    if (!input.hasData)
        return "empty";
    const seatsAssigned = input.seatIds.length === 4
        && input.seatIds.every((id) => String(id ?? "").trim().length > 0);
    if (!seatsAssigned || !input.hasValidTotals)
        return "incomplete";
    return input.useTotalsOnly ? "partial" : "completed";
}
/** Highest integer score among the done hands, or null when no done hand has a score. */
function calculateBestHandScore(hands) {
    const scores = hands
        .filter((hand) => Boolean(hand.isDone))
        .map((hand) => String(hand.handScore ?? "").trim())
        .filter((score) => /^-?\d+$/.test(score))
        .map(Number);
    return scores.length > 0 ? Math.max(...scores) : null;
}
function countChickenHands(hands) {
    return hands.filter((hand) => Boolean(hand.isChickenHand)).length;
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