/**
 * Hand scoring rules. They match the table editor of the apps (`TableEditorState`, `HandDraftState`).
 * The server needs them because a hand confirmed by players must update the table totals without an editor.
 */

export const MIN_HAND_SCORE = 8;
export const MAX_CHICKEN_HAND_SCORE = 12;
const LOSER_COUNT = 3;

export type SeatValues = [number, number, number, number];
export type SeatTexts = [string, string, string, string];
type Data = Record<string, unknown>;

const text = (value: unknown) => String(value ?? "").trim();
const SEAT_FIELDS = ["playerEastId", "playerSouthId", "playerWestId", "playerNorthId"] as const;

/** The ids of the four seats, or null when a seat is empty or two seats share an id. Order: east, south, west, north. */
export function seatIdsOf(table: Data): [string, string, string, string] | null {
  const ids = SEAT_FIELDS.map((field) => text(table[field]));
  if (ids.some((id) => id.length === 0) || new Set(ids).size !== 4) return null;
  return ids as [string, string, string, string];
}

/** A loser of "-" or an empty text both mean a self-drawn hand. */
function normalizedLoser(hand: Data): string {
  const loser = text(hand.playerLooserId);
  return loser === "-" ? "" : loser;
}

/** True when the hand has result data that cannot count. A hand with no data at all counts as empty, not invalid. */
export function isResultSelectionInvalid(hand: Data): boolean {
  const winner = text(hand.playerWinnerId);
  const loser = normalizedLoser(hand);
  const score = text(hand.handScore);
  const isChicken = Boolean(hand.isChickenHand);
  if (winner === "" && loser === "" && score === "") return isChicken;
  if (winner === "" && loser === "" && score === "0") return isChicken;
  if (winner === "" && loser !== "") return true;
  if (winner === "" || score === "") return true;
  if (!/^-?\d+$/.test(score)) return true;
  const parsed = Number(score);
  if (parsed < MIN_HAND_SCORE) return true;
  if (isChicken && parsed > MAX_CHICKEN_HAND_SCORE) return true;
  return false;
}

function isCompletelyEmpty(hand: Data): boolean {
  return text(hand.playerWinnerId) === "" && normalizedLoser(hand) === "" && text(hand.handScore) === ""
    && !hand.isChickenHand;
}

function penalty(value: unknown): number {
  return text(value).split(";").reduce((sum, part) => {
    const parsed = /^-?\d+$/.test(part.trim()) ? Number(part.trim()) : 0;
    return sum + parsed;
  }, 0);
}

/** The score change of every seat for one hand, or null when the hand does not count. */
export function handSeatScores(hand: Data, seats: [string, string, string, string]): SeatValues | null {
  if (isResultSelectionInvalid(hand) || isCompletelyEmpty(hand)) return null;
  const score = text(hand.handScore);
  const winner = text(hand.playerWinnerId);
  const loser = normalizedLoser(hand);

  let base: SeatValues;
  if (winner === "" && loser === "" && score === "0") {
    base = [0, 0, 0, 0];
  } else if (winner === "") {
    return null;
  } else if (loser === "") {
    const handScore = Number(score);
    const winnerPoints = (handScore + MIN_HAND_SCORE) * LOSER_COUNT;
    const otherPoints = -(handScore + MIN_HAND_SCORE);
    if (!seats.includes(winner)) return null;
    base = seats.map((seat) => (seat === winner ? winnerPoints : otherPoints)) as SeatValues;
  } else {
    const handScore = Number(score);
    if (winner === loser || !seats.includes(winner) || !seats.includes(loser)) return null;
    base = seats.map((seat) => {
      if (seat === winner) return handScore + MIN_HAND_SCORE * LOSER_COUNT;
      if (seat === loser) return -(handScore + MIN_HAND_SCORE);
      return -MIN_HAND_SCORE;
    }) as SeatValues;
  }
  const penalties = [hand.playerEastPenalty, hand.playerSouthPenalty, hand.playerWestPenalty, hand.playerNorthPenalty]
    .map(penalty);
  return base.map((value, index) => value + penalties[index]) as SeatValues;
}

/** Total score of every seat over the hands that count, or null when no hand counts. */
export function handScoreTotals(table: Data, hands: Data[]): SeatValues | null {
  const seats = seatIdsOf(table);
  if (seats == null) return null;
  const isCompleted = Boolean(table.isCompleted);
  let totals: SeatValues = [0, 0, 0, 0];
  let counted = false;
  for (const hand of hands) {
    if (!hand.isDone && !isCompleted) continue;
    const scores = handSeatScores(hand, seats);
    if (scores == null) continue;
    totals = totals.map((value, index) => value + scores[index]) as SeatValues;
    counted = true;
  }
  return counted ? totals : null;
}

/** Table points from the four seat scores. Ties share the points of their places. Null when a score is missing. */
export function pointsFromScores(scores: SeatTexts): SeatTexts | null {
  const numbers = scores.map((score) => (/^-?\d+$/.test(score.trim()) ? Number(score.trim()) : null));
  if (numbers.some((value) => value == null)) return null;
  const ranked = (numbers as number[]).map((score, seat) => ({ score, seat })).sort((a, b) => b.score - a.score);
  const [a, b, c, d] = ranked.map((entry) => entry.score);
  let assigned: string[];
  if (a === b && b === c && c === d) assigned = ["1,75", "1,75", "1,75", "1,75"];
  else if (a === b && b === c) assigned = ["2,33", "2,33", "2,33", "0"];
  else if (b === c && c === d) assigned = ["4", "1", "1", "1"];
  else if (a === b && c === d) assigned = ["3", "3", "0,5", "0,5"];
  else if (a === b) assigned = ["3", "3", "1", "0"];
  else if (b === c) assigned = ["4", "1,5", "1,5", "0"];
  else if (c === d) assigned = ["4", "2", "0,5", "0,5"];
  else assigned = ["4", "2", "1", "0"];
  const result: string[] = ["", "", "", ""];
  ranked.forEach((entry, index) => { result[entry.seat] = assigned[index]; });
  return result as SeatTexts;
}

/**
 * The table fields that the editor app persists after a hand changes in hands mode:
 * the effective scores and the points. Manual values are used when no hand counts.
 */
export function tableTotalsPatch(table: Data, hands: Data[]): Record<string, string> {
  const manualScores = [table.manualPlayerEastScore, table.manualPlayerSouthScore, table.manualPlayerWestScore, table.manualPlayerNorthScore]
    .map(text) as SeatTexts;
  const manualPoints = [table.manualPlayerEastPoints, table.manualPlayerSouthPoints, table.manualPlayerWestPoints, table.manualPlayerNorthPoints]
    .map(text) as SeatTexts;
  const calculated = handScoreTotals(table, hands);
  const scores: SeatTexts = calculated == null ? manualScores : calculated.map(String) as SeatTexts;
  const usePointsCalculation = Boolean(table.usePointsCalculation ?? true);
  const points = usePointsCalculation ? pointsFromScores(scores) ?? manualPoints : manualPoints;
  return {
    playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
    playerEastPoints: points[0], playerSouthPoints: points[1], playerWestPoints: points[2], playerNorthPoints: points[3],
  };
}
