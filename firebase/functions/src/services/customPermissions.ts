import { randomUUID } from "node:crypto";
import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { badRequest, notFound } from "../api/httpError";
import {
  CUSTOMIZABLE_ROLES,
  GRANTABLE,
  PERMISSIONS,
  REQUIRED,
  ROLE_PERMISSIONS,
  isPermission,
  type CustomizableRole,
  type Permission,
} from "../models/permissions";

/**
 * Custom roles and the permissions of one person.
 * - The four standard roles stay as they are in `models/permissions.ts`.
 * - A super admin creates custom roles in `roles/{id}`. Each one builds on Editor or Country Admin, which gives its
 *   scope (assigned tournaments or countries) and the permissions that it may hold (`GRANTABLE`).
 * - An editor or a country admin may use a custom role instead of the standard one: `users/{uid}.customRoles`.
 * - A super admin may also add or take away single permissions for one person: `users/{uid}.permissionChanges`.
 *   Only the difference with the role is saved, so a change to the role still reaches the person.
 * What a person gets is always cut to `GRANTABLE` and always keeps `REQUIRED`, so old or edited data never gives more.
 */
export type CustomRole = {
  id: string;
  name: string;
  base: CustomizableRole;
  permissions: Permission[];
};

export type PermissionChange = { added: Permission[]; removed: Permission[] };

export type PermissionSettings = {
  customRoles: Partial<Record<CustomizableRole, string>>;
  permissionChanges: Partial<Record<CustomizableRole, PermissionChange>>;
};

export const ROLES_COLLECTION = "roles";
export const MAX_ROLE_NAME_LENGTH = 40;
const ROLES_TTL_MS = 30_000;
let rolesCache: { until: number; roles: Map<string, CustomRole> } | null = null;

export function forgetCustomRoles(): void {
  rolesCache = null;
}

function sortedPermissions(values: Iterable<Permission>): Permission[] {
  const set = new Set(values);
  return PERMISSIONS.filter((permission) => set.has(permission));
}

function readPermissionList(value: unknown): Permission[] {
  return Array.isArray(value) ? sortedPermissions(value.filter(isPermission)) : [];
}

function isCustomizableRole(value: unknown): value is CustomizableRole {
  return value === "EDITOR" || value === "COUNTRY_ADMIN";
}

function toRole(id: string, data: Record<string, unknown>): CustomRole | null {
  if (!isCustomizableRole(data.base)) return null;
  const allowed = new Set(GRANTABLE[data.base]);
  return {
    id,
    name: String(data.name ?? "").trim() || id,
    base: data.base,
    permissions: readPermissionList(data.permissions).filter((permission) => allowed.has(permission)),
  };
}

export async function loadCustomRoles(): Promise<Map<string, CustomRole>> {
  if (rolesCache && rolesCache.until > Date.now()) return rolesCache.roles;
  const snapshot = await db.collection(ROLES_COLLECTION).get();
  const roles = new Map<string, CustomRole>();
  snapshot.docs.forEach((doc) => {
    const role = toRole(doc.id, doc.data());
    if (role) roles.set(role.id, role);
  });
  rolesCache = { until: Date.now() + ROLES_TTL_MS, roles };
  return roles;
}

/** The custom roles and the changes of one person, as its profile stores them. Anything unknown is left out. */
export function readPermissionSettings(profile: Record<string, unknown> | undefined): PermissionSettings {
  const settings: PermissionSettings = { customRoles: {}, permissionChanges: {} };
  const customRoles = profile?.customRoles as Record<string, unknown> | undefined;
  const changes = profile?.permissionChanges as Record<string, unknown> | undefined;
  for (const level of CUSTOMIZABLE_ROLES) {
    const roleId = customRoles?.[level];
    if (typeof roleId === "string" && roleId !== "") settings.customRoles[level] = roleId;
    const change = changes?.[level] as Record<string, unknown> | undefined;
    if (change) {
      const added = readPermissionList(change.added);
      const removed = readPermissionList(change.removed);
      if (added.length > 0 || removed.length > 0) settings.permissionChanges[level] = { added, removed };
    }
  }
  return settings;
}

/** The permissions of the role that one person uses at this level: its custom role, or the standard one. */
export function rolePermissionsOf(level: CustomizableRole, roleId: string | undefined, roles: ReadonlyMap<string, CustomRole>): Permission[] {
  const role = roleId == null ? undefined : roles.get(roleId);
  return role && role.base === level ? role.permissions : [...ROLE_PERMISSIONS[level]];
}

/** What one person may do at this level: its role, plus and minus its own changes, cut to what the level allows. */
export function effectivePermissions(
  level: CustomizableRole,
  settings: PermissionSettings,
  roles: ReadonlyMap<string, CustomRole>,
): Set<Permission> {
  const granted = new Set(rolePermissionsOf(level, settings.customRoles[level], roles));
  const change = settings.permissionChanges[level];
  change?.added.forEach((permission) => granted.add(permission));
  change?.removed.forEach((permission) => granted.delete(permission));
  const allowed = new Set(GRANTABLE[level]);
  const result = new Set([...granted].filter((permission) => allowed.has(permission)));
  REQUIRED.forEach((permission) => result.add(permission));
  return result;
}

