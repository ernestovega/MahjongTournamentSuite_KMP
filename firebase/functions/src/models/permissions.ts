import type { GlobalUserRole } from "./globalRole";

/**
 * Every action that a role may or may not do, with its name. The routes check a permission, never a role, so this
 * table is the one place that says what each role can do. Each role holds its permissions only in its own scope:
 * a super admin everywhere, a country admin in the tournaments and the accounts of its countries, an editor in the
 * tournaments assigned to it. `docs/user-roles-matrix.md` explains the same table in words.
 */
export const PERMISSIONS = [
  // Inside a tournament.
  "tournament.view",
  "tournament.edit",
  "tournament.roundControl",
  "tournament.editCompleted",
  "tournament.configure",
  "tournament.delete",
  "tournament.resetTable",
  "tournament.members",
  "tournament.activity",
  "tournament.playerEmails",
  // Tournaments, outside one tournament.
  "tournaments.create",
  "tournaments.changeCountry",
  "tournaments.deleteFinished",
  // Accounts.
  "accounts.view",
  "accounts.createPlayer",
  "accounts.manageStaff",
  "accounts.manageSuperAdmins",
  "accounts.changeEmail",
  "accounts.changeEmaId",
  "accounts.passwordReset",
  "accounts.photo",
  "accounts.disable",
  "accounts.delete",
  "accounts.requestRemoval",
  "accounts.reviewRequests",
  // The shared EMA player registry.
  "ema.create",
  "ema.edit",
  "ema.delete",
  // Tools.
  "tools.venues",
  "tools.idCards",
  // DEV menu.
  "dev.activity",
  "dev.errors",
] as const;

export type Permission = typeof PERMISSIONS[number];

/** The permissions that act on one tournament, so the scope of the role must hold that tournament. */
export const TOURNAMENT_PERMISSIONS: ReadonlySet<Permission> = new Set(
  PERMISSIONS.filter((permission) => permission.startsWith("tournament.")),
);

const EDITOR: readonly Permission[] = [
  "tournament.view",
  "tournament.edit",
  "tournament.roundControl",
  "tournament.playerEmails",
  "accounts.view",
  "accounts.createPlayer",
  "tools.venues",
  "tools.idCards",
];

const COUNTRY_ADMIN: readonly Permission[] = [
  ...EDITOR,
  "tournament.editCompleted",
  "tournament.configure",
  "tournament.delete",
  "tournament.resetTable",
  "tournament.members",
  "tournament.activity",
  "tournaments.create",
  "accounts.manageStaff",
  "accounts.passwordReset",
  "accounts.requestRemoval",
  "ema.create",
];

export const ROLE_PERMISSIONS: Readonly<Record<GlobalUserRole, readonly Permission[]>> = {
  PLAYER: [],
  EDITOR,
  COUNTRY_ADMIN,
  SUPER_ADMIN: PERMISSIONS.filter((permission) => permission !== "accounts.requestRemoval"),
};

export function roleHasPermission(role: GlobalUserRole, permission: Permission): boolean {
  return ROLE_PERMISSIONS[role].includes(permission);
}
