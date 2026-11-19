import { db } from "../firebase";
import { readOrganizerIds } from "./organizationsService";
import type { Access } from "./accessService";
import { memberTournamentIds, organizationTournamentIds } from "./tournamentScope";
import type { ManagedUser, TournamentAssignment } from "./usersService";

/**
 * Which accounts an account of the management app sees and changes.
 * - A super admin sees and changes every account.
 * - Everyone else sees every player account, the own account, the super admins, and the editors and club admins
 *   that share an organization with it. The organizations of an editor are the organizers of its tournaments.
 * - An email of a player shows only when the player plays in a tournament that the viewer manages.
 * - A club admin changes, inside its organizations only, the organizations and the tournaments of editors and club
 *   admins. The tournaments and the organizations outside them stay as they are.
 */
export type AccountScope = {
  superAdmin: boolean;
  uid: string;
  /** The tournaments that the viewer manages: assigned ones and the ones of its organizations. */
  tournamentIds: ReadonlySet<string>;
  /** The organizations of the viewer: its own as a club admin and the organizers of its assigned tournaments. */
  organizations: ReadonlySet<string>;
  /** The organizations where the viewer manages accounts: its organizations as a club admin. */
  manageOrganizations: ReadonlySet<string>;
  /** The tournaments of the organizations where the viewer manages accounts. */
  manageTournamentIds: ReadonlySet<string>;
  /** The EMA ids that play in the tournaments of the viewer, or null for a super admin, who sees every email. */
  emaIdsWithEmail: ReadonlySet<string> | null;
};

async function organizersOfTournaments(tournamentIds: ReadonlySet<string>): Promise<Set<string>> {
  const ids = [...tournamentIds];
  if (ids.length === 0) return new Set();
  const snapshots = await db.getAll(...ids.map((id) => db.doc(`tournaments/${id}`)), { fieldMask: ["organizerIds"] });
  return new Set(snapshots.filter((snapshot) => snapshot.exists).flatMap((snapshot) => readOrganizerIds(snapshot.get("organizerIds"))));
}

async function seatedEmaIds(tournamentIds: ReadonlySet<string>): Promise<Set<string>> {
  const emaIds = new Set<string>();
  for (const tournamentId of tournamentIds) {
    const slots = await db.collection(`tournaments/${tournamentId}/players`).select("assignedEmaId").get();
    slots.docs.forEach((slot) => {
      const emaId = String(slot.get("assignedEmaId") ?? "").trim();
      if (emaId !== "") emaIds.add(emaId);
    });
  }
  return emaIds;
}

export async function loadAccountScope(access: Access): Promise<AccountScope> {
  if (access.superAdmin) {
    return {
      superAdmin: true, uid: access.uid, tournamentIds: new Set(), organizations: new Set(), manageOrganizations: new Set(),
      manageTournamentIds: new Set(), emaIdsWithEmail: null,
    };
  }
  const memberIds = access.editor ? await memberTournamentIds(access.uid) : new Set<string>();
  const manageTournamentIds = await organizationTournamentIds(access.adminOrganizations);
  const tournamentIds = new Set([...memberIds, ...manageTournamentIds]);
  const organizations = new Set([...access.adminOrganizations, ...(await organizersOfTournaments(memberIds))]);
  return {
    superAdmin: false,
    uid: access.uid,
    tournamentIds,
    organizations,
    manageOrganizations: access.adminOrganizations,
    manageTournamentIds,
    emaIdsWithEmail: await seatedEmaIds(tournamentIds),
  };
}

/** The organizations of an account: its own as a club admin and the organizers of its assigned tournaments. */
export function accountOrganizations(user: Pick<ManagedUser, "adminOrganizations" | "tournamentAssignments">): Set<string> {
  return new Set([
    ...user.adminOrganizations,
    ...user.tournamentAssignments.flatMap((assignment) => assignment.organizerIds ?? []),
  ]);
}

export function isStaffAccount(user: Pick<ManagedUser, "roles">): boolean {
  return user.roles.some((role) => role !== "PLAYER");
}

export function canSeeAccount(scope: AccountScope, user: ManagedUser): boolean {
  if (scope.superAdmin || user.uid === scope.uid) return true;
  if (!isStaffAccount(user) || user.roles.includes("SUPER_ADMIN")) return true;
  return [...accountOrganizations(user)].some((id) => scope.organizations.has(id));
}

/** True when the viewer may change the organizations and the tournaments of this staff account inside its scope. */
export function canManageStaffAccount(scope: AccountScope, user: ManagedUser): boolean {
  if (scope.superAdmin) return true;
  if (user.uid === scope.uid || user.roles.includes("SUPER_ADMIN") || scope.manageOrganizations.size === 0) return false;
  if (!isStaffAccount(user)) return true;
  return [...accountOrganizations(user)].some((id) => scope.manageOrganizations.has(id));
}

/** True when the account has an organization or a tournament outside the organizations where the viewer manages accounts. */
export function hasGrantsOutside(scope: AccountScope, user: ManagedUser): boolean {
  if (scope.superAdmin) return false;
  return user.adminOrganizations.some((id) => !scope.manageOrganizations.has(id))
    || user.tournamentAssignments.some((assignment) => !scope.manageTournamentIds.has(assignment.tournamentId));
}

export type AccountView = ManagedUser & {
  /** True when the viewer may change the role, the organizations or the tournaments of the account. */
  canManage: boolean;
  /** True when the account has organizations or tournaments that the viewer does not manage, so it cannot demote it. */
  hasGrantsOutside: boolean;
  /** True when the email is hidden from the viewer. */
  emailHidden: boolean;
};

/** The account as the viewer may see it: the assignments inside the scope, and the email when it may show. */
export function viewOfAccount(
  scope: AccountScope,
  user: ManagedUser,
): AccountView {
  if (scope.superAdmin) return { ...user, canManage: user.uid !== scope.uid, hasGrantsOutside: false, emailHidden: false };
  const showsEmail = user.uid === scope.uid
    || isStaffAccount(user)
    || (user.emaId != null && scope.emaIdsWithEmail?.has(user.emaId) === true);
  const assignments: TournamentAssignment[] = user.tournamentAssignments.filter(
    (assignment) => scope.tournamentIds.has(assignment.tournamentId),
  );
  return {
    ...user,
    email: showsEmail ? user.email : "",
    tournamentAssignments: assignments,
    emailHidden: !showsEmail,
    canManage: canManageStaffAccount(scope, user),
    hasGrantsOutside: hasGrantsOutside(scope, user),
  };
}
