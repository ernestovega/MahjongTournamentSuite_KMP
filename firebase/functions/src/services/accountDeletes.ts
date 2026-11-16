import { db } from "../firebase";
import { badRequest } from "../api/httpError";
import { countEnabledAdmins, deleteManagedUser, getManagedUser } from "./usersService";
import { setAccountEmaId } from "./playerAccountsService";

export type AccountDeleteFailure = "NOT_FOUND" | "SELF" | "LAST_ADMIN";

/**
 * Deletes one account for good, like the single delete of the Users screen: the account frees its EMA number first,
 * and the EMA player and the tournament results stay. Returns why it did not delete the account, or null.
 * The own account and the last enabled admin are never deleted.
 */
export async function deleteAccountChecked(actorUid: string, uid: string): Promise<AccountDeleteFailure | null> {
  if (uid === actorUid) return "SELF";
  const current = await getManagedUser(uid).catch((error: { status?: number; code?: string }) => {
    if (error.status === 404 || error.code === "auth/user-not-found") return null;
    throw error;
  });
  if (current == null) return "NOT_FOUND";
  if (current.role === "ADMIN" && !current.disabled && await countEnabledAdmins() <= 1) return "LAST_ADMIN";
  await setAccountEmaId({ uid: current.uid, emaId: null });
  await deleteManagedUser(current.uid);
  return null;
}

export type PlayerAccountDeleteFailure = AccountDeleteFailure | "NO_ACCOUNT" | "UNKNOWN";
export type PlayerAccountDeleteResult = { emaId: string; outcome: "DELETED" | "FAILED"; reason?: PlayerAccountDeleteFailure };

/** The server deletes the accounts of up to 50 EMA players in one request. */
export const MAX_PLAYER_ACCOUNT_DELETES = 50;

/**
 * Deletes the account that each EMA player has, from the Player base. The EMA player itself is never deleted: the EMA
 * owns those records, and the tournament results need them. Every player has its own result.
 */
export async function deleteAccountsOfPlayers(actorUid: string, emaIds: string[]): Promise<PlayerAccountDeleteResult[]> {
  const unique = [...new Set(emaIds)];
  if (unique.length === 0) throw badRequest("Choose at least one player");
  if (unique.length > MAX_PLAYER_ACCOUNT_DELETES) throw badRequest(`At most ${MAX_PLAYER_ACCOUNT_DELETES} players per request`);
  const results: PlayerAccountDeleteResult[] = [];
  // One after another, so the check of the last admin sees the accounts that this request already deleted.
  for (const emaId of unique) {
    const fail = (reason: PlayerAccountDeleteFailure): PlayerAccountDeleteResult => ({ emaId, outcome: "FAILED", reason });
    try {
      const owner = (await db.doc(`emaIdIndex/${emaId}`).get()).get("uid");
      if (typeof owner !== "string" || owner === "") {
        results.push(fail("NO_ACCOUNT"));
        continue;
      }
      const failure = await deleteAccountChecked(actorUid, owner);
      // An index entry whose account is gone is an account that no longer exists.
      results.push(failure == null ? { emaId, outcome: "DELETED" } : fail(failure === "NOT_FOUND" ? "NO_ACCOUNT" : failure));
    } catch (error) {
      console.error("Account delete of an EMA player failed", { emaId, error });
      results.push(fail("UNKNOWN"));
    }
  }
  return results;
}
