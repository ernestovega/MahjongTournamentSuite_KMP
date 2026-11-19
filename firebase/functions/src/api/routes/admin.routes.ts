import { Router, type Response } from "express";
import { recordActivity } from "../../services/activityLog";
import { actorFromToken } from "../../services/changeLog";

import { auth, db } from "../../firebase";
import { assertSafeDocumentId, badRequest, forbidden, notFound } from "../httpError";
import { readContentType, readPhotoData } from "./auth.routes";
import { requireAuth } from "../middleware/requireAuth";
import { accessOf, requirePermission } from "../middleware/requirePermission";
import { getGlobalUserRole, getGlobalUserRoles, parseGlobalUserRole, type GlobalUserRole } from "../../models/globalRole";
import { sendPasswordResetEmail } from "../../services/firebaseAuthRest";
import {
  assertEmailAvailable,
  clearUserPhoto,
  countEnabledSuperAdmins,
  createUserProfile,
  deleteManagedUser,
  getManagedUser,
  getUserProfile,
  listManagedUsers,
  sendPasswordResets,
  setAdminOrganizations,
  setGlobalUserRole,
  setManagedUserDisabled,
  setUserPhoto,
  syncRoleClaims,
  syncUserTournamentAssignments,
  updateManagedUser,
  type ManagedUser,
  type PasswordResetResult,
  type TournamentAssignment,
} from "../../services/usersService";
import { bumpGlobalDataVersion } from "../../services/dataVersionsService";
import { assertEmaIdAvailable, setAccountEmaId } from "../../services/playerAccountsService";
import { pathParam } from "../pathParam";
import { deleteAccountChecked } from "../../services/accountDeletes";
import { listErrorGroups, loadErrorGroup } from "../../services/errorReports";
import { FEED_ORIGINS, FEED_SOURCES, listActivityFeed, type FeedOrigin, type FeedSource } from "../../services/activityFeed";
import { cursorQuery, limitQuery, numberQuery } from "../pageQuery";
import {
  canInOrganization,
  canInTournament,
  forgetAccess,
  forgetAllAccess,
  hasPermissionSomewhere,
  loadAccess,
  permissionsSomewhere,
  readOrganizationList,
  type Access,
} from "../../services/accessService";
import {
  assertOrganizationsExist,
  createOrganization,
  deleteOrganization,
  listOrganizations,
  parseOrganizationInput,
  updateOrganization,
} from "../../services/organizationsService";
import {
  createCustomRole,
  deleteCustomRole,
  listCustomRoles,
  loadCustomRoles,
  parseGrantablePermissions,
  rolePermissionsOf,
  savePermissionSettings,
  updateCustomRole,
  type PermissionSettings,
} from "../../services/customPermissions";
import { CUSTOMIZABLE_ROLES, GRANTABLE, PERMISSIONS, REQUIRED, ROLE_PERMISSIONS, type Permission } from "../../models/permissions";
import { canSeeAccount, isStaffAccount, loadAccountScope, viewOfAccount, type AccountScope } from "../../services/accountScope";
import {
  createAccountRequest,
  listPendingAccountRequests,
  parseAccountRequestKind,
  resolveAccountRequest,
} from "../../services/accountRequests";

/** The server deletes up to 50 accounts in one request. Each one takes several calls, so a longer list would be slow. */
const MAX_USER_DELETES = 50;
type UserDeleteFailure = "NOT_FOUND" | "SELF" | "LAST_SUPER_ADMIN" | "UNKNOWN";
type UserDeleteResult = { uid: string; outcome: "DELETED" | "FAILED"; reason?: UserDeleteFailure };

function requireEmail(value: unknown, fieldName: string): string {
  const email = String(value ?? "").trim();
  if (!email || !email.includes("@")) throw badRequest(`${fieldName} must be a valid email`);
  return email;
}

function parseTournamentAssignments(value: unknown): TournamentAssignment[] {
  if (value == null) return [];
  if (!Array.isArray(value)) throw badRequest("tournamentAssignments must be an array");
  return value.map((item) => {
    const record = item != null && typeof item === "object" ? item as Record<string, unknown> : null;
    const tournamentId = String(record?.tournamentId ?? "").trim();
    if (!tournamentId) throw badRequest("Invalid tournament assignment");
    return { tournamentId, tournamentName: "" };
  });
}

async function parseAdminOrganizations(value: unknown): Promise<string[]> {
  if (value == null) return [];
  if (!Array.isArray(value)) throw badRequest("adminOrganizations must be an array");
  const organizations = readOrganizationList(value);
  if (organizations.length !== new Set(value.map((item) => String(item ?? "").trim())).size) {
    throw badRequest("adminOrganizations must hold organization ids without repeats");
  }
  await assertOrganizationsExist(organizations);
  return organizations;
}

