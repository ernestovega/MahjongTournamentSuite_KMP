import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { db } from "../firebase";
import { notFound } from "../api/httpError";

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

export type TournamentManifest = {
  resources: Record<TournamentDataResource, VersionStamp>;
  /** The revisions that are shared by all tournaments. They come in the same answer, so one poll sees everything. */
  globalResources: Record<GlobalDataResource, VersionStamp>;
  /** Version of each table that was saved since the manifest exists. The key is `<roundId>_<tableId>`. */
  tableVersions: Record<string, number>;
};

export function tableVersionKey(roundId: number, tableId: number): string {
  return `${roundId}_${tableId}`;
}

export function readTableVersions(value: unknown): Record<string, number> {
  if (value == null || typeof value !== "object") return {};
  const versions: Record<string, number> = {};
  for (const [key, version] of Object.entries(value as Record<string, unknown>)) {
    if (Number.isSafeInteger(version) && Number(version) >= 0) versions[key] = Number(version);
  }
  return versions;
}

/** One read gives both the coarse revisions and the exact version of every saved table. */
export async function getTournamentManifest(tournamentId: string): Promise<TournamentManifest> {
  const [snapshot, global] = await Promise.all([
    db.collection("tournaments").doc(tournamentId).get(),
    getGlobalDataVersions(),
  ]);
  if (!snapshot.exists) throw notFound("Tournament not found");
  const versions = snapshot.get("dataVersions") as Record<string, unknown> | undefined ?? {};
  return {
    globalResources: global,
    resources: {
      members: readVersion(versions.members),
      players: readVersion(versions.players),
      teams: readVersion(versions.teams),
      rounds: readVersion(versions.rounds),
      tables: readVersion(versions.tables),
    },
    tableVersions: readTableVersions(versions.tableVersions),
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

/** Records the new version of one table. Polling clients compare it with the version they hold. */
export function tableVersionUpdate(roundId: number, tableId: number, version: number): Record<string, unknown> {
  return {
    ...tournamentVersionUpdate("tables"),
    [`dataVersions.tableVersions.${tableVersionKey(roundId, tableId)}`]: version,
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
