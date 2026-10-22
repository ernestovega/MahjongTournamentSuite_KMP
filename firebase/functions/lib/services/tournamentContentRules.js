"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MIN_TOURNAMENT_PLAYERS = exports.MAX_BEST_HANDS = void 0;
exports.blocksAssignmentChangeAfterProgress = blocksAssignmentChangeAfterProgress;
exports.hasFourValidScores = hasFourValidScores;
exports.calculateTableCompletionStatus = calculateTableCompletionStatus;
exports.calculateBestHandScores = calculateBestHandScores;
exports.countChickenHands = countChickenHands;
exports.calculateHandSummary = calculateHandSummary;
exports.hasDuplicateEmaAssignments = hasDuplicateEmaAssignments;
exports.isValidPlayerCount = isValidPlayerCount;
function blocksAssignmentChangeAfterProgress(previousEmaId, nextEmaId, tournamentHasProgress) {
    return tournamentHasProgress && previousEmaId != null && previousEmaId !== nextEmaId;
}
function hasFourValidScores(values) {
    return hasFourValidNumbers(values);
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
exports.MAX_BEST_HANDS = 3;
/**
 * Scores of the best done hands, from highest to lowest. It keeps the three highest scores
 * and every score tied with the third one, so a tie never hides a hand.
 */
function calculateBestHandScores(hands) {
    const scores = hands
        .filter((hand) => Boolean(hand.isDone))
        .map((hand) => String(hand.handScore ?? "").trim())
        .filter((score) => /^-?\d+$/.test(score))
        .map(Number)
        .sort((a, b) => b - a);
    if (scores.length <= exports.MAX_BEST_HANDS)
        return scores;
    const threshold = scores[exports.MAX_BEST_HANDS - 1];
    return scores.filter((score) => score >= threshold);
}
function countChickenHands(hands) {
    return hands.filter((hand) => Boolean(hand.isChickenHand)).length;
}
/** Chicken hand count and best hand scores of a table. Only the hands give the values. */
function calculateHandSummary(hands) {
    return { chickenHandCount: countChickenHands(hands), bestHandScores: calculateBestHandScores(hands) };
}
function hasDuplicateEmaAssignments(values) {
    const assigned = values
        .map((value) => value?.trim() || null)
        .filter((value) => value != null);
    return new Set(assigned).size !== assigned.length;
}
function hasFourValidNumbers(values) {
    return values.length === 4 && values.every((value) => /^-?\d+$/.test(String(value ?? "").trim()));
}
/** A tournament has at least 16 players, always in groups of 4 (one table). */
exports.MIN_TOURNAMENT_PLAYERS = 16;
function isValidPlayerCount(value) {
    return typeof value === "number" && Number.isInteger(value) && value >= exports.MIN_TOURNAMENT_PLAYERS && value % 4 === 0;
}
//# sourceMappingURL=tournamentContentRules.js.map