async function assertSuperAdminRemainsEnabled(
  current: Pick<ManagedUser, "role" | "disabled">,
  nextRole: GlobalUserRole,
  nextDisabled: boolean,
): Promise<void> {
  if (current.role !== "SUPER_ADMIN" || current.disabled || (nextRole === "SUPER_ADMIN" && !nextDisabled)) return;
  if (await countEnabledSuperAdmins() <= 1) {
    throw badRequest("The last enabled super admin cannot be demoted or disabled");
  }
}

/**
 * The organizations and the tournaments that an account keeps after a change by a viewer who manages only some of them.
 * What lies outside the scope of the viewer stays as it is. Asking for something outside it that the account does not
 * have yet is refused.
 */
function mergeInScope(
  scope: AccountScope,
  current: ManagedUser | null,
  requestedOrganizations: string[],
  requestedAssignments: TournamentAssignment[],
): { organizations: string[]; assignments: TournamentAssignment[] } {
  if (scope.superAdmin) return { organizations: requestedOrganizations, assignments: requestedAssignments };
  const currentOrganizations = current?.adminOrganizations ?? [];
  const currentAssignments = current?.tournamentAssignments ?? [];
  const outsideOrganizations = currentOrganizations.filter((id) => !scope.manageOrganizations.has(id));
  const strayOrganization = requestedOrganizations.find((id) => !scope.manageOrganizations.has(id) && !outsideOrganizations.includes(id));
  if (strayOrganization) throw forbidden(`You can give only your own organizations (${strayOrganization})`);
  const outsideAssignments = currentAssignments.filter((assignment) => !scope.manageTournamentIds.has(assignment.tournamentId));
  const outsideIds = new Set(outsideAssignments.map((assignment) => assignment.tournamentId));
  const strayTournament = requestedAssignments.find((assignment) =>
    !scope.manageTournamentIds.has(assignment.tournamentId) && !outsideIds.has(assignment.tournamentId));
  if (strayTournament) {
    throw forbidden(`You can assign accounts only to the tournaments of your organizations (${strayTournament.tournamentId})`);
  }
  const insideOrganizations = requestedOrganizations.filter((id) => scope.manageOrganizations.has(id));
  const insideAssignments = requestedAssignments.filter((assignment) => scope.manageTournamentIds.has(assignment.tournamentId));
  return {
    organizations: [...new Set([...outsideOrganizations, ...insideOrganizations])].sort(),
    assignments: [...new Map([...outsideAssignments, ...insideAssignments].map((assignment) => [assignment.tournamentId, assignment])).values()],
  };
}

/** Checks the role that an account gets against the organizations and the tournaments that it keeps. */
function assertRoleFits(role: GlobalUserRole, organizations: string[], assignments: TournamentAssignment[]): void {
  if (role === "CLUB_ADMIN" && organizations.length === 0) throw badRequest("A club admin needs at least one organization");
  if (role === "EDITOR" && organizations.length > 0) {
    throw forbidden("The account is a club admin of organizations that you do not manage, so you cannot make it an editor");
  }
  if (role === "EDITOR" && assignments.length === 0) throw badRequest("An editor needs at least one tournament");
  if (role === "PLAYER" && (organizations.length > 0 || assignments.length > 0)) {
    throw forbidden("The account has organizations or tournaments that you do not manage, so you cannot make it a player");
  }
}

async function viewFor(res: Response, uid: string) {
  const scope = await loadAccountScope(accessOf(res));
  return viewOfAccount(scope, await getManagedUser(uid));
}

/** Removes what an account has in the organizations of the viewer: those organizations and their tournaments. */
async function removeInScope(scope: AccountScope, current: ManagedUser): Promise<void> {
  if (current.adminOrganizations.some((id) => scope.manageOrganizations.has(id))) {
    await setAdminOrganizations(current.uid, current.adminOrganizations.filter((id) => !scope.manageOrganizations.has(id)));
  }
  await syncUserTournamentAssignments(current.uid, [], scope.manageTournamentIds);
  await syncRoleClaims(current.uid);
}


/**
 * The custom roles and the changes of one person that a viewer asks for. `levels.EDITOR` and `levels.CLUB_ADMIN`
 * hold `roleId` (a custom role, or null for the standard one) and `permissions` (the whole list that the person
 * gets, or null to use the role as it is). Only the difference with the role is saved.
 * - A super admin changes everything.
 * - A club admin chooses the role of the editors and club admins of its organizations, among the roles that do not
 *   hold more than it holds, and only for accounts with nothing outside its organizations. It never changes single
 *   permissions.
 */
