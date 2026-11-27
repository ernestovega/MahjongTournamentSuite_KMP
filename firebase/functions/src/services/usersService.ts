import { FieldValue } from "firebase-admin/firestore";

import { auth, db, storage } from "../firebase";
import { badRequest, conflict, forbidden, notFound } from "../api/httpError";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "./dataVersionsService";
import { sendPasswordResetEmail } from "./firebaseAuthRest";
import { EMA_PLAYER_REGISTRY_COLLECTION, readAccountPhoto, storePhotoFile, toPlayer } from "./playersService";
import { getGlobalUserRole, getGlobalUserRoles, type GlobalUserRole } from "../models/globalRole";
import { forgetAccess, readOrganizationList } from "./accessService";
import { readOrganizerIds } from "./organizationsService";
import { PERMISSIONS, type CustomizableRole, type Permission } from "../models/permissions";
import { effectivePermissions, loadCustomRoles, readPermissionSettings, type CustomRole, type PermissionSettings } from "./customPermissions";

export type { GlobalUserRole } from "../models/globalRole";

export type TournamentAssignment = {
  tournamentId: string;
  tournamentName: string;
  /** The organizers of the tournament. The organizations of an editor are the organizers of its tournaments. */
  organizerIds?: string[];
};

export type UserProfile = {
  uid: string;
  email: string;
  alias: string;
  emaId: string | null;
};

/**
 * The profile that the signed-in user sees. The photo is the own photo of the user.
 * Without it, the photo is the one of the EMA player of the account. Without both, it is null.
 */
export type OwnProfile = UserProfile & {
  photoUrl: string | null;
  hasOwnPhoto: boolean;
  /**
   * False when the person chose not to show a photo publicly: rankings, statistics and registrations then show no photo,
   * neither the own one nor the one of the EMA player. The person still sees its photo in its profile.
   */
  publicPhoto: boolean;
};

export const MAX_ALIAS_LENGTH = 60;

export type ManagedUser = UserProfile & {
  photoUrl: string | null;
  hasOwnPhoto: boolean;
  role: GlobalUserRole;
  roles: GlobalUserRole[];
  disabled: boolean;
  /** True when the account was disabled because it lost its last role (syncRoleClaims), not by a super admin. */
  disabledWithoutRole: boolean;
  tournamentAssignments: TournamentAssignment[];
  /** The organizations of a club admin. Empty for the other roles. */
  adminOrganizations: string[];
  /** The custom roles and the changes of single permissions of the account. See customPermissions.ts. */
  permissionSettings: PermissionSettings;
  /** What the account may do as an editor and as a club admin, with its custom roles and its changes. */
  rolePermissions: Record<CustomizableRole, Permission[]>;
};

export type AdminAccount = {
  uid: string;
  email: string;
};

export async function getUserProfile(uid: string): Promise<UserProfile> {
  const snap = await db.doc(`users/${uid}`).get();
  if (!snap.exists) throw notFound("User profile not found");
  return {
    uid,
    email: String(snap.get("email") ?? ""),
    alias: String(snap.get("alias") ?? "").trim(),
    emaId: readEmaId(snap.get("emaId")),
  };
}

export async function getOwnProfile(uid: string): Promise<OwnProfile> {
  const [profile, userSnap] = await Promise.all([getUserProfile(uid), db.doc(`users/${uid}`).get()]);
  return {
    ...profile,
    ...(await resolvePhoto(userSnap.get("photoUrl"), profile.emaId)),
    publicPhoto: readAccountPhoto(userSnap.data()).publicPhoto,
  };
}

