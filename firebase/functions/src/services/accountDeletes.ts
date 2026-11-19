import { countEnabledSuperAdmins, deleteManagedUser, getManagedUser } from "./usersService";
import { setAccountEmaId } from "./playerAccountsService";

export type AccountDeleteFailure = "NOT_FOUND" | "SELF" | "LAST_SUPER_ADMIN";

/**
 * Deletes one account for good, like the single delete of the Users screen: the account frees its EMA number first,
 * and the EMA player and the tournament results stay. Deleting an account never deletes its EMA player. Returns why it did not delete the account, or null.
 * The own account and the last enabled super admin are never deleted.
 */
export async function deleteAccountChecked(actorUid: string, uid: string): Promise<AccountDeleteFailure | null> {
  if (uid === actorUid) return "SELF";
  const current = await getManagedUser(uid).catch((error: { status?: number; code?: string }) => {
    if (error.status === 404 || error.code === "auth/user-not-found") return null;
    throw error;
  });
  if (current == null) return "NOT_FOUND";
  if (current.role === "SUPER_ADMIN" && !current.disabled && await countEnabledSuperAdmins() <= 1) return "LAST_SUPER_ADMIN";
  await setAccountEmaId({ uid: current.uid, emaId: null });
  await deleteManagedUser(current.uid);
  return null;
}