async function permissionSettingsFor(
  access: Access,
  current: ManagedUser,
  body: Record<string, unknown> | undefined,
  scopeAllowsAll: boolean,
): Promise<PermissionSettings> {
  const levels = body?.levels as Record<string, unknown> | undefined;
  if (levels == null || typeof levels !== "object") throw badRequest("levels must be an object");
  const roles = await loadCustomRoles();
  const managesPermissions = hasPermissionSomewhere(access, "permissions.manage");
  const next: PermissionSettings = {
    customRoles: { ...current.permissionSettings.customRoles },
    permissionChanges: { ...current.permissionSettings.permissionChanges },
  };
  for (const level of CUSTOMIZABLE_ROLES) {
    const sent = levels[level] as Record<string, unknown> | undefined;
    if (sent == null) continue;
    const rawRoleId = sent.roleId;
    const roleId = rawRoleId == null || rawRoleId === "" ? undefined : String(rawRoleId);
    if (roleId != null) {
      const role = roles.get(roleId);
      if (!role) throw badRequest(`Role not found (${roleId})`);
      if (role.base !== level) throw badRequest(`The role ${role.name} builds on ${role.base}, not on ${level}`);
    }
    const rolePermissions = rolePermissionsOf(level, roleId, roles);
    if (roleId !== current.permissionSettings.customRoles[level] && !managesPermissions) {
      if (!scopeAllowsAll) throw forbidden("The account has organizations or tournaments outside yours, so a super admin chooses its role");
      const own = access.rolePermissions.CLUB_ADMIN;
      const extra = rolePermissions.find((permission) => !own.has(permission));
      if (extra) throw forbidden(`You cannot give a role with a permission that you do not have (${extra})`);
    }
    if (roleId == null) delete next.customRoles[level];
    else next.customRoles[level] = roleId;

    if (sent.permissions === undefined) {
      // Without a list the person keeps its own changes on top of the new role.
      continue;
    }
    const wanted = sent.permissions === null
      ? null
      : new Set([...parseGrantablePermissions(sent.permissions, level, `levels.${level}.permissions`), ...REQUIRED]);
    const base = new Set(rolePermissions);
    const change = wanted == null
      ? undefined
      : {
        added: PERMISSIONS.filter((permission) => wanted.has(permission) && !base.has(permission)),
        removed: PERMISSIONS.filter((permission) => !wanted.has(permission) && base.has(permission)),
      };
    const currentChange = current.permissionSettings.permissionChanges[level];
    const same = JSON.stringify(change ?? { added: [], removed: [] }) === JSON.stringify(currentChange ?? { added: [], removed: [] });
    if (!same && !managesPermissions) throw forbidden("Only a super admin changes single permissions of an account");
    if (change == null || (change.added.length === 0 && change.removed.length === 0)) delete next.permissionChanges[level];
    else next.permissionChanges[level] = change;
  }
  return next;
}

