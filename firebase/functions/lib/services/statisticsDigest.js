"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DIGEST_VERSION = void 0;
exports.handRunOf = handRunOf;
exports.buildTableDigest = buildTableDigest;
const handScoring_1 = require("./handScoring");
/** Change this number when the digest rules change. A stored table digest with another number counts as old. */
exports.DIGEST_VERSION = 4;
const text = (value) => String(value ?? "").trim();
function penaltyOf(value) {
    return text(value).split(";").reduce((sum, part) => {
        const trimmed = part.trim();
        return sum + (/^-?\d+$/.test(trimmed) ? Number(trimmed) : 0);
    }, 0);
}
/** The biggest single penalty of a penalty text, as a positive number. Only negative values are penalties. */
function worstPenaltyOf(value) {
    return text(value).split(";").reduce((worst, part) => {
        const trimmed = part.trim();
        return /^-\d+$/.test(trimmed) ? Math.max(worst, -Number(trimmed)) : worst;
    }, 0);
}
function pointsOf(value) {
    const parsed = Number(text(value).replace(",", "."));
    return Number.isFinite(parsed) && text(value) !== "" ? parsed : 0;
}
function intOf(value) {
    const trimmed = text(value);
    return /^-?\d+$/.test(trimmed) ? Number(trimmed) : null;
}
function handRunOf(flags) {
    let best = 0;
    let current = 0;
    for (const flag of flags) {
        current = flag ? current + 1 : 0;
        if (current > best)
            best = current;
    }
    let start = 0;
    while (start < flags.length && flags[start])
        start += 1;
    let end = 0;
    while (end < flags.length && flags[flags.length - 1 - end])
        end += 1;
    return { best, start, end };
}
/**
 * Builds the digest of one table. The rules match `CalculateStatisticsUseCase` of the domain module:
 * only a completed table gives results, and a hand counts when the hand scoring accepts it.
 * [emaBySlot] gives the EMA number of every slot of the tournament.
 */
