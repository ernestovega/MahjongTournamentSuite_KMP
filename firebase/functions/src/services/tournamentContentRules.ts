export function blocksAssignmentChangeAfterProgress(
  previousEmaId: string | null,
  nextEmaId: string | null,
  tournamentHasProgress: boolean,
): boolean {
  return tournamentHasProgress && previousEmaId != null && previousEmaId !== nextEmaId;
}

export function hasFourValidScores(values: unknown[]): boolean {
  return hasFourValidNumbers(values);
}

export type TableCompletionStatus = "empty" | "incomplete" | "partial" | "completed";

/**
 * Empty: no data. Incomplete: data but no valid totals or missing seats. Partial: seats assigned
 * and valid manual totals. Completed: seats assigned, hands mode and valid calculated totals.
 * The hands mode needs no hands, because the totals can fall back to the manual scores.
 */
export function calculateTableCompletionStatus(input: {
  hasData: boolean;
  seatIds: unknown[];
  useTotalsOnly: boolean;
  hasValidTotals: boolean;
}): TableCompletionStatus {
  if (!input.hasData) return "empty";
  const seatsAssigned = input.seatIds.length === 4
    && input.seatIds.every((id) => String(id ?? "").trim().length > 0);
  if (!seatsAssigned || !input.hasValidTotals) return "incomplete";
  return input.useTotalsOnly ? "partial" : "completed";
}

export const MAX_BEST_HANDS = 3;

/**
 * Scores of the best done hands, from highest to lowest. It keeps the three highest scores
 * and every score tied with the third one, so a tie never hides a hand.
 */
export function calculateBestHandScores(hands: Array<Record<string, unknown>>): number[] {
  const scores = hands
    .filter((hand) => Boolean(hand.isDone))
    .map((hand) => String(hand.handScore ?? "").trim())
    .filter((score) => /^-?\d+$/.test(score))
    .map(Number)
    .sort((a, b) => b - a);
  if (scores.length <= MAX_BEST_HANDS) return scores;
  const threshold = scores[MAX_BEST_HANDS - 1];
  return scores.filter((score) => score >= threshold);
}

export function countChickenHands(hands: Array<Record<string, unknown>>): number {
  return hands.filter((hand) => Boolean(hand.isChickenHand)).length;
}

/** Chicken hand count and best hand scores of a table. Only the hands give the values. */
export function calculateHandSummary(
  hands: Array<Record<string, unknown>>,
): { chickenHandCount: number; bestHandScores: number[] } {
  return { chickenHandCount: countChickenHands(hands), bestHandScores: calculateBestHandScores(hands) };
}

export function hasDuplicateEmaAssignments(values: Array<string | null | undefined>): boolean {
  const assigned = values
    .map((value) => value?.trim() || null)
    .filter((value): value is string => value != null);
  return new Set(assigned).size !== assigned.length;
}

function hasFourValidNumbers(values: unknown[]): boolean {
  return values.length === 4 && values.every((value) => /^-?\d+$/.test(String(value ?? "").trim()));
}

/** A tournament has at least 16 players, always in groups of 4 (one table). */
export const MIN_TOURNAMENT_PLAYERS = 16;

export function isValidPlayerCount(value: unknown): boolean {
  return typeof value === "number" && Number.isInteger(value) && value >= MIN_TOURNAMENT_PLAYERS && value % 4 === 0;
}