export function adminRouter(): Router {
  const router = Router();

  /** The roles of the signed-in account, its organizations and the permissions that it has somewhere. */
  router.get("/whoami", requireAuth, async (_req, res, next) => {
    try {
      const uid = (res.locals.auth as { uid: string }).uid;
      const user = await auth.getUser(uid);
      const access = await loadAccess(uid);
      res.status(200).json({
        uid,
        role: getGlobalUserRole(user),
        roles: getGlobalUserRoles(user),
        adminOrganizations: [...access.adminOrganizations],
        permissions: permissionsSomewhere(access),
      });
    } catch (error) {
      next(error);
    }
  });

  /**
   * The error log of the apps: each problem once, with a count. It answers one page at a time, newest first.
   * `since` (milliseconds) keeps the problems seen at that time or later. A `nextCursor` in the answer means that
   * more pages exist. Send its two fields back as `cursorAt` and `cursorId` to read the next page.
   */
  router.get("/error-groups", requireAuth, requirePermission("dev.errors"), async (req, res, next) => {
    try {
      const limit = limitQuery(req.query.limit, 200);
      const since = req.query.since === undefined ? undefined : numberQuery(req.query.since, "since");
      res.status(200).json(await listErrorGroups({ limit, since, cursor: cursorQuery(req.query) }));
    } catch (error) {
      next(error);
    }
  });

  /** One problem with its stack trace and one page of its newest reports. Pages work as in the list. */
  router.get("/error-groups/:id", requireAuth, requirePermission("dev.errors"), async (req, res, next) => {
    try {
      const limit = limitQuery(req.query.limit, 50);
      const group = await loadErrorGroup(pathParam(req, "id"), limit, cursorQuery(req.query));
      if (!group) throw notFound("Error group not found");
      res.status(200).json(group);
    } catch (error) {
      next(error);
    }
  });

  /**
   * Who wrote what and when: the activity log and the table saves of every tournament in one list, newest first.
   * Filters: `tournamentId`, `uid`, `source` (`all`, `tables` or `other`), `origin` (`admin`, `player` or `system`)
   * the period `from` (inclusive) and `to` (exclusive) in milliseconds, and `q`, a free text that the entry must hold
   * (case and accents do not count). Send `nextCursor` back as `cursor`.
   * A super admin reads all of it. A club admin reads the activity of one tournament of its organizations.
   */
  router.get("/activity", requireAuth, requirePermission("tournament.activity"), async (req, res, next) => {
    try {
      const limit = limitQuery(req.query.limit, 50);
      const source = (req.query.source ?? "all") as FeedSource;
      if (!FEED_SOURCES.includes(source)) throw badRequest("source must be all, tables or other");
      const tournamentId = typeof req.query.tournamentId === "string" && req.query.tournamentId ? req.query.tournamentId : undefined;
      if (tournamentId) assertSafeDocumentId(tournamentId, "tournament id");
      const access = accessOf(res);
      if (!access.superAdmin && (tournamentId == null || !(await canInTournament(access, "tournament.activity", tournamentId)))) {
        throw forbidden("You can read the activity of the tournaments of your organizations only");
      }
      const uid = typeof req.query.uid === "string" && req.query.uid ? req.query.uid : undefined;
      const origin = req.query.origin === undefined || req.query.origin === "" ? undefined : req.query.origin as FeedOrigin;
      if (origin !== undefined && !FEED_ORIGINS.includes(origin)) throw badRequest("origin must be admin, player or system");
      const fromMs = req.query.from === undefined ? undefined : numberQuery(req.query.from, "from");
      const toMs = req.query.to === undefined ? undefined : numberQuery(req.query.to, "to");
      if (fromMs !== undefined && toMs !== undefined && toMs <= fromMs) throw badRequest("to must be after from");
      const text = typeof req.query.q === "string" ? req.query.q.trim().slice(0, 100) : "";
      res.status(200).json(await listActivityFeed(
        { tournamentId, uid, source, origin, fromMs, toMs, text: text || undefined }, limit, req.query.cursor,
      ));
    } catch (error) {
      next(error);
    }
  });

  /** The accounts that the signed-in account may see. See accountScope.ts. */
  router.get("/users", requireAuth, requirePermission("accounts.view"), async (_req, res, next) => {
    try {
      const scope = await loadAccountScope(accessOf(res));
      const users = (await listManagedUsers()).filter((user) => canSeeAccount(scope, user)).map((user) => viewOfAccount(scope, user));
      res.status(200).json({ users });
    } catch (error) {
      next(error);
    }
  });

  /**
   * Creates an account. Every editor and admin creates player accounts. A club admin creates editors and club
   * admins of its organizations, and a super admin creates any account.
   */
  router.post("/users", requireAuth, requirePermission("accounts.createPlayer"), async (req, res, next) => {
    let createdUid: string | null = null;
    try {
      const access = accessOf(res);
      const email = requireEmail(req.body?.email, "email");
      const alias = String(req.body?.alias ?? "").trim();
      const role: GlobalUserRole | null = req.body?.role === "PLAYER" ? "PLAYER" : parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      if (role === "SUPER_ADMIN" && !hasPermissionSomewhere(access, "accounts.manageSuperAdmins")) {
        throw forbidden("Only super admins can create super admins");
      }
      if ((role === "EDITOR" || role === "CLUB_ADMIN") && !hasPermissionSomewhere(access, "accounts.manageStaff")) {
        throw forbidden("Only admins can create editor and admin accounts");
      }
      const scope = await loadAccountScope(access);
      const merged = role === "PLAYER" || role === "SUPER_ADMIN"
        ? { organizations: [], assignments: [] }
        : mergeInScope(scope, null, role === "CLUB_ADMIN" ? await parseAdminOrganizations(req.body?.adminOrganizations) : [],
          parseTournamentAssignments(req.body?.tournamentAssignments));
      assertRoleFits(role, merged.organizations, merged.assignments);
      // The EMA id links the account to a player. Check it before the account exists.
      const emaId = String(req.body?.emaId ?? "").trim() || null;
      if (role === "PLAYER" && emaId == null) throw badRequest("A player account needs an EMA id");
      if (emaId != null) await assertEmaIdAvailable(emaId);
      await assertEmailAvailable(email);

      const user = await auth.createUser({ email, disabled: false });
      createdUid = user.uid;
      await createUserProfile(user.uid, email, alias);
      await setGlobalUserRole(user.uid, role, merged.organizations);
      await syncUserTournamentAssignments(user.uid, merged.assignments);
      if (emaId != null) await setAccountEmaId({ uid: user.uid, emaId });
      await syncRoleClaims(user.uid);
      await sendPasswordResetEmail(email);
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "user.create", actor: actorFromToken(res.locals.auth), target: `user ${user.uid}`,
        details: { role, emaId, adminOrganizations: merged.organizations, tournamentAssignments: merged.assignments },
      });

      res.status(201).json(await viewFor(res, user.uid));
    } catch (error) {
      if (createdUid) {
        await setAccountEmaId({ uid: createdUid, emaId: null }).catch(() => undefined);
        await Promise.allSettled([
          auth.deleteUser(createdUid),
          syncUserTournamentAssignments(createdUid, []),
        ]);
        await db.doc(`users/${createdUid}`).delete().catch(() => undefined);
      }
      next(error);
    }
  });

  router.put("/users/:uid", requireAuth, requirePermission("accounts.view"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const current = await getManagedUser(pathParam(req, "uid"));
      const scope = await loadAccountScope(access);
      if (!canSeeAccount(scope, current)) throw forbidden("You cannot change this account");
      const role: GlobalUserRole | null = req.body?.role === "PLAYER" ? "PLAYER" : parseGlobalUserRole(req.body?.role);
      if (!role) throw badRequest("Invalid role");
      // A viewer that does not see the email of the account sends none: the account keeps its email.
      const sentEmail = String(req.body?.email ?? "").trim();
      const email = sentEmail === "" ? current.email : requireEmail(sentEmail, "email");
      const alias = String(req.body?.alias ?? "").trim();
      const self = access.uid === current.uid;
      const staffChange = role !== "PLAYER" || isStaffAccount(current);

      // A new email plus a password reset takes over the account. Only a super admin changes the email.
      if (email.toLowerCase() !== current.email.toLowerCase() && !access.superAdmin) {
        throw forbidden("Only a super admin can change the email of an account");
      }
      if ((current.role === "SUPER_ADMIN" || role === "SUPER_ADMIN") && !access.superAdmin && !self) {
        throw forbidden("Only super admins can change super admin accounts");
      }
      if (staffChange && !self && !(access.superAdmin || (scope.manageOrganizations.size > 0 && viewOfAccount(scope, current).canManage))) {
        throw forbidden("You cannot change the role, the organizations or the tournaments of this account");
      }

      let merged = { organizations: current.adminOrganizations, assignments: current.tournamentAssignments };
      if (self) {
        if (role !== current.role) throw badRequest("You cannot change your own role");
      } else if (staffChange) {
        merged = role === "SUPER_ADMIN" || (role === "PLAYER" && access.superAdmin)
          ? { organizations: [], assignments: [] }
          : mergeInScope(scope, current, role === "CLUB_ADMIN" || role === "EDITOR" ? await parseAdminOrganizations(req.body?.adminOrganizations) : [],
            role === "PLAYER" ? [] : parseTournamentAssignments(req.body?.tournamentAssignments));
        assertRoleFits(role, merged.organizations, merged.assignments);
      }
      await assertSuperAdminRemainsEnabled(current, role, current.disabled);
      await assertEmailAvailable(email, current.uid);
      // Check the EMA id before any change, so a refusal leaves the account as it was.
      const nextEmaId = typeof req.body?.emaId === "string" ? req.body.emaId.trim() || null : undefined;
      const emaIdChanges = nextEmaId !== undefined && nextEmaId !== (current.emaId ?? null);
      if (role === "PLAYER" && (nextEmaId === undefined ? current.emaId : nextEmaId) == null) {
        throw badRequest("A player account needs an EMA id");
      }
      if (emaIdChanges) {
        if (!access.superAdmin) throw forbidden("Only a super admin can change the EMA id of an account");
        if (nextEmaId != null) await assertEmaIdAvailable(nextEmaId, current.uid);
      }

      if (emaIdChanges) await setAccountEmaId({ uid: current.uid, emaId: nextEmaId ?? null });
      const result = await updateManagedUser({
        uid: current.uid,
        email,
        alias,
        role: self ? current.role : role,
        adminOrganizations: merged.organizations,
        tournamentAssignments: merged.assignments,
      });
      // An account disabled because it lost its last tournament comes back with its new role.
      const enabled = current.disabledWithoutRole && !self && role !== "PLAYER" && result !== "DISABLED";
      if (enabled) await setManagedUserDisabled(current.uid, false);
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "user.update", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`,
        details: {
          roleFrom: current.role, roleTo: role, alias, emaId: emaIdChanges ? nextEmaId : undefined,
          adminOrganizations: merged.organizations, tournamentAssignments: merged.assignments, enabled: enabled || undefined,
        },
      });
      res.status(200).json({ ...(await viewFor(res, current.uid)), roleChange: result });
    } catch (error) {
      next(error);
    }
  });

  /**
   * The roles: the four standard ones as the code has them and the custom ones of `roles/{id}`, with how many
   * accounts use each. `grantable` says which permissions a role that builds on Editor or Club Admin may hold.
   */
  router.get("/roles", requireAuth, requirePermission("accounts.view"), async (_req, res, next) => {
    try {
      res.status(200).json({
        standard: (["EDITOR", "CLUB_ADMIN", "SUPER_ADMIN"] as const).map((role) => ({ id: role, base: role, permissions: ROLE_PERMISSIONS[role] })),
        custom: await listCustomRoles(),
        grantable: GRANTABLE,
        required: REQUIRED,
      });
    } catch (error) {
      next(error);
    }
  });

  router.post("/roles", requireAuth, requirePermission("permissions.manage"), async (req, res, next) => {
    try {
      const role = await createCustomRole(req.body, accessOf(res).uid);
      await recordActivity({
        action: "role.create", actor: actorFromToken(res.locals.auth), target: `role ${role.name}`,
        details: { id: role.id, base: role.base, permissions: role.permissions },
      });
      res.status(201).json({ ...role, accounts: 0 });
    } catch (error) {
      next(error);
    }
  });

  router.put("/roles/:roleId", requireAuth, requirePermission("permissions.manage"), async (req, res, next) => {
    try {
      const role = await updateCustomRole(pathParam(req, "roleId"), req.body, accessOf(res).uid);
      forgetAllAccess();
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "role.update", actor: actorFromToken(res.locals.auth), target: `role ${role.name}`,
        details: { id: role.id, base: role.base, permissions: role.permissions },
      });
      const accounts = (await listCustomRoles()).find((item) => item.id === role.id)?.accounts ?? 0;
      res.status(200).json({ ...role, accounts });
    } catch (error) {
      next(error);
    }
  });

  /** Deletes a custom role. The accounts that used it get the standard role again. */
  router.delete("/roles/:roleId", requireAuth, requirePermission("permissions.manage"), async (req, res, next) => {
    try {
      const { role, uids } = await deleteCustomRole(pathParam(req, "roleId"));
      forgetAllAccess();
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "role.delete", actor: actorFromToken(res.locals.auth), target: `role ${role.name}`,
        details: { id: role.id, base: role.base, accounts: uids },
      });
      res.status(200).json({ ok: true, accounts: uids.length });
    } catch (error) {
      next(error);
    }
  });

  /** The custom roles and the single permissions of one editor or club admin. See permissionSettingsFor. */
  router.put("/users/:uid/permissions", requireAuth, requirePermission("accounts.manageStaff"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const current = await getManagedUser(pathParam(req, "uid"));
      if (access.uid === current.uid) throw badRequest("You cannot change your own permissions");
      if (current.roles.includes("SUPER_ADMIN")) throw badRequest("A super admin always has every permission");
      const scope = await loadAccountScope(access);
      const view = viewOfAccount(scope, current);
      if (!canSeeAccount(scope, current) || !view.canManage) throw forbidden("You cannot change this account");
      const settings = await permissionSettingsFor(access, current, req.body, access.superAdmin || !view.hasGrantsOutside);
      await savePermissionSettings(current.uid, settings);
      forgetAccess(current.uid);
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "user.permissions", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`,
        details: { customRoles: settings.customRoles, permissionChanges: settings.permissionChanges },
      });
      res.status(200).json(await viewFor(res, current.uid));
    } catch (error) {
      next(error);
    }
  });

  // Only super admins set the photo of another user. A user sets the own photo with /auth/me/photo.
  router.put("/users/:uid/photo", requireAuth, requirePermission("accounts.photo"), async (req, res, next) => {
    try {
      const contentType = readContentType(req.body);
      await setUserPhoto(pathParam(req, "uid"), contentType, readPhotoData(req.body));
      await recordActivity({
        action: "user.photo", actor: actorFromToken(res.locals.auth), target: pathParam(req, "uid"), details: { contentType },
      });
      res.status(200).json(await viewFor(res, pathParam(req, "uid")));
    } catch (error) {
      next(error);
    }
  });

  router.delete("/users/:uid/photo", requireAuth, requirePermission("accounts.photo"), async (req, res, next) => {
    try {
      await clearUserPhoto(pathParam(req, "uid"));
      await recordActivity({
        action: "user.photo.remove", actor: actorFromToken(res.locals.auth), target: pathParam(req, "uid"), details: {},
      });
      res.status(200).json(await viewFor(res, pathParam(req, "uid")));
    } catch (error) {
      next(error);
    }
  });

  // Sends the password reset email to many accounts at once. A club admin sends it to the accounts that it sees.
  router.post("/users/password-resets", requireAuth, requirePermission("accounts.passwordReset"), async (req, res, next) => {
    try {
      const raw = Array.isArray(req.body?.uids) ? req.body.uids as unknown[] : [];
      const uids = raw.map((value) => String(value ?? "").trim());
      if (uids.some((uid) => uid === "")) throw badRequest("uids must be non-empty texts");
      const access = accessOf(res);
      let allowed = uids;
      const refused: PasswordResetResult[] = [];
      if (!access.superAdmin) {
        const scope = await loadAccountScope(access);
        const visible = new Set((await listManagedUsers()).filter((user) => canSeeAccount(scope, user)).map((user) => user.uid));
        allowed = uids.filter((uid) => visible.has(uid));
        uids.filter((uid) => !visible.has(uid)).forEach((uid) => refused.push({ uid, outcome: "FAILED", reason: "NOT_FOUND" }));
      }
      const results = [...(allowed.length > 0 ? await sendPasswordResets(allowed) : []), ...refused];
      if (results.length === 0) throw badRequest("Choose at least one account");
      await recordActivity({
        action: "user.passwordReset", actor: actorFromToken(res.locals.auth), target: `${results.length} users`,
        details: { uids: results.map((result) => result.uid), sent: results.filter((result) => result.outcome === "SENT").length },
      });
      res.status(200).json({ results });
    } catch (error) {
      next(error);
    }
  });

  router.put("/users/:uid/disabled", requireAuth, requirePermission("accounts.disable"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const disabled = req.body?.disabled;
      if (typeof disabled !== "boolean") throw badRequest("disabled must be a boolean");
      const current = await getManagedUser(pathParam(req, "uid"));
      if (access.uid === current.uid && disabled) throw badRequest("You cannot disable your own account");
      await assertSuperAdminRemainsEnabled(current, current.role, disabled);

      await setManagedUserDisabled(current.uid, disabled);
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: disabled ? "user.disable" : "user.enable", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`,
      });
      res.status(200).json(await viewFor(res, current.uid));
    } catch (error) {
      next(error);
    }
  });

  // Deletes an account for good, for example when the person asks for it (privacy policy). Only super admins do this.
  // The EMA player and the tournament results stay; the slots show "No account" again.
  router.delete("/users/:uid", requireAuth, requirePermission("accounts.delete"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const current = await getManagedUser(pathParam(req, "uid"));
      if (access.uid === current.uid) throw badRequest("You cannot delete your own account");
      await assertSuperAdminRemainsEnabled(current, current.role, true);
      await setAccountEmaId({ uid: current.uid, emaId: null });
      await deleteManagedUser(current.uid);
      await recordActivity({
        action: "user.delete", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`, details: {},
      });
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  // Deletes many accounts at once, like the single delete above. Every account has its own result: the own account,
  // the last enabled super admin or an unknown account fails alone, and the others are still deleted. The accounts
  // go one after another, so the check of the last super admin sees the ones that this request already deleted.
  router.post("/users/delete", requireAuth, requirePermission("accounts.delete"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const raw = Array.isArray(req.body?.uids) ? req.body.uids as unknown[] : [];
      const uids = [...new Set(raw.map((value) => String(value ?? "").trim()))];
      if (uids.some((uid) => uid === "")) throw badRequest("uids must be non-empty texts");
      if (uids.length === 0) throw badRequest("Choose at least one account");
      if (uids.length > MAX_USER_DELETES) throw badRequest(`At most ${MAX_USER_DELETES} accounts per request`);
      const results: UserDeleteResult[] = [];
      for (const uid of uids) {
        const fail = (reason: UserDeleteFailure): UserDeleteResult => ({ uid, outcome: "FAILED", reason });
        try {
          const failure = await deleteAccountChecked(access.uid, uid);
          results.push(failure == null ? { uid, outcome: "DELETED" } : fail(failure));
        } catch (error) {
          console.error("Account delete failed", { uid, error });
          results.push(fail("UNKNOWN"));
        }
      }
      const deleted = results.filter((result) => result.outcome === "DELETED").map((result) => result.uid);
      if (deleted.length > 0) {
        await recordActivity({
          action: "user.delete", actor: actorFromToken(res.locals.auth), target: `${deleted.length} users`, details: { uids: deleted },
        });
      }
      res.status(200).json({ results });
    } catch (error) {
      next(error);
    }
  });

  /**
   * A club admin takes an account out of its organizations: the account loses those organizations and their
   * tournaments. With `kind` DISABLE or DELETE it also asks the super admins to disable or delete the whole account.
   * An editor that keeps no tournament becomes a player, or is disabled without an EMA id.
   */
  router.post("/users/:uid/removal", requireAuth, requirePermission("accounts.requestRemoval"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const current = await getManagedUser(pathParam(req, "uid"));
      if (access.uid === current.uid) throw badRequest("You cannot remove your own account");
      if (current.roles.includes("SUPER_ADMIN")) throw forbidden("Only super admins can change super admin accounts");
      const scope = await loadAccountScope(access);
      if (!canSeeAccount(scope, current)) throw forbidden("You cannot change this account");
      const kind = req.body?.kind == null ? null : parseAccountRequestKind(req.body.kind);
      if (req.body?.kind != null && kind == null) throw badRequest("kind must be DISABLE or DELETE");
      const reason = String(req.body?.reason ?? "");
      if (viewOfAccount(scope, current).canManage) await removeInScope(scope, current);
      const request = kind == null ? null : await createAccountRequest({ kind, targetUid: current.uid, reason, requestedByUid: access.uid });
      await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "user.removal", actor: actorFromToken(res.locals.auth), target: `user ${current.uid}`,
        details: { organizations: [...scope.manageOrganizations], request: kind ?? undefined, reason: reason || undefined },
      });
      res.status(200).json({ user: await viewFor(res, current.uid), request });
    } catch (error) {
      next(error);
    }
  });

  /** The open requests of the club admins to disable or delete accounts. */
  router.get("/account-requests", requireAuth, requirePermission("accounts.reviewRequests"), async (_req, res, next) => {
    try {
      res.status(200).json({ requests: await listPendingAccountRequests() });
    } catch (error) {
      next(error);
    }
  });

  /** A super admin approves a request, which disables or deletes the account at once, or rejects it. */
  router.post("/account-requests/:requestId", requireAuth, requirePermission("accounts.reviewRequests"), async (req, res, next) => {
    try {
      const access = accessOf(res);
      const approve = req.body?.approve;
      if (typeof approve !== "boolean") throw badRequest("approve must be a boolean");
      const request = await resolveAccountRequest(pathParam(req, "requestId"), approve, access.uid);
      let outcome: string = approve ? "DONE" : "REJECTED";
      if (approve) {
        if (request.kind === "DELETE") {
          outcome = (await deleteAccountChecked(access.uid, request.targetUid)) ?? "DONE";
        } else {
          const current = await getManagedUser(request.targetUid);
          await assertSuperAdminRemainsEnabled(current, current.role, true);
          await setManagedUserDisabled(current.uid, true);
        }
        await bumpGlobalDataVersion("users");
      }
      await recordActivity({
        action: approve ? "user.request.approve" : "user.request.reject", actor: actorFromToken(res.locals.auth),
        target: `user ${request.targetUid}`, details: { kind: request.kind, outcome },
      });
      res.status(200).json({ request, outcome });
    } catch (error) {
      next(error);
    }
  });

  /** Every organization (club or federation), by name. Every staff account reads them to pick organizers. */
  router.get("/organizations", requireAuth, requirePermission("tournament.view"), async (_req, res, next) => {
    try {
      const access = accessOf(res);
      const organizations = await listOrganizations();
      res.status(200).json({
        organizations: organizations.map((organization) => ({
          ...organization,
          canEdit: canInOrganization(access, "organizations.edit", organization.id),
          canDelete: access.superAdmin,
        })),
      });
    } catch (error) {
      next(error);
    }
  });

  /** Only a super admin creates organizations, so there are no repeated clubs. */
  router.post("/organizations", requireAuth, requirePermission("organizations.create"), async (req, res, next) => {
    try {
      const input = parseOrganizationInput(req.body);
      const organization = await createOrganization(input, accessOf(res).uid);
      await recordActivity({
        action: "organization.create", actor: actorFromToken(res.locals.auth), target: `organization ${organization.id}`,
        details: { name: input.name, type: input.type, country: input.country, logo: input.logo != null },
      });
      res.status(201).json(organization);
    } catch (error) {
      next(error);
    }
  });

  /** A super admin edits any organization. A club admin edits its own ones. */
  router.put("/organizations/:organizationId", requireAuth, requirePermission("organizations.edit"), async (req, res, next) => {
    try {
      const organizationId = pathParam(req, "organizationId");
      assertSafeDocumentId(organizationId, "organization id");
      const input = parseOrganizationInput(req.body);
      if (!canInOrganization(accessOf(res), "organizations.edit", organizationId)) {
        throw forbidden("You can edit only your own organizations");
      }
      const organization = await updateOrganization(organizationId, input);
      await recordActivity({
        action: "organization.update", actor: actorFromToken(res.locals.auth), target: `organization ${organizationId}`,
        details: {
          name: input.name, type: input.type, country: input.country, primaryColor: input.primaryColor,
          logo: input.removeLogo ? "removed" : input.logo != null ? "changed" : undefined,
        },
      });
      res.status(200).json(organization);
    } catch (error) {
      next(error);
    }
  });

  /** Only a super admin deletes an organization, and only one that organizes no tournament. */
  router.delete("/organizations/:organizationId", requireAuth, requirePermission("organizations.delete"), async (req, res, next) => {
    try {
      const organizationId = pathParam(req, "organizationId");
      assertSafeDocumentId(organizationId, "organization id");
      const { affectedUids } = await deleteOrganization(organizationId);
      for (const uid of affectedUids) {
        forgetAccess(uid);
        await syncRoleClaims(uid);
      }
      if (affectedUids.length > 0) await bumpGlobalDataVersion("users");
      await recordActivity({
        action: "organization.delete", actor: actorFromToken(res.locals.auth), target: `organization ${organizationId}`,
        details: { clubAdmins: affectedUids.length },
      });
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  router.get("/users/lookup", requireAuth, requirePermission("accounts.view"), async (req, res, next) => {
    try {
      const identifier = String(req.query.identifier ?? "").trim();
      if (!identifier) throw badRequest("Missing identifier");
      if (!identifier.includes("@")) throw badRequest("Enter a user email address");
      const profile = await auth.getUserByEmail(identifier).then((user) => getUserProfile(user.uid));
      res.status(200).json(profile);
    } catch (error) {
      next(error);
    }
  });

  return router;
}
