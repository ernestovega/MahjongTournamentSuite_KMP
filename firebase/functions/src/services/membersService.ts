import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { Role } from "../models/role";

export type TournamentMember = {
  uid: string;
  email: string;
  role: Role;
};

export async function listTournamentMembers(tournamentId: string): Promise<TournamentMember[]> {
  const snap = await db.collection(`tournaments/${tournamentId}/members`).get();
  return Promise.all(snap.docs.map(async (d) => ({
    uid: d.id,
    email: await auth.getUser(d.id).then((user) => user.email ?? "").catch(() => ""),
    role: d.get("role") as Role,
  })));
}

export async function upsertTournamentMember(params: {
  tournamentId: string;
  uid: string;
  role: Role;
}): Promise<void> {
  const ref = db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`);
  await ref.set(
    {
      uid: params.uid,
      role: params.role,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}

export async function removeTournamentMember(params: {
  tournamentId: string;
  uid: string;
}): Promise<void> {
  await db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`).delete();
}