function buildTableDigest(params) {
    const { table, hands, emaBySlot } = params;
    const roundId = Number(table.roundId);
    const tableId = Number(table.tableId);
    const isCompleted = Boolean(table.isCompleted);
    const summary = {
        roundId, tableId, isCompleted, hasHands: false, handsPlayed: 0, drawnHands: 0,
        winningHands: 0, selfDraws: 0, winningScoreSum: 0, bestHand: 0, chickenHands: 0,
    };
    const results = [];
    const seatTexts = (0, handScoring_1.seatIdsOf)(table);
    const seatIds = seatTexts?.map(intOf) ?? null;
    const scores = [table.playerEastScore, table.playerSouthScore, table.playerWestScore, table.playerNorthScore].map(intOf);
    if (!isCompleted || seatTexts == null || seatIds == null || seatIds.some((id) => id == null) || scores.some((s) => s == null)) {
        return { summary, results };
    }
    const ids = seatIds;
    const finalScores = scores;
    // The table points come only from the scores of the table.
    const tablePoints = (0, handScoring_1.tablePointsOf)(table);
    const points = [tablePoints.playerEastPoints, tablePoints.playerSouthPoints, tablePoints.playerWestPoints, tablePoints.playerNorthPoints].map(pointsOf);
    const counted = [];
    if (!Boolean(table.useTotalsOnly)) {
        const sorted = [...hands].sort((a, b) => Number(a.handId) - Number(b.handId));
        for (const hand of sorted) {
            const change = (0, handScoring_1.handSeatScores)(hand, seatTexts);
            if (change != null)
                counted.push({ hand, change });
        }
    }
    const hasHands = counted.length > 0;
    summary.hasHands = hasHands;
    summary.handsPlayed = counted.length;
    const zeros = () => [0, 0, 0, 0];
    const won = zeros(), selfDraws = zeros(), dealIns = zeros();
    const winSum = zeros(), best = zeros(), chicken = zeros(), dealInSum = zeros();
    const lostToSelfDraws = zeros(), otherWins = zeros();
    const penaltyCount = zeros(), penaltyPoints = zeros(), maxPenalty = zeros();
    const matrix = () => [zeros(), zeros(), zeros(), zeros()];
    const discardLosses = matrix(), selfDrawLosses = matrix(), othersDiscardWins = matrix();
    const wonFlags = [[], [], [], []];
    const noDealInFlags = [[], [], [], []];
    const opponentKey = (slotId) => emaBySlot.get(slotId) || `${params.tournamentId}#${slotId}`;
    let drawn = 0;
    for (const { hand, change } of counted) {
        [hand.playerEastPenalty, hand.playerSouthPenalty, hand.playerWestPenalty, hand.playerNorthPenalty]
            .forEach((entry, seat) => { maxPenalty[seat] = Math.max(maxPenalty[seat], worstPenaltyOf(entry)); });
        [hand.playerEastPenalty, hand.playerSouthPenalty, hand.playerWestPenalty, hand.playerNorthPenalty]
            .map(penaltyOf)
            .forEach((penalty, seat) => {
            if (penalty < 0) {
                penaltyCount[seat] += 1;
                penaltyPoints[seat] += -penalty;
            }
        });
        const winnerText = text(hand.playerWinnerId);
        const handLoserText = text(hand.playerLooserId) === "-" ? "" : text(hand.playerLooserId);
        for (let seat = 0; seat < 4; seat += 1) {
            wonFlags[seat].push(winnerText !== "" && seatTexts.indexOf(winnerText) === seat);
            noDealInFlags[seat].push(handLoserText === "" || seatTexts.indexOf(handLoserText) !== seat);
        }
        if (winnerText === "") {
            drawn += 1;
            continue;
        }
        const winner = seatTexts.indexOf(winnerText);
        const score = Number(text(hand.handScore));
        const loserText = handLoserText;
        won[winner] += 1;
        winSum[winner] += score;
        if (score > best[winner])
            best[winner] = score;
        summary.winningHands += 1;
        summary.winningScoreSum += score;
        if (score > summary.bestHand)
            summary.bestHand = score;
        if (Boolean(hand.isChickenHand)) {
            chicken[winner] += 1;
            summary.chickenHands += 1;
        }
        if (loserText === "") {
            selfDraws[winner] += 1;
            summary.selfDraws += 1;
        }
        else {
            const loser = seatTexts.indexOf(loserText);
            dealIns[loser] += 1;
            dealInSum[loser] += score;
        }
        const loserSeat = loserText === "" ? -1 : seatTexts.indexOf(loserText);
        for (let seat = 0; seat < 4; seat += 1) {
            if (seat === winner || seat === loserSeat)
                continue;
            if (loserSeat === -1)
                lostToSelfDraws[seat] += 1;
            else
                otherWins[seat] += 1;
        }
        for (let seat = 0; seat < 4; seat += 1) {
            if (seat === winner)
                continue;
            if (loserSeat === -1)
                selfDrawLosses[seat][winner] += 1;
            else if (seat === loserSeat)
                discardLosses[seat][winner] += 1;
            else
                othersDiscardWins[seat][winner] += 1;
        }
    }
    summary.drawnHands = drawn;
    for (let seat = 0; seat < 4; seat += 1) {
        results.push({
            emaId: emaBySlot.get(ids[seat]) ?? "",
            slotId: ids[seat],
            roundId,
            tableId,
            seat,
            position: 1 + finalScores.filter((score) => score > finalScores[seat]).length,
            score: finalScores[seat],
            points: points[seat],
            hasHands,
            handsPlayed: hasHands ? counted.length : 0,
            handsWon: won[seat],
            selfDraws: selfDraws[seat],
            dealIns: dealIns[seat],
            drawnHands: drawn,
            winningScoreSum: winSum[seat],
            bestHand: best[seat],
            chickenHands: chicken[seat],
            dealInScoreSum: dealInSum[seat],
            lostToSelfDraws: lostToSelfDraws[seat],
            otherWins: otherWins[seat],
            penaltyCount: penaltyCount[seat],
            penaltyPoints: penaltyPoints[seat],
            maxPenalty: maxPenalty[seat],
            rivals: [0, 1, 2, 3].filter((other) => other !== seat).map((other) => ({
                opponent: opponentKey(ids[other]),
                tablesBehind: finalScores[other] > finalScores[seat] ? 1 : 0,
                discardLosses: discardLosses[seat][other],
                selfDrawLosses: selfDrawLosses[seat][other],
                othersDiscardWins: othersDiscardWins[seat][other],
            })),
            winRun: hasHands ? handRunOf(wonFlags[seat]) : { best: 0, start: 0, end: 0 },
            noDealInRun: hasHands ? handRunOf(noDealInFlags[seat]) : { best: 0, start: 0, end: 0 },
        });
    }
    return { summary, results };
}
//# sourceMappingURL=statisticsDigest.js.map