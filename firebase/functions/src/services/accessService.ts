import { auth, db } from "../firebase";
import { getGlobalUserRoles, type GlobalUserRole } from "../models/globalRole";
import { readOrganizerIds } from "./organizationsService";
import { PERMISSIONS, ROLE_PERMISSIONS, TOURNAMENT_PERMISSIONS, type Permission } from "../models/permissions";

/**
 * What one account may do, read from the account itself and not from the token. A token keeps the claims of the
 * moment it was issued for up to one hour, so a role or an organization that changed reaches the API at the next request.
 * Each result is reused for a short time per server instance, like the membership check.
 */
export type Access = {
  uid: string;
  disabled: boolean;
  superAdmin: boolean;
  /** The organizations of a club admin, as organization ids. Empty for every other account. */
  adminOrganizations: ReadonlySet<string>;
  editor: boolean;
  player: boolean;
};

const ACCESS_TTL_MS = 30_000;
const MAX_CACHED = 2000;
const accessCache = new Map<string, { until: number; access: Access }>();
const organizersCache = new Map<string, { until: number; organizerIds: string[] | null }>();
const memberCache = new Map<string, number>();

function remember<T>(cache: Map<string, T>, key: string, value: T): void {
  if (cache.size >= MAX_CACHED) cache.clear();
  cache.set(key, value);
}

/** Forgets what this server instance knows about an account, after a change that the account must see at once. */
export function forgetAccess(uid: string): void {
  accessCache.delete(uid);
  for (const key of [...memberCache.keys()]) if (key.endsWith(`/${uid}`)) memberCache.delete(key);
}

/** Forgets the organizers of a tournament after they changed or the tournament was deleted. */
export function forgetTournamentOrganizers(tournamentId: string): void {
  organizersCache.delete(tournamentId);
}

/** A stored list of organization ids, sorted and without repeats. */
export function readOrganizationList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map((item) => String(item ?? "").trim()).filter((id) => id !== ""))].sort();
}

export async function loadAccess(uid: string): Promise<Access> {
  const cached = accessCache.get(uid);
  if (cached && cached.until > Date.now()) return cached.access;
  const [user, profile] = await Promise.all([
    auth.getUser(uid).catch(() => null),
    db.doc(`users/${uid}`).get(),
  ]);
  const roles = user ? getGlobalUserRoles(user) : [];
  const organizations = roles.includes("CLUB_ADMIN") ? readOrganizationList(profile.get("adminOrganizations")) : [];
  const access: Access = {
    uid,
    disabled: user?.disabled ?? true,
    superAdmin: roles.includes("SUPER_ADMIN"),
    adminOrganizations: new Set(organizations),
    editor: roles.includes("EDITOR"),
    player: roles.includes("PLAYER"),
  };
  remember(accessCache, uid, { until: Date.now() + ACCESS_TTL_MS, access });
  return access;
}

/** The roles that the account holds somewhere, so the permissions it may use in at least one place. */
export function heldRoles(access: Access): GlobalUserRole[] {
  const roles: GlobalUserRole[] = [];
  if (access.superAdmin) roles.push("SUPER_ADMIN");
  if (access.adminOrganizations.size > 0) roles.push("CLUB_ADMIN");
  if (access.editor) roles.push("EDITOR");
  if (access.player) roles.push("PLAYER");
  return roles;
}

export function isStaff(access: Access): boolean {
  return access.superAdmin || access.adminOrganizations.size > 0 || access.editor;
}

/** True when some role of the account grants the permission somewhere. A route then checks the scope. */
export function hasPermissionSomewhere(access: Access, permission: Permission): boolean {
  return heldRoles(access).some((role) => ROLE_PERMISSIONS[role].includes(permission));
}

/** Every permission that some role of the account grants somewhere. The apps use it to show or hide actions. */
export function permissionsSomewhere(access: Access): Permission[] {
  return PERMISSIONS.filter((permission) => hasPermissionSomewhere(access, permission));
}

/** The organizers of a tournament (the first one is the main one), or null when the tournament does not exist. */
export async function tournamentOrganizers(tournamentId: string): Promise<string[] | null> {
  const cached = organizersCache.get(tournamentId);
  if (cached && cached.until > Date.now()) return cached.organizerIds;
  const snapshot = await db.doc(`tournaments/${tournamentId}`).get();
  const organizerIds = snapshot.exists ? readOrganizerIds(snapshot.get("organizerIds")) : null;
  remember(organizersCache, tournamentId, { until: Date.now() + ACCESS_TTL_MS, organizerIds });
  return organizerIds;
}

/** True when the club admin manages one of the organizations. */
export function managesSomeOrganization(access: Access, organizerIds: readonly string[] | null): boolean {
  return organizerIds != null && organizerIds.some((id) => access.adminOrganizations.has(id));
}

export async function isTournamentMember(uid: string, tournamentId: string): Promise<boolean> {
  const key = `${tournamentId}/${uid}`;
  if ((memberCache.get(key) ?? 0) > Date.now()) return true;
  const member = await db.doc(`tournaments/${tournamentId}/members/${uid}`).get();
  if (!member.exists) {
    memberCache.delete(key);
    return false;
  }
  remember(memberCache, key, Date.now() + ACCESS_TTL_MS);
  return true;
}

/**
 * The roles that the account holds in one tournament. `known` lets a caller that already read the tournament and
 * its members skip those reads.
 */
export async function rolesInTournament(
  access: Access,
  tournamentId: string,
  known?: { organizerIds?: string[] | null; member?: boolean },
): Promise<GlobalUserRole[]> {
  const roles: GlobalUserRole[] = [];
  if (access.superAdmin) roles.push("SUPER_ADMIN");
  if (access.adminOrganizations.size > 0) {
    const organizerIds = known?.organizerIds !== undefined ? known.organizerIds : await tournamentOrganizers(tournamentId);
    if (managesSomeOrganization(access, organizerIds)) roles.push("CLUB_ADMIN");
  }
  if (access.editor) {
    const member = known?.member !== undefined ? known.member : await isTournamentMember(access.uid, tournamentId);
    if (member) roles.push("EDITOR");
  }
  return roles;
}

export function permissionsOf(roles: GlobalUserRole[]): Set<Permission> {
  return new Set(roles.flatMap((role) => ROLE_PERMISSIONS[role]));
}

/** The tournament permissions of the account in one tournament, for the apps. */
export async function tournamentPermissions(
  access: Access,
  tournamentId: string,
  known?: { organizerIds?: string[] | null; member?: boolean },
): Promise<Permission[]> {
  const granted = permissionsOf(await rolesInTournament(access, tournamentId, known));
  return PERMISSIONS.filter((permission) => TOURNAMENT_PERMISSIONS.has(permission) && granted.has(permission));
}

export async function canInTournament(access: Access, permission: Permission, tournamentId: string): Promise<boolean> {
  return permissionsOf(await rolesInTournament(access, tournamentId)).has(permission);
}

/** True when the account may use the permission for this organization: a super admin, or a club admin of it. */
export function canInOrganization(access: Access, permission: Permission, organizationId: string): boolean {
  if (access.superAdmin && ROLE_PERMISSIONS.SUPER_ADMIN.includes(permission)) return true;
  return access.adminOrganizations.has(organizationId) && ROLE_PERMISSIONS.CLUB_ADMIN.includes(permission);
}
