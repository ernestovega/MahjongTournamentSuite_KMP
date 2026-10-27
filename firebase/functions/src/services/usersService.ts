import { FieldValue } from "firebase-admin/firestore";

import { auth, db, storage } from "../firebase";
import { badRequest, conflict, forbidden, notFound } from "../api/httpError";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "./dataVersionsService";
import { EMA_PLAYER_REGISTRY_COLLECTION, storePhotoFile, toPlayer } from "./playersService";
import { getGlobalUserRole, getGlobalUserRoles, type GlobalUserRole } from "../models/globalRole";

export type { GlobalUserRole } from "../models/globalRole";

export type TournamentAssignment = {
  tournamentId: string;
  tournamentName: string;
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
};

export const MAX_ALIAS_LENGTH = 60;

export type ManagedUser = UserProfile & {
  photoUrl: string | null;
  hasOwnPhoto: boolean;
  role: GlobalUserRole;
  roles: GlobalUserRole[];
  disabled: boolean;
  tournamentAssignments: TournamentAssignment[];
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
  return { ...profile, ...(await resolvePhoto(userSnap.get("photoUrl"), profile.emaId)) };
}

/** The own photo of the user wins. The photo of the EMA player is the fallback. */
async function resolvePhoto(ownPhoto: unknown, emaId: string | null): Promise<{ photoUrl: string | null; hasOwnPhoto: boolean }> {
  if (typeof ownPhoto === "string" && ownPhoto.length > 0) return { photoUrl: ownPhoto, hasOwnPhoto: true };
  if (emaId == null) return { photoUrl: null, hasOwnPhoto: false };
  const player = await db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${emaId}`).get();
  return { photoUrl: player.exists ? toPlayer(emaId, player.data() ?? {}).photoUrl : null, hasOwnPhoto: false };
}

/** Saves the own photo of a user. A user has one photo only. A new photo replaces the old one. */
export async function setUserPhoto(uid: string, contentType: string, dataBase64: string): Promise<void> {
  await getUserProfile(uid);
  const photoUrl = await storePhotoFile({ folder: "userPhotos", id: uid, contentType, dataBase64 });
  await db.doc(`users/${uid}`).set({ photoUrl, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await bumpGlobalDataVersion("users");
}

/** Removes the own photo of a user. The photo of the EMA player is the photo of the user again. */
export async function clearUserPhoto(uid: string): Promise<void> {
  await getUserProfile(uid);
  await storage.deleteFiles({ prefix: `userPhotos/${uid}.` });
  await db.doc(`users/${uid}`).set({ photoUrl: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await bumpGlobalDataVersion("users");
}

/** Sets the alias that the user chose. The alias is the name that the apps show for the account. */
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

export async function listManagedUsers(
  visibleTournamentIds?: ReadonlySet<string>,
): Promise<ManagedUser[]> {
  const profiles = await db.collection("users").get();
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
        tournamentAssignments: assignmentsByUid.get(user.uid) ?? [],
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
  snaps.forEach((snap, index) => photos.set(ids[index], snap.exists ? toPlayer(ids[index], snap.data() ?? {}).photoUrl : null));
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
    tournamentAssignments: await listUserTournamentAssignments(uid, visibleTournamentIds),
  };
}

export async function setGlobalUserRole(uid: string, role: GlobalUserRole): Promise<void> {
  const user = await auth.getUser(uid);
  const claims: Record<string, unknown> = { ...(user.customClaims ?? {}) };
  delete claims.admin;
  delete claims.editor;
  if (role === "ADMIN") claims.admin = true;
  else if (role === "EDITOR") claims.editor = true;
  else claims.player = true;
  await auth.setCustomUserClaims(uid, claims);
}

export async function updateManagedUser(params: {
  uid: string;
  email: string;
  alias: string;
  role: GlobalUserRole;
  tournamentAssignments: TournamentAssignment[];
  assignmentScope?: ReadonlySet<string>;
}): Promise<void> {
  await auth.updateUser(params.uid, { email: params.email });
  await setGlobalUserRole(params.uid, params.role);
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
  await syncUserTournamentAssignments(
    params.uid,
    params.role === "EDITOR" ? params.tournamentAssignments : [],
    params.role === "EDITOR" ? params.assignmentScope : undefined,
  );
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
  const tournaments = tournamentRefs.length === 0 ? [] : await db.getAll(...tournamentRefs, { fieldMask: ["name"] });
  const namesById = new Map(
    tournaments.filter((tournament) => tournament.exists).map((tournament) => [tournament.id, String(tournament.get("name") ?? "")]),
  );
  memberships.forEach(({ uid, tournamentRef }) => {
    if (!namesById.has(tournamentRef.id)) return;
    const assignment: TournamentAssignment = {
      tournamentId: tournamentRef.id,
      tournamentName: namesById.get(tournamentRef.id) ?? "",
    };
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
    return {
      tournamentId: tournament.id,
      tournamentName: String(tournament.get("name") ?? ""),
    } satisfies TournamentAssignment;
  }));
  return assignments
    .filter((assignment): assignment is TournamentAssignment => assignment !== null)
    .sort((left, right) => left.tournamentName.localeCompare(right.tournamentName));
}

export async function listAssignedTournamentIds(uid: string): Promise<Set<string>> {
  return new Set((await listUserTournamentAssignments(uid)).map((assignment) => assignment.tournamentId));
}

export async function syncUserTournamentAssignments(
  uid: string,
  assignments: TournamentAssignment[],
  scopeTournamentIds?: ReadonlySet<string>,
): Promise<void> {
  const desired = new Map(assignments.map((assignment) => [assignment.tournamentId, assignment]));
  const tournamentIds = [...desired.keys()];
  if (tournamentIds.length !== assignments.length) throw conflict("Duplicate tournament assignment");
  if (scopeTournamentIds && tournamentIds.some((id) => !scopeTournamentIds.has(id))) {
    throw forbidden("Editors can assign accounts only to their own tournaments");
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
  if (operationCount > 0) await batch.commit();
}

export async function setManagedUserDisabled(uid: string, disabled: boolean): Promise<void> {
  await auth.updateUser(uid, { disabled });
  if (disabled) await auth.revokeRefreshTokens(uid);
  await db.doc(`users/${uid}`).set(
    { disabled, updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
}

export async function countEnabledAdmins(): Promise<number> {
  let count = 0;
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    count += page.users.filter((user) => !user.disabled && getGlobalUserRole(user) === "ADMIN").length;
    pageToken = page.pageToken;
  } while (pageToken);
  return count;
}

export async function listEnabledAdmins(): Promise<AdminAccount[]> {
  const admins: AdminAccount[] = [];
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => {
      if (!user.disabled && getGlobalUserRole(user) === "ADMIN") {
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
    if (existing.uid !== excludedUid) throw conflict("Email already in use");
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
