"use strict";
/**
 * Hand scoring rules. They match the table editor of the apps (`TableEditorState`, `HandDraftState`).
 * The server needs them because a hand confirmed by players must update the table totals without an editor.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_CHICKEN_HAND_SCORE = exports.MIN_HAND_SCORE = void 0;
exports.seatIdsOf = seatIdsOf;
exports.isResultSelectionInvalid = isResultSelectionInvalid;
exports.handSeatScores = handSeatScores;
exports.handScoreTotals = handScoreTotals;
exports.pointsFromScores = pointsFromScores;
exports.tablePointsOf = tablePointsOf;
exports.tableTotalsPatch = tableTotalsPatch;
exports.MIN_HAND_SCORE = 8;
exports.MAX_CHICKEN_HAND_SCORE = 12;
const LOSER_COUNT = 3;
const text = (value) => String(value ?? "").trim();
const SEAT_FIELDS = ["playerEastId", "playerSouthId", "playerWestId", "playerNorthId"];
/** The ids of the four seats, or null when a seat is empty or two seats share an id. Order: east, south, west, north. */
function seatIdsOf(table) {
    const ids = SEAT_FIELDS.map((field) => text(table[field]));
    if (ids.some((id) => id.length === 0) || new Set(ids).size !== 4)
        return null;
    return ids;
}
/** A loser of "-" or an empty text both mean a self-drawn hand. */
function normalizedLoser(hand) {
    const loser = text(hand.playerLooserId);
    return loser === "-" ? "" : loser;
}
/** True when the hand has result data that cannot count. A hand with no data at all counts as empty, not invalid. */
function isResultSelectionInvalid(hand) {
    const winner = text(hand.playerWinnerId);
    const loser = normalizedLoser(hand);
    const score = text(hand.handScore);
    const isChicken = Boolean(hand.isChickenHand);
    if (winner === "" && loser === "" && score === "")
        return isChicken;
    if (winner === "" && loser === "" && score === "0")
        return isChicken;
    if (winner === "" && loser !== "")
        return true;
    if (winner === "" || score === "")
        return true;
    if (!/^-?\d+$/.test(score))
        return true;
    const parsed = Number(score);
    if (parsed < exports.MIN_HAND_SCORE)
        return true;
    if (isChicken && parsed > exports.MAX_CHICKEN_HAND_SCORE)
        return true;
    return false;
}
function isCompletelyEmpty(hand) {
    return text(hand.playerWinnerId) === "" && normalizedLoser(hand) === "" && text(hand.handScore) === ""
        && !hand.isChickenHand;
}
function penalty(value) {
    return text(value).split(";").reduce((sum, part) => {
        const parsed = /^-?\d+$/.test(part.trim()) ? Number(part.trim()) : 0;
        return sum + parsed;
    }, 0);
}
/** The score change of every seat for one hand, or null when the hand does not count. */
function handSeatScores(hand, seats) {
    if (isResultSelectionInvalid(hand) || isCompletelyEmpty(hand))
        return null;
    const score = text(hand.handScore);
    const winner = text(hand.playerWinnerId);
    const loser = normalizedLoser(hand);
    let base;
    if (winner === "" && loser === "" && score === "0") {
        base = [0, 0, 0, 0];
    }
    else if (winner === "") {
        return null;
    }
    else if (loser === "") {
        const handScore = Number(score);
        const winnerPoints = (handScore + exports.MIN_HAND_SCORE) * LOSER_COUNT;
        const otherPoints = -(handScore + exports.MIN_HAND_SCORE);
        if (!seats.includes(winner))
            return null;
        base = seats.map((seat) => (seat === winner ? winnerPoints : otherPoints));
    }
    else {
        const handScore = Number(score);
        if (winner === loser || !seats.includes(winner) || !seats.includes(loser))
            return null;
        base = seats.map((seat) => {
            if (seat === winner)
                return handScore + exports.MIN_HAND_SCORE * LOSER_COUNT;
            if (seat === loser)
                return -(handScore + exports.MIN_HAND_SCORE);
            return -exports.MIN_HAND_SCORE;
        });
    }
    const penalties = [hand.playerEastPenalty, hand.playerSouthPenalty, hand.playerWestPenalty, hand.playerNorthPenalty]
        .map(penalty);
    return base.map((value, index) => value + penalties[index]);
}
/** Total score of every seat over the hands that count, or null when no hand counts. */
function handScoreTotals(table, hands) {
    const seats = seatIdsOf(table);
    if (seats == null)
        return null;
    const isCompleted = Boolean(table.isCompleted);
    let totals = [0, 0, 0, 0];
    let counted = false;
    for (const hand of hands) {
        if (!hand.isDone && !isCompleted)
            continue;
        const scores = handSeatScores(hand, seats);
        if (scores == null)
            continue;
        totals = totals.map((value, index) => value + scores[index]);
        counted = true;
    }
    return counted ? totals : null;
}
/** Table points from the four seat scores. Ties share the points of their places. Null when a score is missing. */
function pointsFromScores(scores) {
    const numbers = scores.map((score) => (/^-?\d+$/.test(score.trim()) ? Number(score.trim()) : null));
    if (numbers.some((value) => value == null))
        return null;
    const ranked = numbers.map((score, seat) => ({ score, seat })).sort((a, b) => b.score - a.score);
    const [a, b, c, d] = ranked.map((entry) => entry.score);
    let assigned;
    if (a === b && b === c && c === d)
        assigned = ["1,75", "1,75", "1,75", "1,75"];
    else if (a === b && b === c)
        assigned = ["2,33", "2,33", "2,33", "0"];
    else if (b === c && c === d)
        assigned = ["4", "1", "1", "1"];
    else if (a === b && c === d)
        assigned = ["3", "3", "0,5", "0,5"];
    else if (a === b)
        assigned = ["3", "3", "1", "0"];
    else if (b === c)
        assigned = ["4", "1,5", "1,5", "0"];
    else if (c === d)
        assigned = ["4", "2", "0,5", "0,5"];
    else
        assigned = ["4", "2", "1", "0"];
    const result = ["", "", "", ""];
    ranked.forEach((entry, index) => { result[entry.seat] = assigned[index]; });
    return result;
}
/**
 * The table points of a table. They come only from the scores of the table: the manual scores when the table
 * uses manual totals, otherwise the saved totals of the hands. A seat has an empty text while a score is missing.
 * Nobody types or forces table points.
 */
