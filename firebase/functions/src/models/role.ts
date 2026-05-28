export type Role = "READER" | "EDITOR" | "ADMIN";

export const ROLE_ORDER: Record<Role, number> = {
  READER: 1,
  EDITOR: 2,
  ADMIN: 3,
};

export function roleMeetsMinimum(role: Role, minimum: Role): boolean {
  return ROLE_ORDER[role] >= ROLE_ORDER[minimum];
}

export function parseRole(value: unknown): Role | null {
  if (value === "READER" || value === "EDITOR" || value === "ADMIN") return value;
  return null;
}
