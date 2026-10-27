import { handSeatScores, seatIdsOf, tablePointsOf, type SeatValues } from "./handScoring";

type Data = Record<string, unknown>;

/** The result of one player at one completed table. It matches `TableResult` of the domain module. */
export type DigestResult = {
  /** EMA number of the player. Empty when the slot has no registered player. */
  emaId: string;
  slotId: number;
  roundId: number;
  tableId: number;
  seat: number;
  position: number;
  score: number;
  points: number;
  /**
   * False when the result does not count for the slot: a substitute played the table, or the player is disqualified.
   * A result stored before this field existed counts.
   */
  countsForSlot?: boolean;
  hasHands: boolean;
  handsPlayed: number;
  handsWon: number;
  selfDraws: number;
  dealIns: number;
  drawnHands: number;
  winningScoreSum: number;
  bestHand: number;
  chickenHands: number;
  dealInScoreSum: number;
  lostToSelfDraws: number;
  otherWins: number;
  penaltyCount: number;
  penaltyPoints: number;
  /** The biggest single penalty that the player received at this table, as a positive number. */
  maxPenalty: number;
  /** What the three opponents did to this player at this table. */
  rivals: DigestRival[];
  winRun: HandRun;
  noDealInRun: HandRun;
};

/** The longest run of hands inside one table, with the run at the start and at the end of the table. */
export type HandRun = { best: number; start: number; end: number };

/** What one opponent did to a player at one table. Without an EMA number the opponent is `tournamentId#slotId`. */
export type DigestRival = {
  opponent: string;
  tablesBehind: number;
  discardLosses: number;
  selfDrawLosses: number;
  othersDiscardWins: number;
};

/** The totals of one table. Incomplete tables appear too, so the client can count them. */
export type DigestTable = {
  roundId: number;
  tableId: number;
  isCompleted: boolean;
  hasHands: boolean;
  handsPlayed: number;
  drawnHands: number;
  winningHands: number;
  /** Winning hands that the winner drew by themselves. */
  selfDraws: number;
  winningScoreSum: number;
  bestHand: number;
  chickenHands: number;
};

/** The digest of one table: the table totals and, for a completed table, one result for each player seat. */
export type TableDigest = {
  summary: DigestTable;
  /** Empty while the table is open or its seats and scores are not valid. */
  results: DigestResult[];
};

/** Change this number when the digest rules change. A stored table digest with another number counts as old. */
export const DIGEST_VERSION = 6;

const text = (value: unknown): string => String(value ?? "").trim();

function penaltyOf(value: unknown): number {
  return text(value).split(";").reduce((sum, part) => {
    const trimmed = part.trim();
    return sum + (/^-?\d+$/.test(trimmed) ? Number(trimmed) : 0);
  }, 0);
}

/** The biggest single penalty of a penalty text, as a positive number. Only negative values are penalties. */
function worstPenaltyOf(value: unknown): number {
  return text(value).split(";").reduce((worst, part) => {
    const trimmed = part.trim();
    return /^-\d+$/.test(trimmed) ? Math.max(worst, -Number(trimmed)) : worst;
  }, 0);
}

function pointsOf(value: unknown): number {
  const parsed = Number(text(value).replace(",", "."));
  return Number.isFinite(parsed) && text(value) !== "" ? parsed : 0;
}

function intOf(value: unknown): number | null {
  const trimmed = text(value);
  return /^-?\d+$/.test(trimmed) ? Number(trimmed) : null;
}

export function handRunOf(flags: boolean[]): HandRun {
  let best = 0;
  let current = 0;
  for (const flag of flags) {
    current = flag ? current + 1 : 0;
    if (current > best) best = current;
  }
  let start = 0;
  while (start < flags.length && flags[start]) start += 1;
  let end = 0;
  while (end < flags.length && flags[flags.length - 1 - end]) end += 1;
  return { best, start, end };
}

/**
 * Builds the digest of one table. The rules match `CalculateStatisticsUseCase` of the domain module:
 * only a completed table gives results, and a hand counts when the hand scoring accepts it.
 * [emaBySlot] gives the EMA number of every slot of the tournament.
 */
