/**
 * What a script must do to make an account the owner of an EMA id, given what is stored now.
 * ALREADY: the profile and the index say it already. CLAIM: both are free. CONFLICT: another account or another
 * EMA id is involved, so the script must not write.
 */
export function ownershipAction(input: {
  uid: string;
  emaId: string;
  profileEmaId: string | null;
  indexUid: string | null;
}): "ALREADY" | "CLAIM" | "CONFLICT" {
  const profileFree = input.profileEmaId == null || input.profileEmaId === input.emaId;
  const indexFree = input.indexUid == null || input.indexUid === input.uid;
  if (!profileFree || !indexFree) return "CONFLICT";
  return input.profileEmaId === input.emaId && input.indexUid === input.uid ? "ALREADY" : "CLAIM";
}
