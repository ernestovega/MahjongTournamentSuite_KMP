import { auth, db } from "../firebase";
import { getGlobalUserRoles, type GlobalUserRole } from "../models/globalRole";
import { PERMISSIONS, ROLE_PERMISSIONS, TOURNAMENT_PERMISSIONS, type Permission } from "../models/permissions";

/**
 * What one account may do, read from the account itself and not from the token. A token keeps the claims of the
 * moment it was issued for up to one hour, so a role or a country that changed reaches the API at the next request.
 * Each result is reused for a short time per server instance, like the membership check.
 */
export type Access = {
  uid: string;
  disabled: boolean;
  superAdmin: boolean;
  /** The countries of a country admin, as two-letter codes. Empty for every other account. */
  adminCountries: ReadonlySet<string>;
  editor: boolean;
  player: boolean;
};

const ACCESS_TTL_MS = 30_000;
const MAX_CACHED = 2000;
const accessCache = new Map<string, { until: number; access: Access }>();
const countryCache = new Map<string, { until: number; country: string | null }>();
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

/** Forgets the country of a tournament after it changed or the tournament was deleted. */
export function forgetTournamentCountry(tournamentId: string): void {
  countryCache.delete(tournamentId);
}

export function readCountryList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map((item) => String(item ?? "").trim().toUpperCase()).filter((code) => /^[A-Z]{2}$/.test(code)))].sort();
}

export async function loadAccess(uid: string): Promise<Access> {
  const cached = accessCache.get(uid);
  if (cached && cached.until > Date.now()) return cached.access;
  const [user, profile] = await Promise.all([
    auth.getUser(uid).catch(() => null),
    db.doc(`users/${uid}`).get(),
  ]);
  const roles = user ? getGlobalUserRoles(user) : [];
  const countries = roles.includes("COUNTRY_ADMIN") ? readCountryList(profile.get("adminCountries")) : [];
  const access: Access = {
    uid,
    disabled: user?.disabled ?? true,
    superAdmin: roles.includes("SUPER_ADMIN"),
    adminCountries: new Set(countries),
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
  if (access.adminCountries.size > 0) roles.push("COUNTRY_ADMIN");
  if (access.editor) roles.push("EDITOR");
  if (access.player) roles.push("PLAYER");
  return roles;
}

export function isStaff(access: Access): boolean {
  return access.superAdmin || access.adminCountries.size > 0 || access.editor;
}

/** True when some role of the account grants the permission somewhere. A route then checks the scope. */
export function hasPermissionSomewhere(access: Access, permission: Permission): boolean {
  return heldRoles(access).some((role) => ROLE_PERMISSIONS[role].includes(permission));
}

/** Every permission that some role of the account grants somewhere. The apps use it to show or hide actions. */
export function permissionsSomewhere(access: Access): Permission[] {
  return PERMISSIONS.filter((permission) => hasPermissionSomewhere(access, permission));
}

/** The host country of a tournament, or null when the tournament does not exist. */
export async function tournamentCountry(tournamentId: string): Promise<string | null> {
  const cached = countryCache.get(tournamentId);
  if (cached && cached.until > Date.now()) return cached.country;
  const snapshot = await db.doc(`tournaments/${tournamentId}`).get();
  const country = snapshot.exists ? String(snapshot.get("hostCountry") ?? "").trim().toUpperCase() : null;
  remember(countryCache, tournamentId, { until: Date.now() + ACCESS_TTL_MS, country });
  return country;
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
  known?: { country?: string | null; member?: boolean },
): Promise<GlobalUserRole[]> {
  const roles: GlobalUserRole[] = [];
  if (access.superAdmin) roles.push("SUPER_ADMIN");
  if (access.adminCountries.size > 0) {
    const country = known?.country !== undefined ? known.country : await tournamentCountry(tournamentId);
    if (country != null && access.adminCountries.has(country)) roles.push("COUNTRY_ADMIN");
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
  known?: { country?: string | null; member?: boolean },
): Promise<Permission[]> {
  const granted = permissionsOf(await rolesInTournament(access, tournamentId, known));
  return PERMISSIONS.filter((permission) => TOURNAMENT_PERMISSIONS.has(permission) && granted.has(permission));
}

export async function canInTournament(access: Access, permission: Permission, tournamentId: string): Promise<boolean> {
  return permissionsOf(await rolesInTournament(access, tournamentId)).has(permission);
}

/** True when the account may use the permission for this country: a super admin, or a country admin of it. */
export function canInCountry(access: Access, permission: Permission, country: string): boolean {
  if (access.superAdmin && ROLE_PERMISSIONS.SUPER_ADMIN.includes(permission)) return true;
  return access.adminCountries.has(country) && ROLE_PERMISSIONS.COUNTRY_ADMIN.includes(permission);
}
