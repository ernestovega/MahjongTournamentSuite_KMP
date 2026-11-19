import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest } from "../api/httpError";
import { getGlobalUserRoles, type GlobalUserRole } from "../models/globalRole";
import { forgetAccess, readCountryList, tournamentCountry } from "./accessService";
import { listEnabledSuperAdmins, syncRoleClaims, type RoleSyncResult } from "./usersService";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";

export type TournamentMember = {
  uid: string;
  email: string;
  /** SUPER_ADMIN and COUNTRY_ADMIN manage the tournament without an assignment. EDITOR is assigned to it. */
  role: GlobalUserRole;
  /** True for an editor whose only tournament is this one: removing it makes the account a player. */
  onlyTournament: boolean;
};

/** The enabled country admins of a country. */
async function listCountryAdmins(country: string): Promise<Array<{ uid: string; email: string }>> {
  if (country === "") return [];
  const profiles = await db.collection("users").where("adminCountries", "array-contains", country).get();
  const users = await Promise.all(profiles.docs.map((profile) => auth.getUser(profile.id).catch(() => null)));
  return users
    .filter((user): user is NonNullable<typeof user> => user != null && !user.disabled && getGlobalUserRoles(user).includes("COUNTRY_ADMIN"))
    .map((user) => ({ uid: user.uid, email: user.email ?? "" }));
}

/** Everyone who manages the tournament: the super admins, the country admins of its country and its editors. */
export async function listTournamentMembers(tournamentId: string): Promise<TournamentMember[]> {
  const country = (await tournamentCountry(tournamentId)) ?? "";
  const [membersSnapshot, superAdmins, countryAdmins] = await Promise.all([
    db.collection(`tournaments/${tournamentId}/members`).get(),
    listEnabledSuperAdmins(),
    listCountryAdmins(country),
  ]);
  const implicit = new Set([...superAdmins, ...countryAdmins].map((account) => account.uid));
  const editors = await Promise.all(membersSnapshot.docs.map(async (document) => {
    if (implicit.has(document.id)) return null;
    const user = await auth.getUser(document.id).catch(() => null);
    if (!user || !getGlobalUserRoles(user).includes("EDITOR")) return null;
    const memberships = await db.collectionGroup("members").where("uid", "==", document.id).limit(2).get();
    return {
      uid: document.id,
      email: user.email ?? "",
      role: "EDITOR" as const,
      onlyTournament: memberships.size <= 1,
    };
  }));

  const assignedEditors = editors.filter((member): member is NonNullable<typeof member> => member !== null);
  const result: TournamentMember[] = [
    ...superAdmins.map((account) => ({ ...account, role: "SUPER_ADMIN" as const, onlyTournament: false })),
    ...countryAdmins.filter((account) => !superAdmins.some((admin) => admin.uid === account.uid))
      .map((account) => ({ ...account, role: "COUNTRY_ADMIN" as const, onlyTournament: false })),
    ...assignedEditors,
  ];
  return result.sort((left, right) => left.email.localeCompare(right.email));
}

/**
 * Assigns an account to a tournament as its editor. A player becomes an editor with it. A country admin of another
 * country can be an editor here. A super admin and a country admin of this country already manage the tournament.
 */
export async function upsertTournamentMember(params: {
  tournamentId: string;
  uid: string;
}): Promise<void> {
  const user = await auth.getUser(params.uid);
  if (user.disabled) throw badRequest("A disabled account cannot be assigned");
  const roles = getGlobalUserRoles(user);
  if (roles.includes("SUPER_ADMIN")) {
    throw badRequest("Super admins already have access to every tournament");
  }
  if (roles.includes("COUNTRY_ADMIN")) {
    const country = await tournamentCountry(params.tournamentId);
    const profile = await db.doc(`users/${params.uid}`).get();
    if (country != null && readCountryList(profile.get("adminCountries")).includes(country)) {
      throw badRequest("Country admins already have access to the tournaments of their countries");
    }
  }
  if (!roles.includes("EDITOR") && !roles.includes("COUNTRY_ADMIN")) {
    throw badRequest("A player account cannot be assigned as an editor. Make it an editor in Accounts");
  }

  const ref = db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`);
  await ref.set(
    {
      uid: params.uid,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    },
  );
  forgetAccess(params.uid);
  await syncRoleClaims(params.uid);
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "members"),
    bumpGlobalDataVersion("users"),
    // An editor lists only the tournaments assigned to it.
    bumpGlobalDataVersion("tournaments"),
  ]);
}

/** Ends the assignment of an editor. An editor that loses its last tournament becomes a player, see syncRoleClaims. */
export async function removeTournamentMember(params: {
  tournamentId: string;
  uid: string;
}): Promise<RoleSyncResult> {
  const user = await auth.getUser(params.uid);
  if (getGlobalUserRoles(user).includes("SUPER_ADMIN")) {
    throw badRequest("Super admins cannot be removed from a tournament");
  }
  await db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`).delete();
  forgetAccess(params.uid);
  const result = await syncRoleClaims(params.uid);
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "members"),
    bumpGlobalDataVersion("users"),
    // An editor lists only the tournaments assigned to it.
    bumpGlobalDataVersion("tournaments"),
  ]);
  return result;
}

/** Brings the roles of the editors of a deleted tournament in line: one that lost its last tournament becomes a player. */
export async function applyLostAssignments(uids: string[]): Promise<void> {
  for (const uid of uids) await syncRoleClaims(uid);
}
