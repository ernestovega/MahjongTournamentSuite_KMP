import type { UserRecord } from "firebase-admin/auth";

/**
 * The roles of an account, in rank order. An account may hold several at once: a club admin of one organization can
 * also be an editor of a tournament of another organization, and any of them can also play.
 * - SUPER_ADMIN: every tournament and every account.
 * - CLUB_ADMIN: every tournament that its organizations (clubs or federations, `users/{uid}.adminOrganizations`)
 *   organize or co-organize, and the staff accounts of those organizations.
 * - EDITOR: the tournaments assigned to it (`tournaments/{id}/members/{uid}`).
 * - PLAYER: the tournaments where it plays, through its EMA id.
 */
export type GlobalUserRole = "PLAYER" | "EDITOR" | "CLUB_ADMIN" | "SUPER_ADMIN";

export const ROLE_RANK: readonly GlobalUserRole[] = ["SUPER_ADMIN", "CLUB_ADMIN", "EDITOR", "PLAYER"];

/** Token claims that mark the roles of an account. The organizations and the assignments live in Firestore. */
export type RoleClaims = { superAdmin?: boolean; clubAdmin?: boolean; editor?: boolean; player?: boolean };

/** An account may use management routes when it is a super admin, a club admin or an editor. */
export function hasStaffClaim(claims: RoleClaims | undefined): boolean {
  return claims?.superAdmin === true || claims?.clubAdmin === true || claims?.editor === true;
}

export function hasPlayerClaim(claims: RoleClaims | undefined): boolean {
  return claims?.player === true;
}

export function hasSuperAdminClaim(claims: { superAdmin?: boolean } | undefined): boolean {
  return claims?.superAdmin === true;
}

/** All roles of an account, in rank order: SUPER_ADMIN, CLUB_ADMIN, EDITOR, PLAYER. */
export function getGlobalUserRoles(user: Pick<UserRecord, "customClaims">): GlobalUserRole[] {
  const claims = user.customClaims as RoleClaims | undefined;
  const roles: GlobalUserRole[] = [];
  if (hasSuperAdminClaim(claims)) roles.push("SUPER_ADMIN");
  if (claims?.clubAdmin === true) roles.push("CLUB_ADMIN");
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
  return value === "EDITOR" || value === "CLUB_ADMIN" || value === "SUPER_ADMIN" ? value : null;
}

/** True when the first role ranks above the second one. */
export function ranksAbove(left: GlobalUserRole, right: GlobalUserRole): boolean {
  return ROLE_RANK.indexOf(left) < ROLE_RANK.indexOf(right);
}
