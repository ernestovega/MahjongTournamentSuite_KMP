import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest } from "../api/httpError";
import { getGlobalUserRole, type GlobalUserRole } from "../models/globalRole";
import { listEnabledAdmins } from "./usersService";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";

export type TournamentMember = {
  uid: string;
  email: string;
  role: GlobalUserRole;
};

export async function listTournamentMembers(tournamentId: string): Promise<TournamentMember[]> {
  const [membersSnapshot, admins] = await Promise.all([
    db.collection(`tournaments/${tournamentId}/members`).get(),
    listEnabledAdmins(),
  ]);
  const adminIds = new Set(admins.map((admin) => admin.uid));
  const editors = await Promise.all(membersSnapshot.docs.map(async (document) => {
    if (adminIds.has(document.id)) return null;
    const user = await auth.getUser(document.id).catch(() => null);
    if (!user || getGlobalUserRole(user) !== "EDITOR") return null;
    return {
      uid: document.id,
      email: user.email ?? "",
      role: "EDITOR" as const,
    };
  }));

  const assignedEditors = editors.filter(
    (member): member is NonNullable<typeof member> => member !== null,
  );
  const result: TournamentMember[] = [
    ...admins.map((admin) => ({ ...admin, role: "ADMIN" as const })),
    ...assignedEditors,
  ];
  return result.sort((left, right) => left.email.localeCompare(right.email));
}

export async function upsertTournamentMember(params: {
  tournamentId: string;
  uid: string;
}): Promise<void> {
  const user = await auth.getUser(params.uid);
  if (user.disabled) throw badRequest("A disabled account cannot be assigned");
  if (getGlobalUserRole(user) === "ADMIN") {
    throw badRequest("Admins already have access to every tournament");
  }

  const ref = db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`);
  await ref.set(
    {
      uid: params.uid,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    },
  );
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "members"),
    bumpGlobalDataVersion("users"),
  ]);
}

export async function removeTournamentMember(params: {
  tournamentId: string;
  uid: string;
}): Promise<void> {
  const user = await auth.getUser(params.uid);
  if (getGlobalUserRole(user) === "ADMIN") {
    throw badRequest("Admins cannot be removed from a tournament");
  }
  await db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`).delete();
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "members"),
    bumpGlobalDataVersion("users"),
  ]);
}
