import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { db } from "../firebase";

export type GlobalDataResource = "tournaments" | "emaPlayers" | "countries" | "users";
export type TournamentDataResource = "members" | "players" | "teams" | "rounds" | "tables";

type VersionStamp = {
  revision: number;
  changedAt: string | null;
};

function toIsoString(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

function readVersion(value: unknown): VersionStamp {
  const data = value != null && typeof value === "object" ? value as Record<string, unknown> : {};
  return {
    revision: Number.isSafeInteger(data.revision) ? Number(data.revision) : 0,
    changedAt: toIsoString(data.changedAt),
  };
}

export async function getGlobalDataVersions(): Promise<Record<GlobalDataResource, VersionStamp>> {
  const snapshot = await db.collection("_meta").doc("dataVersions").get();
  const resources = snapshot.get("resources") as Record<string, unknown> | undefined ?? {};
  return {
    tournaments: readVersion(resources.tournaments),
    emaPlayers: readVersion(resources.emaPlayers),
    countries: readVersion(resources.countries),
    users: readVersion(resources.users),
  };
}

export async function getTournamentDataVersions(
  tournamentId: string,
): Promise<Record<TournamentDataResource, VersionStamp>> {
  const snapshot = await db.collection("tournaments").doc(tournamentId).get();
  const resources = snapshot.get("dataVersions") as Record<string, unknown> | undefined ?? {};
  return {
    members: readVersion(resources.members),
    players: readVersion(resources.players),
    teams: readVersion(resources.teams),
    rounds: readVersion(resources.rounds),
    tables: readVersion(resources.tables),
  };
}

export function globalVersionUpdate(resource: GlobalDataResource): Record<string, unknown> {
  return {
    resources: {
      [resource]: {
        revision: FieldValue.increment(1),
        changedAt: FieldValue.serverTimestamp(),
      },
    },
  };
}

export function tournamentVersionUpdate(resource: TournamentDataResource): Record<string, unknown> {
  return {
    [`dataVersions.${resource}.revision`]: FieldValue.increment(1),
    [`dataVersions.${resource}.changedAt`]: FieldValue.serverTimestamp(),
  };
}

export async function bumpGlobalDataVersion(resource: GlobalDataResource): Promise<void> {
  await db.collection("_meta").doc("dataVersions").set(globalVersionUpdate(resource), { merge: true });
}

export async function bumpTournamentDataVersion(
  tournamentId: string,
  resource: TournamentDataResource,
): Promise<void> {
  await db.collection("tournaments").doc(tournamentId).update(tournamentVersionUpdate(resource));
}