/** The own photo of the user wins. The photo of the EMA player is the fallback. */
async function resolvePhoto(ownPhoto: unknown, emaId: string | null): Promise<{ photoUrl: string | null; hasOwnPhoto: boolean }> {
  if (typeof ownPhoto === "string" && ownPhoto.length > 0) return { photoUrl: ownPhoto, hasOwnPhoto: true };
  if (emaId == null) return { photoUrl: null, hasOwnPhoto: false };
  const player = await db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${emaId}`).get();
  return { photoUrl: player.exists ? toPlayer(emaId, player.data() as FirebaseFirestore.DocumentData).photoUrl : null, hasOwnPhoto: false };
}

/** Saves the own photo of a user. A user has one photo only. A new photo replaces the old one. */
export async function setUserPhoto(uid: string, contentType: string, dataBase64: string): Promise<void> {
  await getUserProfile(uid);
  const photoUrl = await storePhotoFile({ folder: "userPhotos", id: uid, contentType, dataBase64 });
  await db.doc(`users/${uid}`).set({ photoUrl, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await photoChanged();
}

/** The own photo is also the public photo of the person, so the apps read the registry again. */
async function photoChanged(): Promise<void> {
  await bumpGlobalDataVersion("users");
  await bumpGlobalDataVersion("emaPlayers");
}

/**
 * Whether the person shows a photo publicly: the own photo, or else the one of the EMA player. With false, rankings,
 * statistics and registrations show no photo of the person.
 */
export async function setOwnPublicPhoto(uid: string, rawVisible: unknown): Promise<void> {
  if (typeof rawVisible !== "boolean") throw badRequest("publicPhoto must be true or false");
  await getUserProfile(uid);
  await db.doc(`users/${uid}`).set(
    { publicPhoto: rawVisible ? FieldValue.delete() : false, updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
  await photoChanged();
}

/** Removes the own photo of a user. The photo of the EMA player is the photo of the user again. */
export async function clearUserPhoto(uid: string): Promise<void> {
  await getUserProfile(uid);
  await storage.deleteFiles({ prefix: `userPhotos/${uid}.` });
  await db.doc(`users/${uid}`).set({ photoUrl: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await photoChanged();
}

/**
 * Sets the alias that the user chose. The alias is private: only the person and the organizers see it, to tell apart
 * people with the same name. The public routes never show it.
 */
export async function updateOwnAlias(uid: string, rawAlias: unknown): Promise<void> {
  const alias = typeof rawAlias === "string" ? rawAlias.trim().replace(/\s+/g, " ") : "";
  if (alias.length === 0 || alias.length > MAX_ALIAS_LENGTH) {
    throw badRequest(`The alias needs 1 to ${MAX_ALIAS_LENGTH} characters`);
  }
  await db.doc(`users/${uid}`).set({ alias, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await bumpGlobalDataVersion("users");
}

function readEmaId(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

export async function createUserProfile(uid: string, email: string, alias = ""): Promise<void> {
  await db.doc(`users/${uid}`).set(
    {
      uid,
      email,
      alias: alias.trim(),
      contactEmail: email,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}

function permissionFieldsOf(
  profile: Record<string, unknown> | undefined,
  customRoles: ReadonlyMap<string, CustomRole>,
): Pick<ManagedUser, "permissionSettings" | "rolePermissions"> {
  const permissionSettings = readPermissionSettings(profile);
  const list = (level: CustomizableRole) => PERMISSIONS.filter((permission) =>
    effectivePermissions(level, permissionSettings, customRoles).has(permission));
  return { permissionSettings, rolePermissions: { EDITOR: list("EDITOR"), CLUB_ADMIN: list("CLUB_ADMIN") } };
}

export async function listManagedUsers(
  visibleTournamentIds?: ReadonlySet<string>,
): Promise<ManagedUser[]> {
  const profiles = await db.collection("users").get();
  const customRoles = await loadCustomRoles();
  const profilesByUid = new Map(profiles.docs.map((doc) => [doc.id, doc.data()]));
  const assignmentsByUid = await listTournamentAssignmentsByUid(visibleTournamentIds);
  const emaPhotos = await loadEmaPhotos(profiles.docs.map((doc) => readEmaId(doc.get("emaId"))));
  const users: ManagedUser[] = [];
  let pageToken: string | undefined;

  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => {
      const profile = profilesByUid.get(user.uid);
      users.push({
        ...photoOf(profile?.photoUrl, readEmaId(profile?.emaId), emaPhotos),
        uid: user.uid,
        email: user.email ?? String(profile?.email ?? ""),
        alias: String(profile?.alias ?? "").trim(),
        emaId: readEmaId(profile?.emaId),
        role: getGlobalUserRole(user),
        roles: getGlobalUserRoles(user),
        disabled: user.disabled,
        disabledWithoutRole: user.disabled && profile?.disabledReason === DISABLED_WITHOUT_ROLE,
        tournamentAssignments: assignmentsByUid.get(user.uid) ?? [],
        adminOrganizations: getGlobalUserRoles(user).includes("CLUB_ADMIN") ? readOrganizationList(profile?.adminOrganizations) : [],
        ...permissionFieldsOf(profile, customRoles),
      });
    });
    pageToken = page.pageToken;
  } while (pageToken);

  return users.sort((left, right) => left.email.localeCompare(right.email));
}

/** The photos of the EMA players of some accounts, by EMA ID. */
async function loadEmaPhotos(emaIds: Array<string | null>): Promise<Map<string, string | null>> {
  const ids = [...new Set(emaIds.filter((id): id is string => id != null))];
  const photos = new Map<string, string | null>();
  if (ids.length === 0) return photos;
  const snaps = await db.getAll(...ids.map((id) => db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${id}`)));
  snaps.forEach((snap, index) => photos.set(ids[index], snap.exists ? toPlayer(ids[index], snap.data() as FirebaseFirestore.DocumentData).photoUrl : null));
  return photos;
}

