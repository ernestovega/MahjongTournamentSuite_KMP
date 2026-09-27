import type { UserRecord } from "firebase-admin/auth";

export type GlobalUserRole = "EDITOR" | "ADMIN";

export function getGlobalUserRole(user: Pick<UserRecord, "customClaims">): GlobalUserRole {
  return user.customClaims?.admin === true ? "ADMIN" : "EDITOR";
}

export function parseGlobalUserRole(value: unknown): GlobalUserRole | null {
  return value === "EDITOR" || value === "ADMIN" ? value : null;
}

export function hasAdminClaim(claims: { admin?: boolean } | undefined): boolean {
  return claims?.admin === true;
}
