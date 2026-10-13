import type { UserRecord } from "firebase-admin/auth";

export type GlobalUserRole = "PLAYER" | "EDITOR" | "ADMIN";

/** Token claims that decide the capabilities of an account. */
export type RoleClaims = { admin?: boolean; editor?: boolean; player?: boolean };

/** An account may use management routes only when it is an admin or an editor. */
export function hasEditorAccess(claims: RoleClaims | undefined): boolean {
  return claims?.admin === true || claims?.editor === true;
}

export function hasPlayerClaim(claims: RoleClaims | undefined): boolean {
  return claims?.player === true;
}

export function hasAdminClaim(claims: { admin?: boolean } | undefined): boolean {
  return claims?.admin === true;
}

/** All capabilities of an account, in rank order: ADMIN, EDITOR, PLAYER. */
export function getGlobalUserRoles(user: Pick<UserRecord, "customClaims">): GlobalUserRole[] {
  const claims = user.customClaims as RoleClaims | undefined;
  const roles: GlobalUserRole[] = [];
  if (hasAdminClaim(claims)) roles.push("ADMIN");
  if (claims?.editor === true) roles.push("EDITOR");
  if (hasPlayerClaim(claims)) roles.push("PLAYER");
  return roles;
}

/** The highest capability of an account. An account with no claim has no capability and reads as a player. */
export function getGlobalUserRole(user: Pick<UserRecord, "customClaims">): GlobalUserRole {
  return getGlobalUserRoles(user)[0] ?? "PLAYER";
}

/** Roles that management routes may set. Player accounts exist only through invitations. */
export function parseGlobalUserRole(value: unknown): GlobalUserRole | null {
  return value === "EDITOR" || value === "ADMIN" ? value : null;
}
