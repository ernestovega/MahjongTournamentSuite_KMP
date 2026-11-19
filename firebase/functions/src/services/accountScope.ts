import { db } from "../firebase";
import type { Access } from "./accessService";
import { countryTournamentIds, memberTournamentIds } from "./tournamentScope";
import type { ManagedUser, TournamentAssignment } from "./usersService";

/**
 * Which accounts an account of the management app sees and changes.
 * - A super admin sees and changes every account.
 * - Everyone else sees every player account, the own account, the super admins, and the editors and country admins
 *   that share a country with it. The countries of an editor are the countries of its tournaments.
 * - An email of a player shows only when the player plays in a tournament that the viewer manages.
 * - A country admin changes, inside its countries only, the countries and the tournaments of editors and country
 *   admins. The tournaments and the countries outside them stay as they are.
 */
export type AccountScope = {
  superAdmin: boolean;
  uid: string;
  /** The tournaments that the viewer manages: assigned ones and the ones of its countries. */
  tournamentIds: ReadonlySet<string>;
  /** The countries of the viewer: its own as a country admin and the ones of its assigned tournaments. */
  countries: ReadonlySet<string>;
  /** The countries where the viewer manages accounts: its countries as a country admin. */
  manageCountries: ReadonlySet<string>;
  /** The tournaments of the countries where the viewer manages accounts. */
  manageTournamentIds: ReadonlySet<string>;
  /** The EMA ids that play in the tournaments of the viewer, or null for a super admin, who sees every email. */
  emaIdsWithEmail: ReadonlySet<string> | null;
};

async function countriesOf(tournamentIds: ReadonlySet<string>): Promise<Set<string>> {
  const ids = [...tournamentIds];
  if (ids.length === 0) return new Set();
  const snapshots = await db.getAll(...ids.map((id) => db.doc(`tournaments/${id}`)), { fieldMask: ["hostCountry"] });
  return new Set(snapshots.filter((snapshot) => snapshot.exists).map((snapshot) => String(snapshot.get("hostCountry") ?? "").toUpperCase()));
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
      superAdmin: true, uid: access.uid, tournamentIds: new Set(), countries: new Set(), manageCountries: new Set(),
      manageTournamentIds: new Set(), emaIdsWithEmail: null,
    };
  }
  const memberIds = access.editor ? await memberTournamentIds(access.uid) : new Set<string>();
  const manageTournamentIds = await countryTournamentIds(access.adminCountries);
  const tournamentIds = new Set([...memberIds, ...manageTournamentIds]);
  const countries = new Set([...access.adminCountries, ...(await countriesOf(memberIds))]);
  return {
    superAdmin: false,
    uid: access.uid,
    tournamentIds,
    countries,
    manageCountries: access.adminCountries,
    manageTournamentIds,
    emaIdsWithEmail: await seatedEmaIds(tournamentIds),
  };
}

/** The countries of an account: its own as a country admin and the ones of its assigned tournaments. */
export function accountCountries(user: Pick<ManagedUser, "adminCountries" | "tournamentAssignments">): Set<string> {
  return new Set([
    ...user.adminCountries,
    ...user.tournamentAssignments.map((assignment) => assignment.country ?? "").filter((country) => country !== ""),
  ]);
}

export function isStaffAccount(user: Pick<ManagedUser, "roles">): boolean {
  return user.roles.some((role) => role !== "PLAYER");
}

export function canSeeAccount(scope: AccountScope, user: ManagedUser): boolean {
  if (scope.superAdmin || user.uid === scope.uid) return true;
  if (!isStaffAccount(user) || user.roles.includes("SUPER_ADMIN")) return true;
  return [...accountCountries(user)].some((country) => scope.countries.has(country));
}

/** True when the viewer may change the countries and the tournaments of this staff account inside its scope. */
export function canManageStaffAccount(scope: AccountScope, user: ManagedUser): boolean {
  if (scope.superAdmin) return true;
  if (user.uid === scope.uid || user.roles.includes("SUPER_ADMIN") || scope.manageCountries.size === 0) return false;
  if (!isStaffAccount(user)) return true;
  return [...accountCountries(user)].some((country) => scope.manageCountries.has(country));
}

/** True when the account has a country or a tournament outside the countries where the viewer manages accounts. */
export function hasGrantsOutside(scope: AccountScope, user: ManagedUser): boolean {
  if (scope.superAdmin) return false;
  return user.adminCountries.some((country) => !scope.manageCountries.has(country))
    || user.tournamentAssignments.some((assignment) => !scope.manageTournamentIds.has(assignment.tournamentId));
}

export type AccountView = ManagedUser & {
  /** True when the viewer may change the role, the countries or the tournaments of the account. */
  canManage: boolean;
  /** True when the account has countries or tournaments that the viewer does not manage, so it cannot demote it. */
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