function tablePointsOf(table) {
    const useTotalsOnly = Boolean(table.useTotalsOnly ?? false);
    const score = (manual, saved) => (useTotalsOnly ? text(table[manual]) || text(table[saved]) : text(table[saved]));
    const scores = [
        score("manualPlayerEastScore", "playerEastScore"),
        score("manualPlayerSouthScore", "playerSouthScore"),
        score("manualPlayerWestScore", "playerWestScore"),
        score("manualPlayerNorthScore", "playerNorthScore"),
    ];
    const points = pointsFromScores(scores) ?? ["", "", "", ""];
    return { playerEastPoints: points[0], playerSouthPoints: points[1], playerWestPoints: points[2], playerNorthPoints: points[3] };
}
/**
 * The table fields that the editor app persists after a hand changes in hands mode:
 * the effective scores and the points. Manual values are used when no hand counts.
 */
function tableTotalsPatch(table, hands) {
    const manualScores = [table.manualPlayerEastScore, table.manualPlayerSouthScore, table.manualPlayerWestScore, table.manualPlayerNorthScore]
        .map(text);
    const calculated = handScoreTotals(table, hands);
    const scores = calculated == null ? manualScores : calculated.map(String);
    return {
        playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
        ...tablePointsOf({
            useTotalsOnly: false,
            playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
        }),
    };
}
//# sourceMappingURL=handScoring.js.map