export function buildTableDigest(params: {
  tournamentId: string;
  table: Data;
  hands: Data[];
  emaBySlot: Map<number, string>;
  /** Slots whose result does not count for the slot in this table. See [PlayerSubstitution]. */
  uncountedSlots?: Set<number>;
  /** Slots whose table points and score are 0 in the statistics: disqualified players. */
  zeroedSlots?: Set<number>;
}): TableDigest {
  const { table, hands, emaBySlot } = params;
  const uncountedSlots = params.uncountedSlots ?? new Set<number>();
  const zeroedSlots = params.zeroedSlots ?? new Set<number>();
  const roundId = Number(table.roundId);
  const tableId = Number(table.tableId);
  const isCompleted = Boolean(table.isCompleted);
  const summary: DigestTable = {
    roundId, tableId, isCompleted, hasHands: false, handsPlayed: 0, drawnHands: 0,
    winningHands: 0, selfDraws: 0, winningScoreSum: 0, bestHand: 0, chickenHands: 0,
  };
  const results: DigestResult[] = [];

  const seatTexts = seatIdsOf(table);
  const seatIds = seatTexts?.map(intOf) ?? null;
  const scores = [table.playerEastScore, table.playerSouthScore, table.playerWestScore, table.playerNorthScore].map(intOf);
  if (!isCompleted || seatTexts == null || seatIds == null || seatIds.some((id) => id == null) || scores.some((s) => s == null)) {
    return { summary, results };
  }
  const ids = seatIds as number[];
  const finalScores = scores as number[];
  // The table points come only from the scores of the table.
  const tablePoints = tablePointsOf(table);
  const points = [tablePoints.playerEastPoints, tablePoints.playerSouthPoints, tablePoints.playerWestPoints, tablePoints.playerNorthPoints].map(pointsOf);

  const counted: Array<{ hand: Data; change: SeatValues }> = [];
  if (!Boolean(table.useTotalsOnly)) {
    const sorted = [...hands].sort((a, b) => Number(a.handId) - Number(b.handId));
    for (const hand of sorted) {
      const change = handSeatScores(hand, seatTexts);
      if (change != null) counted.push({ hand, change });
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
  const wonFlags: boolean[][] = [[], [], [], []];
  const noDealInFlags: boolean[][] = [[], [], [], []];
  const opponentKey = (slotId: number): string => emaBySlot.get(slotId) || `${params.tournamentId}#${slotId}`;
  let drawn = 0;

  for (const { hand } of counted) {
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
    if (score > best[winner]) best[winner] = score;
    summary.winningHands += 1;
    summary.winningScoreSum += score;
    if (score > summary.bestHand) summary.bestHand = score;
    if (Boolean(hand.isChickenHand)) {
      chicken[winner] += 1;
      summary.chickenHands += 1;
    }
    if (loserText === "") {
      selfDraws[winner] += 1;
      summary.selfDraws += 1;
    } else {
      const loser = seatTexts.indexOf(loserText);
      dealIns[loser] += 1;
      dealInSum[loser] += score;
    }
    const loserSeat = loserText === "" ? -1 : seatTexts.indexOf(loserText);
    for (let seat = 0; seat < 4; seat += 1) {
      if (seat === winner || seat === loserSeat) continue;
      if (loserSeat === -1) lostToSelfDraws[seat] += 1;
      else otherWins[seat] += 1;
    }
    for (let seat = 0; seat < 4; seat += 1) {
      if (seat === winner) continue;
      if (loserSeat === -1) selfDrawLosses[seat][winner] += 1;
      else if (seat === loserSeat) discardLosses[seat][winner] += 1;
      else othersDiscardWins[seat][winner] += 1;
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
      score: zeroedSlots.has(ids[seat]) ? 0 : finalScores[seat],
      points: zeroedSlots.has(ids[seat]) ? 0 : points[seat],
      countsForSlot: !uncountedSlots.has(ids[seat]),
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
