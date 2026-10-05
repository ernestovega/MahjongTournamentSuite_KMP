export function blocksAssignmentChangeAfterProgress(
  previousEmaId: string | null,
  nextEmaId: string | null,
  tournamentHasProgress: boolean,
): boolean {
  return tournamentHasProgress && previousEmaId != null && previousEmaId !== nextEmaId;
}

export function hasFourValidScores(values: unknown[]): boolean {
  return hasFourValidNumbers(values, false);
}

export function hasValidTablePoints(values: unknown[]): boolean {
  if (!hasFourValidNumbers(values, true)) return false;

  const total = values.reduce(
    (sum: number, value: unknown) => sum + Number(String(value).replace(",", ".")),
    0,
  );
  return Math.abs(total - EXPECTED_TABLE_POINTS_TOTAL) < TABLE_POINTS_TOLERANCE;
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

/** Highest integer score among the done hands, or null when no done hand has a score. */
export function calculateBestHandScore(hands: Array<Record<string, unknown>>): number | null {
  const scores = hands
    .filter((hand) => Boolean(hand.isDone))
    .map((hand) => String(hand.handScore ?? "").trim())
    .filter((score) => /^-?\d+$/.test(score))
    .map(Number);
  return scores.length > 0 ? Math.max(...scores) : null;
}

export function countChickenHands(hands: Array<Record<string, unknown>>): number {
  return hands.filter((hand) => Boolean(hand.isChickenHand)).length;
}

export function hasDuplicateEmaAssignments(values: Array<string | null | undefined>): boolean {
  const assigned = values
    .map((value) => value?.trim() || null)
    .filter((value): value is string => value != null);
  return new Set(assigned).size !== assigned.length;
}

function hasFourValidNumbers(values: unknown[], allowDecimal: boolean): boolean {
  const pattern = allowDecimal ? /^-?\d+(?:[,.]\d+)?$/ : /^-?\d+$/;
  return values.length === 4 && values.every((value) => pattern.test(String(value ?? "").trim()));
}

const EXPECTED_TABLE_POINTS_TOTAL = 7;
const TABLE_POINTS_TOLERANCE = 0.011;