/** Reads a list of permissions sent by an app and refuses the ones that the level cannot hold. */
export function parseGrantablePermissions(value: unknown, level: CustomizableRole, field: string): Permission[] {
  if (value == null) return [];
  if (!Array.isArray(value)) throw badRequest(`${field} must be an array`);
  const unknown = value.find((item) => !isPermission(item));
  if (unknown !== undefined) throw badRequest(`${field} holds an unknown permission (${String(unknown)})`);
  const allowed = new Set(GRANTABLE[level]);
  const refused = (value as Permission[]).find((permission) => !allowed.has(permission));
  if (refused) throw badRequest(`${field}: the role ${level} cannot have ${refused}`);
  return sortedPermissions(value as Permission[]);
}

export function parseCustomizableRole(value: unknown): CustomizableRole {
  if (!isCustomizableRole(value)) throw badRequest("base must be EDITOR or COUNTRY_ADMIN");
  return value;
}

function parseRoleName(value: unknown): string {
  const name = String(value ?? "").trim().replace(/\s+/g, " ");
  if (name.length === 0 || name.length > MAX_ROLE_NAME_LENGTH) {
    throw badRequest(`The name of a role needs 1 to ${MAX_ROLE_NAME_LENGTH} characters`);
  }
  return name;
}

async function assertNameFree(name: string, exceptId?: string): Promise<void> {
  const roles = await loadCustomRoles();
  const taken = [...roles.values()].some((role) => role.id !== exceptId && role.name.toLowerCase() === name.toLowerCase());
  const standard = ["player", "editor", "country admin", "super admin"].includes(name.toLowerCase());
  if (taken || standard) throw badRequest("A role with this name already exists");
}

export type CustomRoleView = CustomRole & { accounts: number };

/** How many accounts use each custom role. */
async function roleUsage(): Promise<Map<string, number>> {
  const usage = new Map<string, number>();
  const profiles = await db.collection("users").select("customRoles").get();
  profiles.docs.forEach((doc) => {
    const settings = readPermissionSettings(doc.data());
    new Set(Object.values(settings.customRoles)).forEach((id) => usage.set(id, (usage.get(id) ?? 0) + 1));
  });
  return usage;
}

export async function listCustomRoles(): Promise<CustomRoleView[]> {
  forgetCustomRoles();
  const [roles, usage] = await Promise.all([loadCustomRoles(), roleUsage()]);
  return [...roles.values()]
    .map((role) => ({ ...role, accounts: usage.get(role.id) ?? 0 }))
    .sort((left, right) => left.name.localeCompare(right.name));
}

export async function createCustomRole(body: Record<string, unknown> | undefined, actorUid: string): Promise<CustomRole> {
  const name = parseRoleName(body?.name);
  const base = parseCustomizableRole(body?.base);
  const permissions = effectiveRoleList(base, parseGrantablePermissions(body?.permissions, base, "permissions"));
  await assertNameFree(name);
  const id = randomUUID();
  await db.doc(`${ROLES_COLLECTION}/${id}`).set({
    name, base, permissions, createdBy: actorUid, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
  });
  forgetCustomRoles();
  return { id, name, base, permissions };
}

function effectiveRoleList(base: CustomizableRole, permissions: Permission[]): Permission[] {
  return sortedPermissions([...permissions, ...REQUIRED].filter((permission) => GRANTABLE[base].includes(permission)));
}

/** Changes the name or the permissions of a custom role. Its base stays: the people who use it keep their scope. */
export async function updateCustomRole(id: string, body: Record<string, unknown> | undefined, actorUid: string): Promise<CustomRole> {
  const current = (await loadCustomRolesFresh()).get(id);
  if (!current) throw notFound("Role not found");
  if (body?.base != null && body.base !== current.base) throw badRequest("The base of a role cannot change");
  const name = parseRoleName(body?.name);
  const permissions = effectiveRoleList(current.base, parseGrantablePermissions(body?.permissions, current.base, "permissions"));
  await assertNameFree(name, id);
  await db.doc(`${ROLES_COLLECTION}/${id}`).set(
    { name, permissions, updatedBy: actorUid, updatedAt: FieldValue.serverTimestamp() }, { merge: true },
  );
  forgetCustomRoles();
  return { id, name, base: current.base, permissions };
}

async function loadCustomRolesFresh(): Promise<Map<string, CustomRole>> {
  forgetCustomRoles();
  return loadCustomRoles();
}

/** Deletes a custom role. The accounts that used it go back to the standard role. Returns those accounts. */
export async function deleteCustomRole(id: string): Promise<{ role: CustomRole; uids: string[] }> {
  const role = (await loadCustomRolesFresh()).get(id);
  if (!role) throw notFound("Role not found");
  const users = await db.collection("users").where(`customRoles.${role.base}`, "==", id).get();
  const batch = db.batch();
  users.docs.forEach((doc) => batch.update(doc.ref, { [`customRoles.${role.base}`]: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }));
  batch.delete(db.doc(`${ROLES_COLLECTION}/${id}`));
  await batch.commit();
  forgetCustomRoles();
  return { role, uids: users.docs.map((doc) => doc.id) };
}

/** Saves the custom roles and the changes of one person. Empty values are removed from the profile. */
export async function savePermissionSettings(uid: string, settings: PermissionSettings): Promise<void> {
  const customRoles: Record<string, unknown> = {};
  const permissionChanges: Record<string, unknown> = {};
  for (const level of CUSTOMIZABLE_ROLES) {
    const roleId = settings.customRoles[level];
    customRoles[level] = roleId ?? FieldValue.delete();
    const change = settings.permissionChanges[level];
    permissionChanges[level] = change && (change.added.length > 0 || change.removed.length > 0)
      ? { added: change.added, removed: change.removed }
      : FieldValue.delete();
  }
  await db.doc(`users/${uid}`).set({ customRoles, permissionChanges, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}
