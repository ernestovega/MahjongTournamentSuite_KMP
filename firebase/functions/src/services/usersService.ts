import { FieldValue } from "firebase-admin/firestore";
import type { UserRecord } from "firebase-admin/auth";

import { auth, db } from "../firebase";
import { conflict, notFound } from "../api/httpError";
import type { Role } from "../models/role";

export type GlobalUserRole = "REGULAR" | "SUPERADMIN";

export type TournamentAssignment = {
  tournamentId: string;
  tournamentName: string;
  role: Role;
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

export function getGlobalUserRole(user: Pick<UserRecord, "customClaims">): GlobalUserRole {
  if (user.customClaims?.superadmin === true) return "SUPERADMIN";
  return "REGULAR";
}

export async function getUserProfile(uid: string): Promise<UserProfile> {
  const snap = await db.doc(`users/${uid}`).get();
  if (!snap.exists) {
    throw notFound("User profile not found");
  }

  const email = snap.get("email") as string;
  const alias = String(snap.get("alias") ?? "").trim();
  return { uid, email, alias };
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

export async function listManagedUsers(): Promise<ManagedUser[]> {
  const profiles = await db.collection("users").get();
  const profilesByUid = new Map(profiles.docs.map((doc) => [doc.id, doc.data()]));
  const assignmentsByUid = await listTournamentAssignmentsByUid();
  const users: ManagedUser[] = [];
  let pageToken: string | undefined;

  do {
    const page = await auth.listUsers(1_000, pageToken);
    page.users.forEach((user) => {
      const profile = profilesByUid.get(user.uid);
      const email = user.email ?? String(profile?.email ?? "");
      users.push({
        uid: user.uid,
        email,
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

export async function getManagedUser(uid: string): Promise<ManagedUser> {
  const user = await auth.getUser(uid);
  const profile = await db.doc(`users/${uid}`).get();
  const email = user.email ?? String(profile.get("email") ?? "");
  const alias = String(profile.get("alias") ?? "").trim();
  return {
    uid,
    email,
    alias,
    role: getGlobalUserRole(user),
    disabled: user.disabled,
    tournamentAssignments: await listUserTournamentAssignments(uid),
  };
}

export async function setGlobalUserRole(uid: string, role: GlobalUserRole): Promise<void> {
  const user = await auth.getUser(uid);
  const claims = { ...(user.customClaims ?? {}) };
  delete claims.admin;
  delete claims.superadmin;
  if (role === "SUPERADMIN") claims.superadmin = true;
  await auth.setCustomUserClaims(uid, claims);
}

export async function updateManagedUser(params: {
  uid: string;
  email: string;
  alias: string;
  role: GlobalUserRole;
  tournamentAssignments: TournamentAssignment[];
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
  await syncUserTournamentAssignments(params.uid, params.tournamentAssignments);
}

async function listTournamentAssignmentsByUid(): Promise<Map<string, TournamentAssignment[]>> {
  const result = new Map<string, TournamentAssignment[]>();
  const tournaments = await db.collection("tournaments").get();
  await Promise.all(tournaments.docs.map(async (tournament) => {
    const members = await tournament.ref.collection("members").get();
    members.docs.forEach((member) => {
      const assignment: TournamentAssignment = {
        tournamentId: tournament.id,
        tournamentName: String(tournament.get("name") ?? ""),
        role: member.get("role") as Role,
      };
      result.set(member.id, [...(result.get(member.id) ?? []), assignment]);
    });
  }));
  result.forEach((assignments) => assignments.sort((left, right) => left.tournamentName.localeCompare(right.tournamentName)));
  return result;
}

export async function listUserTournamentAssignments(uid: string): Promise<TournamentAssignment[]> {
  const memberships = await db.collectionGroup("members").where("uid", "==", uid).get();
  const assignments = await Promise.all(memberships.docs.map(async (membership) => {
    const tournamentRef = membership.ref.parent.parent;
    if (!tournamentRef) return null;
    const tournament = await tournamentRef.get();
    if (!tournament.exists) return null;
    return {
      tournamentId: tournament.id,
      tournamentName: String(tournament.get("name") ?? ""),
      role: membership.get("role") as Role,
    } satisfies TournamentAssignment;
  }));
  return assignments
    .filter((assignment): assignment is TournamentAssignment => assignment !== null)
    .sort((left, right) => left.tournamentName.localeCompare(right.tournamentName));
}

export async function syncUserTournamentAssignments(
  uid: string,
  assignments: TournamentAssignment[],
): Promise<void> {
  const desired = new Map(assignments.map((assignment) => [assignment.tournamentId, assignment]));
  const tournamentIds = [...desired.keys()];
  if (tournamentIds.length !== assignments.length) throw conflict("Duplicate tournament assignment");

  const tournamentRefs = tournamentIds.map((id) => db.doc(`tournaments/${id}`));
  const tournamentDocs = tournamentRefs.length > 0 ? await db.getAll(...tournamentRefs) : [];
  if (tournamentDocs.some((document) => !document.exists)) throw notFound("Tournament not found");

  const current = await db.collectionGroup("members").where("uid", "==", uid).get();
  const batch = db.batch();
  let operationCount = 0;
  current.docs.forEach((membership) => {
    const tournamentId = membership.ref.parent.parent?.id;
    if (tournamentId && !desired.has(tournamentId)) {
      batch.delete(membership.ref);
      operationCount++;
    }
  });
  assignments.forEach((assignment) => {
    batch.set(db.doc(`tournaments/${assignment.tournamentId}/members/${uid}`), {
      uid,
      role: assignment.role,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    operationCount++;
  });
  if (operationCount > 0) await batch.commit();
}

export async function setManagedUserDisabled(uid: string, disabled: boolean): Promise<void> {
  await auth.updateUser(uid, { disabled });
  if (disabled) await auth.revokeRefreshTokens(uid);
  await db.doc(`users/${uid}`).set(
    {
      disabled,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}

export async function countEnabledSuperadmins(): Promise<number> {
  let count = 0;
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    count += page.users.filter((user) => !user.disabled && getGlobalUserRole(user) === "SUPERADMIN").length;
    pageToken = page.pageToken;
  } while (pageToken);
  return count;
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
