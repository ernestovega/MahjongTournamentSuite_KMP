import { db } from "../firebase";
import type { Access } from "./accessService";
import { listAllTournaments, mapTournamentDoc, type Tournament } from "./tournamentsService";

/** Firestore takes at most 30 values in one `array-contains-any` filter. */
const IN_FILTER_LIMIT = 30;

/** The ids of the tournaments assigned to an editor. */
export async function memberTournamentIds(uid: string): Promise<Set<string>> {
  const memberships = await db.collectionGroup("members").where("uid", "==", uid).get();
  return new Set(memberships.docs.map((member) => member.ref.parent.parent?.id).filter((id): id is string => id != null));
}

/** The ids of the tournaments that some organizations organize or co-organize. */
export async function organizationTournamentIds(organizations: ReadonlySet<string>): Promise<Set<string>> {
  const organizationIds = [...organizations];
  const ids = new Set<string>();
  for (let start = 0; start < organizationIds.length; start += IN_FILTER_LIMIT) {
    const chunk = organizationIds.slice(start, start + IN_FILTER_LIMIT);
    const snapshot = await db.collection("tournaments").where("organizerIds", "array-contains-any", chunk).select().get();
    snapshot.docs.forEach((document) => ids.add(document.id));
  }
  return ids;
}

/**
 * The tournaments that the management app lists for an account: every tournament for a super admin, the tournaments
 * of its organizations for a club admin, and the assigned ones for an editor. `memberIds` are the assigned ones.
 */
export async function listVisibleTournaments(access: Access): Promise<{ tournaments: Tournament[]; memberIds: Set<string> }> {
  const memberIds = access.editor ? await memberTournamentIds(access.uid) : new Set<string>();
  if (access.superAdmin) return { tournaments: await listAllTournaments(), memberIds };
  const ids = new Set([...memberIds, ...(await organizationTournamentIds(access.adminOrganizations))]);
  if (ids.size === 0) return { tournaments: [], memberIds };
  const snapshots = await db.getAll(...[...ids].map((id) => db.doc(`tournaments/${id}`)));
  const tournaments = await Promise.all(snapshots.filter((snapshot) => snapshot.exists).map((snapshot) => mapTournamentDoc(snapshot)));
  return { tournaments, memberIds };
}

/** The ids of the tournaments that an account manages, or null when it manages every tournament. */
export async function visibleTournamentIdSet(access: Access): Promise<Set<string> | null> {
  if (access.superAdmin) return null;
  const memberIds = access.editor ? await memberTournamentIds(access.uid) : new Set<string>();
  return new Set([...memberIds, ...(await organizationTournamentIds(access.adminOrganizations))]);
}

/** True when some table of the tournament has a seat, a score or a hand. */
export async function tournamentHasResults(tournamentId: string): Promise<boolean> {
  const tables = await db.collection(`tournaments/${tournamentId}/tables`).where("hasProgress", "==", true).limit(1).get();
  return !tables.empty;
}

/** True when the last day of the tournament is before today, in UTC. */
export function tournamentIsPast(eventEndDate: string | null | undefined, today = new Date().toISOString().slice(0, 10)): boolean {
  return typeof eventEndDate === "string" && eventEndDate !== "" && eventEndDate < today;
}