function photoOf(
  ownPhoto: unknown,
  emaId: string | null,
  emaPhotos: Map<string, string | null>,
): { photoUrl: string | null; hasOwnPhoto: boolean } {
  if (typeof ownPhoto === "string" && ownPhoto.length > 0) return { photoUrl: ownPhoto, hasOwnPhoto: true };
  return { photoUrl: emaId == null ? null : emaPhotos.get(emaId) ?? null, hasOwnPhoto: false };
}

export async function getManagedUser(
  uid: string,
  visibleTournamentIds?: ReadonlySet<string>,
): Promise<ManagedUser> {
  const user = await auth.getUser(uid);
  const profile = await db.doc(`users/${uid}`).get();
  return {
    uid,
    email: user.email ?? String(profile.get("email") ?? ""),
    alias: String(profile.get("alias") ?? "").trim(),
    emaId: readEmaId(profile.get("emaId")),
    ...(await resolvePhoto(profile.get("photoUrl"), readEmaId(profile.get("emaId")))),
    role: getGlobalUserRole(user),
    roles: getGlobalUserRoles(user),
    disabled: user.disabled,
    disabledWithoutRole: user.disabled && profile.get("disabledReason") === DISABLED_WITHOUT_ROLE,
    tournamentAssignments: await listUserTournamentAssignments(uid, visibleTournamentIds),
    adminOrganizations: getGlobalUserRoles(user).includes("CLUB_ADMIN") ? readOrganizationList(profile.get("adminOrganizations")) : [],
    ...permissionFieldsOf(profile.data(), await loadCustomRoles()),
  };
}

/**
 * Sets the main role of an account and keeps its player capability. A club admin gets its organizations. Scripts and
 * tests use it directly. The routes check the rules first and then call it with the result.
 */
