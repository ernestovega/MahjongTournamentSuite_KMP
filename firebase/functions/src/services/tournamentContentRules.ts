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
