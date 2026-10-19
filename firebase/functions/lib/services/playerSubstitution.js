"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SUBSTITUTION_REASONS = void 0;
exports.readSubstitution = readSubstitution;
exports.slotResultIgnored = slotResultIgnored;
exports.substitutePlays = substitutePlays;
/**
 * The substitution of a player who played some rounds. The substitute is never recorded.
 * The slot keeps its person and its earlier results. By the EMA rules, the person gets no result
 * for the rounds that the substitute plays. A disqualified person gets 0 for the whole tournament.
 */
exports.SUBSTITUTION_REASONS = ["LATE", "ILLNESS", "DISQUALIFIED"];
/** Reads the stored map of a slot. Returns null when the slot has no valid substitution. */
function readSubstitution(value) {
    if (value == null || typeof value !== "object")
        return null;
    const data = value;
    const reason = exports.SUBSTITUTION_REASONS.find((candidate) => candidate === data.reason);
    const fromRound = Number(data.fromRound);
    if (reason == null || !Number.isInteger(fromRound) || fromRound <= 0)
        return null;
    return {
        reason,
        fromRound,
        duringSession: data.duringSession === true,
        excludedFromEma: data.excludedFromEma === true,
    };
}
/** True when the results of the slot in this round do not count for the slot. */
function slotResultIgnored(substitution, roundId) {
    if (substitution == null)
        return false;
    return substitution.reason === "DISQUALIFIED" || roundId >= substitution.fromRound;
}
/** True when the substitute plays this round and so the person has no personal statistics from it. */
function substitutePlays(substitution, roundId) {
    return substitution != null && roundId >= substitution.fromRound;
}
//# sourceMappingURL=playerSubstitution.js.map