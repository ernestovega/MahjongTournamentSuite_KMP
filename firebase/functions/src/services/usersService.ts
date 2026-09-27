import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { conflict, forbidden, notFound } from "../api/httpError";
import { getGlobalUserRole, type GlobalUserRole } from "../models/globalRole";

export type { GlobalUserRole } from "../models/globalRole";

export type TournamentAssignment = {
  tournamentId: string;
  tournamentName: string;
};

export type UserProfile = {
  uid: string;
  email: string;
  alias: string;
};

export type ManagedUser = UserProfile & {
  role: GlobalUserRole;
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
  };
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
  const users: ManagedUser[] = [];
  let pageToken: string | undefined;

  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => {
      const profile = profilesByUid.get(user.uid);
      users.push({
        uid: user.uid,
        email: user.email ?? String(profile?.email ?? ""),
        alias: String(profile?.alias ?? "").trim(),
        role: getGlobalUserRole(user),
        disabled: user.disabled,
        tournamentAssignments: assignmentsByUid.get(user.uid) ?? [],
      });
    });
    pageToken = page.pageToken;
  } while (pageToken);

  return users.sort((left, right) => left.email.localeCompare(right.email));
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
    role: getGlobalUserRole(user),
    disabled: user.disabled,
    tournamentAssignments: await listUserTournamentAssignments(uid, visibleTournamentIds),
  };
}

export async function setGlobalUserRole(uid: string, role: GlobalUserRole): Promise<void> {
  const user = await auth.getUser(uid);
  const claims = { ...(user.customClaims ?? {}) };
  delete claims.admin;
  delete claims.superadmin;
  if (role === "ADMIN") claims.admin = true;
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
      emaId: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  await syncUserTournamentAssignments(
    params.uid,
    params.role === "ADMIN" ? [] : params.tournamentAssignments,
    params.role === "ADMIN" ? undefined : params.assignmentScope,
  );
}

async function listTournamentAssignmentsByUid(
  visibleTournamentIds?: ReadonlySet<string>,
): Promise<Map<string, TournamentAssignment[]>> {
  const result = new Map<string, TournamentAssignment[]>();
  const tournaments = await db.collection("tournaments").get();
  await Promise.all(tournaments.docs.map(async (tournament) => {
    if (visibleTournamentIds && !visibleTournamentIds.has(tournament.id)) return;
    const members = await tournament.ref.collection("members").get();
    members.docs.forEach((member) => {
      const assignment: TournamentAssignment = {
        tournamentId: tournament.id,
        tournamentName: String(tournament.get("name") ?? ""),
      };
      result.set(member.id, [...(result.get(member.id) ?? []), assignment]);
    });
  }));
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
  current.docs.forEach((membership) => {
    const tournamentId = membership.ref.parent.parent?.id;
    const isInScope = tournamentId && (!scopeTournamentIds || scopeTournamentIds.has(tournamentId));
    if (tournamentId && isInScope && !desired.has(tournamentId)) {
      batch.delete(membership.ref);
      operationCount++;
    }
  });
  assignments.forEach((assignment) => {
    batch.set(db.doc(`tournaments/${assignment.tournamentId}/members/${uid}`), {
      uid,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    });
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