export async function setGlobalUserRole(uid: string, role: GlobalUserRole, adminOrganizations: string[] = []): Promise<void> {
  const user = await auth.getUser(uid);
  const claims: Record<string, unknown> = { ...(user.customClaims ?? {}) };
  delete claims.superAdmin;
  delete claims.clubAdmin;
  delete claims.countryAdmin;
  delete claims.editor;
  const organizations = role === "CLUB_ADMIN" ? readOrganizationList(adminOrganizations) : [];
  if (role === "CLUB_ADMIN" && organizations.length === 0) throw badRequest("A club admin needs at least one organization");
  if (role === "SUPER_ADMIN") claims.superAdmin = true;
  else if (role === "CLUB_ADMIN") claims.clubAdmin = true;
  else if (role === "EDITOR") claims.editor = true;
  else claims.player = true;
  await db.doc(`users/${uid}`).set(
    {
      adminOrganizations: organizations.length > 0 ? organizations : FieldValue.delete(),
      adminCountries: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  await auth.setCustomUserClaims(uid, claims);
  forgetAccess(uid);
  // The role decides which tournaments the account lists, so the cached lists are old.
  await bumpGlobalDataVersion("tournaments");
}

/** The `disabledReason` of an account that syncRoleClaims disabled. A super admin that enables or disables clears it. */
const DISABLED_WITHOUT_ROLE = "NO_ROLE";

/** What happened to an account after it lost an organization or a tournament. */
export type RoleSyncResult = "UNCHANGED" | "BECAME_PLAYER" | "DISABLED";

/**
 * Brings the role claims of an account in line with its data: the editor role holds while the account has an
 * assigned tournament, the club admin role while it has an organization. An account that keeps no role becomes a
 * player when it has an EMA id. Without one it cannot sign in to anything, so it is disabled, and a super admin can
 * enable it again with a new role or delete it.
 */
export async function syncRoleClaims(uid: string): Promise<RoleSyncResult> {
  const user = await auth.getUser(uid).catch(() => null);
  if (!user) return "UNCHANGED";
  const claims: Record<string, unknown> = { ...(user.customClaims ?? {}) };
  if (claims.superAdmin === true) return "UNCHANGED";
  const [profile, memberships] = await Promise.all([
    db.doc(`users/${uid}`).get(),
    db.collectionGroup("members").where("uid", "==", uid).limit(1).get(),
  ]);
  const hadStaffRole = claims.clubAdmin === true || claims.countryAdmin === true || claims.editor === true;
  const clubAdmin = readOrganizationList(profile.get("adminOrganizations")).length > 0;
  const editor = !memberships.empty;
  delete claims.clubAdmin;
  delete claims.countryAdmin;
  delete claims.editor;
  if (clubAdmin) claims.clubAdmin = true;
  if (editor) claims.editor = true;
  const before = JSON.stringify(user.customClaims ?? {});
  let result: RoleSyncResult = "UNCHANGED";
  if (hadStaffRole && !clubAdmin && !editor) {
    if (readEmaId(profile.get("emaId")) != null) {
      claims.player = true;
      result = "BECAME_PLAYER";
    } else {
      result = "DISABLED";
    }
  }
  if (JSON.stringify(claims) !== before) await auth.setCustomUserClaims(uid, claims);
  if (result === "DISABLED" && !user.disabled) {
    await setManagedUserDisabled(uid, true);
    // A club admin may enable it again by giving it a tournament of its organizations (PUT /admin/users/:uid).
    await db.doc(`users/${uid}`).set({ disabledReason: DISABLED_WITHOUT_ROLE }, { merge: true });
  }
  forgetAccess(uid);
  if (result !== "UNCHANGED") await bumpGlobalDataVersion("users");
  return result;
}

/** Saves the organizations of a club admin. Call syncRoleClaims after it to bring its role claims in line. */
export async function setAdminOrganizations(uid: string, organizations: string[]): Promise<void> {
  const list = readOrganizationList(organizations);
  await db.doc(`users/${uid}`).set(
    { adminOrganizations: list.length > 0 ? list : FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
  forgetAccess(uid);
}

/** Saves the email, the alias, the role, the organizations and the tournaments of an account that a route checked. */
export async function updateManagedUser(params: {
  uid: string;
  email: string;
  alias: string;
  role: GlobalUserRole;
  adminOrganizations: string[];
  tournamentAssignments: TournamentAssignment[];
}): Promise<RoleSyncResult> {
  await auth.updateUser(params.uid, { email: params.email });
  await db.doc(`users/${params.uid}`).set(
    {
      uid: params.uid,
      email: params.email,
      alias: params.alias.trim(),
      contactEmail: params.email,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  await setGlobalUserRole(params.uid, params.role, params.adminOrganizations);
  await syncUserTournamentAssignments(params.uid, params.role === "SUPER_ADMIN" || params.role === "PLAYER" ? [] : params.tournamentAssignments);
  return syncRoleClaims(params.uid);
}

async function listTournamentAssignmentsByUid(
  visibleTournamentIds?: ReadonlySet<string>,
): Promise<Map<string, TournamentAssignment[]>> {
  const result = new Map<string, TournamentAssignment[]>();
  // One query reads the members of all tournaments. Then one read gets the name of each tournament that has members.
  const members = await db.collectionGroup("members").get();
  const memberships = members.docs.flatMap((member) => {
    const tournamentRef = member.ref.parent.parent;
    if (!tournamentRef || (visibleTournamentIds && !visibleTournamentIds.has(tournamentRef.id))) return [];
    return [{ uid: member.id, tournamentRef }];
  });
  const tournamentRefs = [...new Map(memberships.map(({ tournamentRef }) => [tournamentRef.id, tournamentRef])).values()];
  const tournaments = tournamentRefs.length === 0 ? [] : await db.getAll(...tournamentRefs, { fieldMask: ["name", "organizerIds"] });
  const assignmentsById = new Map(tournaments.filter((tournament) => tournament.exists).map((tournament): [string, TournamentAssignment] => [
    tournament.id,
    { tournamentId: tournament.id, tournamentName: String(tournament.get("name") ?? ""), organizerIds: readOrganizerIds(tournament.get("organizerIds")) },
  ]));
  memberships.forEach(({ uid, tournamentRef }) => {
    const assignment = assignmentsById.get(tournamentRef.id);
    if (assignment == null) return;
    result.set(uid, [...(result.get(uid) ?? []), assignment]);
  });
  result.forEach((assignments) => assignments.sort(
    (left, right) => left.tournamentName.localeCompare(right.tournamentName),
  ));
  return result;
}

export async function listUserTournamentAssignments(
  uid: string,
  visibleTournamentIds?: ReadonlySet<string>,
): Promise<TournamentAssignment[]> {
  const memberships = await db.collectionGroup("members").where("uid", "==", uid).get();
  const assignments = await Promise.all(memberships.docs.map(async (membership) => {
    const tournamentRef = membership.ref.parent.parent;
    if (!tournamentRef || (visibleTournamentIds && !visibleTournamentIds.has(tournamentRef.id))) return null;
    const tournament = await tournamentRef.get();
    if (!tournament.exists) return null;
    const assignment: TournamentAssignment = {
      tournamentId: tournament.id,
      tournamentName: String(tournament.get("name") ?? ""),
      organizerIds: readOrganizerIds(tournament.get("organizerIds")),
    };
    return assignment;
  }));
  return assignments
    .filter((assignment): assignment is TournamentAssignment => assignment !== null)
    .sort((left, right) => left.tournamentName.localeCompare(right.tournamentName));
}

export async function syncUserTournamentAssignments(
  uid: string,
  assignments: TournamentAssignment[],
  scopeTournamentIds?: ReadonlySet<string>,
): Promise<void> {
  const desired = new Map(assignments.map((assignment) => [assignment.tournamentId, assignment]));
  const tournamentIds = [...desired.keys()];
  if (tournamentIds.length !== assignments.length) throw conflict("Duplicate tournament assignment", { code: "DUPLICATE_TOURNAMENT" });
  if (scopeTournamentIds && tournamentIds.some((id) => !scopeTournamentIds.has(id))) {
    throw forbidden("You can assign accounts only to the tournaments that you manage");
  }

  const tournamentRefs = tournamentIds.map((id) => db.doc(`tournaments/${id}`));
  const tournamentDocs = tournamentRefs.length > 0 ? await db.getAll(...tournamentRefs) : [];
  if (tournamentDocs.some((document) => !document.exists)) throw notFound("Tournament not found");

  const current = await db.collectionGroup("members").where("uid", "==", uid).get();
  const batch = db.batch();
  let operationCount = 0;
  const affectedTournamentIds = new Set<string>();
  current.docs.forEach((membership) => {
    const tournamentId = membership.ref.parent.parent?.id;
    const isInScope = tournamentId && (!scopeTournamentIds || scopeTournamentIds.has(tournamentId));
    if (tournamentId && isInScope && !desired.has(tournamentId)) {
      batch.delete(membership.ref);
      affectedTournamentIds.add(tournamentId);
      operationCount++;
    }
  });
  assignments.forEach((assignment) => {
    batch.set(db.doc(`tournaments/${assignment.tournamentId}/members/${uid}`), {
      uid,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    });
    affectedTournamentIds.add(assignment.tournamentId);
    operationCount++;
  });
  affectedTournamentIds.forEach((tournamentId) => {
    batch.update(db.doc(`tournaments/${tournamentId}`), tournamentVersionUpdate("members"));
    operationCount++;
  });
  if (operationCount > 0) {
    await batch.commit();
    forgetAccess(uid);
    // An editor lists only the tournaments assigned to it, so the cached lists are old.
    await bumpGlobalDataVersion("tournaments");
  }
}

export async function setManagedUserDisabled(uid: string, disabled: boolean): Promise<void> {
  await auth.updateUser(uid, { disabled });
  if (disabled) await auth.revokeRefreshTokens(uid);
  forgetAccess(uid);
  await db.doc(`users/${uid}`).set(
    { disabled, disabledReason: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
}

/**
 * Deletes an account for good: the sign-in, the profile (email, alias), the own photo and the editor assignments.
 * Clear the EMA id first with setAccountEmaId. The EMA player and the tournament results stay.
 */
export async function deleteManagedUser(uid: string): Promise<void> {
  await auth.getUser(uid);
  await syncUserTournamentAssignments(uid, []);
  const profile = await db.doc(`users/${uid}`).get();
  const photo = readAccountPhoto(profile.data());
  if (typeof profile.get("photoUrl") === "string") await storage.deleteFiles({ prefix: `userPhotos/${uid}.` });
  await db.doc(`users/${uid}`).delete();
  await auth.deleteUser(uid);
  if (photo.photoUrl != null || !photo.publicPhoto) await photoChanged();
  else await bumpGlobalDataVersion("users");
}

export async function countEnabledSuperAdmins(): Promise<number> {
  let count = 0;
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    count += page.users.filter((user) => !user.disabled && getGlobalUserRole(user) === "SUPER_ADMIN").length;
    pageToken = page.pageToken;
  } while (pageToken);
  return count;
}

export async function listEnabledSuperAdmins(): Promise<AdminAccount[]> {
  const admins: AdminAccount[] = [];
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => {
      if (!user.disabled && getGlobalUserRole(user) === "SUPER_ADMIN") {
        admins.push({ uid: user.uid, email: user.email ?? "" });
      }
    });
    pageToken = page.pageToken;
  } while (pageToken);
  return admins;
}

export async function assertEmailAvailable(email: string, excludedUid?: string): Promise<void> {
  try {
    const existing = await auth.getUserByEmail(email);
    if (existing.uid !== excludedUid) throw conflict("Email already in use", { code: "EMAIL_IN_USE" });
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (code !== "auth/user-not-found") throw error;
  }
}

/** Adds the player capability to an account and keeps the other claims. */
export async function grantPlayerCapability(uid: string): Promise<void> {
  const user = await auth.getUser(uid);
  await auth.setCustomUserClaims(uid, { ...(user.customClaims ?? {}), player: true });
}

/** Removes the player capability from an account and keeps the other claims. */
export async function revokePlayerCapability(uid: string): Promise<void> {
  const user = await auth.getUser(uid);
  const claims: Record<string, unknown> = { ...(user.customClaims ?? {}) };
  delete claims.player;
  await auth.setCustomUserClaims(uid, claims);
}

export type PasswordResetFailure = "NOT_FOUND" | "NO_EMAIL" | "DISABLED" | "EMAIL_FAILED";
export type PasswordResetResult = { uid: string; outcome: "SENT" | "FAILED"; reason?: PasswordResetFailure };

export const MAX_PASSWORD_RESETS = 200;
const PASSWORD_RESET_CONCURRENCY = 5;

/**
 * Sends the password reset email to many accounts. Every account has its own result: a disabled account, an account
 * with no email or an unknown account fails alone, and the others still get the email.
 */
export async function sendPasswordResets(uids: string[]): Promise<PasswordResetResult[]> {
  const unique = [...new Set(uids)];
  if (unique.length === 0) throw badRequest("Choose at least one account");
  if (unique.length > MAX_PASSWORD_RESETS) throw badRequest(`At most ${MAX_PASSWORD_RESETS} accounts per request`);
  const results: PasswordResetResult[] = new Array(unique.length);
  let next = 0;
  const worker = async () => {
    while (next < unique.length) {
      const index = next++;
      const uid = unique[index];
      const fail = (reason: PasswordResetFailure): PasswordResetResult => ({ uid, outcome: "FAILED", reason });
      try {
        const user = await auth.getUser(uid).catch((error: { code?: string }) => {
          if (error.code === "auth/user-not-found") return null;
          throw error;
        });
        if (user == null) results[index] = fail("NOT_FOUND");
        else if (user.disabled) results[index] = fail("DISABLED");
        else if (!user.email) results[index] = fail("NO_EMAIL");
        else {
          await sendPasswordResetEmail(user.email);
          results[index] = { uid, outcome: "SENT" };
        }
      } catch (error) {
        console.error("Password reset email failed", { uid, error });
        results[index] = fail("EMAIL_FAILED");
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(PASSWORD_RESET_CONCURRENCY, unique.length) }, worker));
  return results;
}
