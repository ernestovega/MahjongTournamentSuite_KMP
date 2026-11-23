import { db } from "../firebase";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { listManagedUsers, type ManagedUser } from "./usersService";
import type { GlobalUserRole } from "../models/globalRole";

/** An account of the app that can get a staff ID card: only its name, never its email. */
export type IdCardStaffCandidate = {
  uid: string;
  name: string;
  role: GlobalUserRole;
  /** True when the account is an editor or an admin of this tournament. */
  inTournament: boolean;
};

const ROLE_ORDER: Record<GlobalUserRole, number> = { EDITOR: 0, CLUB_ADMIN: 1, SUPER_ADMIN: 2, PLAYER: 3 };

/**
 * The accounts with a name, for the staff ID cards. The editors and admins of the tournament come first, then the other
 * editors and admins, then the players, each group by name.
 */
export function idCardStaffCandidatesOf(
  users: Array<Pick<ManagedUser, "uid" | "alias" | "emaId" | "role" | "disabled" | "tournamentAssignments">>,
  tournamentId: string,
  emaNames: ReadonlyMap<string, string>,
): IdCardStaffCandidate[] {
  return users
    .filter((user) => !user.disabled)
    .map((user) => ({
      uid: user.uid,
      // The name of the EMA player of the account, or its alias.
      name: ((user.emaId == null ? "" : emaNames.get(user.emaId)) || user.alias).trim(),
      role: user.role,
      inTournament: user.role !== "PLAYER" && user.tournamentAssignments.some((it) => it.tournamentId === tournamentId),
    }))
    .filter((candidate) => candidate.name.length > 0)
    .sort((left, right) =>
      Number(right.inTournament) - Number(left.inTournament) ||
      Number(left.role === "PLAYER") - Number(right.role === "PLAYER") ||
      ROLE_ORDER[left.role] - ROLE_ORDER[right.role] ||
      left.name.localeCompare(right.name));
}

export async function listIdCardStaffCandidates(tournamentId: string): Promise<IdCardStaffCandidate[]> {
  const users = await listManagedUsers();
  const emaIds = [...new Set(users.map((user) => user.emaId).filter((id): id is string => id != null))];
  const emaNames = new Map<string, string>();
  for (let start = 0; start < emaIds.length; start += 300) {
    const chunk = emaIds.slice(start, start + 300);
    const snapshots = await db.getAll(...chunk.map((id) => db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${id}`)));
    snapshots.forEach((snapshot, index) => {
      if (!snapshot.exists) return;
      const name = `${String(snapshot.get("firstName") ?? "")} ${String(snapshot.get("lastName") ?? "")}`.trim();
      if (name.length > 0) emaNames.set(chunk[index], name);
    });
  }
  return idCardStaffCandidatesOf(users, tournamentId, emaNames);
}
