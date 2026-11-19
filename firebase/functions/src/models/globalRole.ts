import type { UserRecord } from "firebase-admin/auth";

/**
 * The roles of an account, in rank order. An account may hold several at once: a country admin of one country can
 * also be an editor of a tournament of another country, and any of them can also play.
 * - SUPER_ADMIN: every tournament and every account.
 * - COUNTRY_ADMIN: every tournament of its countries (`users/{uid}.adminCountries`), and the accounts of those countries.
 * - EDITOR: the tournaments assigned to it (`tournaments/{id}/members/{uid}`).
 * - PLAYER: the tournaments where it plays, through its EMA id.
 */
export type GlobalUserRole = "PLAYER" | "EDITOR" | "COUNTRY_ADMIN" | "SUPER_ADMIN";

export const ROLE_RANK: readonly GlobalUserRole[] = ["SUPER_ADMIN", "COUNTRY_ADMIN", "EDITOR", "PLAYER"];

/** Token claims that mark the roles of an account. The countries and the assignments live in Firestore. */
export type RoleClaims = { superAdmin?: boolean; countryAdmin?: boolean; editor?: boolean; player?: boolean };

/** An account may use management routes when it is a super admin, a country admin or an editor. */
export function hasStaffClaim(claims: RoleClaims | undefined): boolean {
  return claims?.superAdmin === true || claims?.countryAdmin === true || claims?.editor === true;
}

export function hasPlayerClaim(claims: RoleClaims | undefined): boolean {
  return claims?.player === true;
}

export function hasSuperAdminClaim(claims: { superAdmin?: boolean } | undefined): boolean {
  return claims?.superAdmin === true;
}

/** All roles of an account, in rank order: SUPER_ADMIN, COUNTRY_ADMIN, EDITOR, PLAYER. */
export function getGlobalUserRoles(user: Pick<UserRecord, "customClaims">): GlobalUserRole[] {
  const claims = user.customClaims as RoleClaims | undefined;
  const roles: GlobalUserRole[] = [];
  if (hasSuperAdminClaim(claims)) roles.push("SUPER_ADMIN");
  if (claims?.countryAdmin === true) roles.push("COUNTRY_ADMIN");
  if (claims?.editor === true) roles.push("EDITOR");
  if (hasPlayerClaim(claims)) roles.push("PLAYER");
  return roles;
}

/** The highest role of an account. An account with no claim has no capability and reads as a player. */
export function getGlobalUserRole(user: Pick<UserRecord, "customClaims">): GlobalUserRole {
  return getGlobalUserRoles(user)[0] ?? "PLAYER";
}

/** The roles that a management route may set. The player role has its own checks in the route. */
export function parseGlobalUserRole(value: unknown): Exclude<GlobalUserRole, "PLAYER"> | null {
  return value === "EDITOR" || value === "COUNTRY_ADMIN" || value === "SUPER_ADMIN" ? value : null;
}

/** True when the first role ranks above the second one. */
export function ranksAbove(left: GlobalUserRole, right: GlobalUserRole): boolean {
  return ROLE_RANK.indexOf(left) < ROLE_RANK.indexOf(right);
}
