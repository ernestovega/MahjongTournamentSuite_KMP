export type EmaIdProblem =
  /** Two accounts say they own the same EMA id. */
  | { code: "DUPLICATE_OWNER"; emaId: string; uids: string[] }
  /** The index names another account than the profile that holds the EMA id. */
  | { code: "INDEX_MISMATCH"; emaId: string; profileUid: string; indexUid: string | null }
  /** The index names an account whose profile does not hold the EMA id. */
  | { code: "ORPHAN_INDEX"; emaId: string; indexUid: string };

/**
 * Checks that one EMA id belongs to one account. The profile of the account and the index document
 * `emaIdIndex/{emaId}` must say the same. The slots of the tournaments find their account through this index.
 */
export function findEmaIdProblems(input: {
  profiles: Array<{ uid: string; emaId: string | null }>;
  index: Array<{ emaId: string; uid: string }>;
}): EmaIdProblem[] {
  const ownersByEmaId = new Map<string, string[]>();
  input.profiles.forEach((profile) => {
    if (profile.emaId) ownersByEmaId.set(profile.emaId, [...(ownersByEmaId.get(profile.emaId) ?? []), profile.uid]);
  });
  const indexByEmaId = new Map(input.index.map((entry) => [entry.emaId, entry.uid]));
  const problems: EmaIdProblem[] = [];
  ownersByEmaId.forEach((uids, emaId) => {
    if (uids.length > 1) problems.push({ code: "DUPLICATE_OWNER", emaId, uids });
    else if (indexByEmaId.get(emaId) !== uids[0]) {
      problems.push({ code: "INDEX_MISMATCH", emaId, profileUid: uids[0], indexUid: indexByEmaId.get(emaId) ?? null });
    }
  });
  indexByEmaId.forEach((uid, emaId) => {
    if (!ownersByEmaId.has(emaId)) problems.push({ code: "ORPHAN_INDEX", emaId, indexUid: uid });
  });
  return problems.sort((a, b) => a.emaId.localeCompare(b.emaId));
}

export type PlayerClaimProblem =
  /** The account has the player capability but owns no EMA id. */
  | { code: "PLAYER_WITHOUT_EMA_ID"; uid: string }
  /** The account owns an EMA id but has no player capability. */
  | { code: "EMA_ID_WITHOUT_PLAYER"; uid: string };

/** The player capability and the EMA id go together: an account has one only when it has the other. */
export function findPlayerClaimProblems(accounts: Array<{ uid: string; hasPlayerClaim: boolean; emaId: string | null }>): PlayerClaimProblem[] {
  return accounts.flatMap((account): PlayerClaimProblem[] => {
    if (account.hasPlayerClaim && !account.emaId) return [{ code: "PLAYER_WITHOUT_EMA_ID", uid: account.uid }];
    if (!account.hasPlayerClaim && account.emaId) return [{ code: "EMA_ID_WITHOUT_PLAYER", uid: account.uid }];
    return [];
  });
}
