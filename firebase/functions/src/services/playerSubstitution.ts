/**
 * The substitution of a player who played some rounds. The substitute is never recorded.
 * The slot keeps its person and its earlier results. By the EMA rules, the person gets no result
 * for the rounds that the substitute plays. A disqualified person gets 0 for the whole tournament.
 */
export const SUBSTITUTION_REASONS = ["LATE", "ILLNESS", "DISQUALIFIED"] as const;
export type SubstitutionReason = (typeof SUBSTITUTION_REASONS)[number];

export type PlayerSubstitution = {
  reason: SubstitutionReason;
  /** The first round that the substitute plays. */
  fromRound: number;
  /** True when the player leaves after the session has started. The substitute then gets 0 table points in that round. */
  duringSession: boolean;
  /**
   * The head referee excluded the player. The EMA report and the statistics leave out the player.
   * The player stays in the ranking, struck through. Only for illness.
   */
  excludedFromEma: boolean;
};

/** Reads the stored map of a slot. Returns null when the slot has no valid substitution. */
export function readSubstitution(value: unknown): PlayerSubstitution | null {
  if (value == null || typeof value !== "object") return null;
  const data = value as Record<string, unknown>;
  const reason = SUBSTITUTION_REASONS.find((candidate) => candidate === data.reason);
  const fromRound = Number(data.fromRound);
  if (reason == null || !Number.isInteger(fromRound) || fromRound <= 0) return null;
  return {
    reason,
    fromRound,
    duringSession: data.duringSession === true,
    excludedFromEma: data.excludedFromEma === true,
  };
}

/** True when the results of the slot in this round do not count for the slot. */
export function slotResultIgnored(substitution: PlayerSubstitution | null, roundId: number): boolean {
  if (substitution == null) return false;
  return substitution.reason === "DISQUALIFIED" || roundId >= substitution.fromRound;
}

/** True when the substitute plays this round and so the person has no personal statistics from it. */
export function substitutePlays(substitution: PlayerSubstitution | null, roundId: number): boolean {
  return substitution != null && roundId >= substitution.fromRound;
}
