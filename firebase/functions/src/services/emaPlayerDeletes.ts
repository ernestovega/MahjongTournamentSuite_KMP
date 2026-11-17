import { db, storage } from "../firebase";
import { badRequest } from "../api/httpError";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import { setAccountEmaId, tournamentIdsOfEmaId } from "./playerAccountsService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";

export type EmaPlayerDeleteFailure = "NOT_FOUND" | "UNKNOWN";
export type EmaPlayerDeleteResult = { emaId: string; outcome: "DELETED" | "DISABLED" | "FAILED"; reason?: EmaPlayerDeleteFailure };
export type EmaPlayerTournaments = { emaId: string; tournaments: Array<{ id: string; name: string }> };

/** The server deletes up to 50 EMA players in one request. */
export const MAX_EMA_PLAYER_DELETES = 50;

function checkedIds(emaIds: string[]): string[] {
  const unique = [...new Set(emaIds)];
  if (unique.length === 0) throw badRequest("Choose at least one player");
  if (unique.length > MAX_EMA_PLAYER_DELETES) throw badRequest(`At most ${MAX_EMA_PLAYER_DELETES} players per request`);
  return unique;
}

/** The tournaments where each player has data. Players without data are left out. */
export async function tournamentsOfEmaPlayers(emaIds: string[]): Promise<EmaPlayerTournaments[]> {
  const result: EmaPlayerTournaments[] = [];
  for (const emaId of checkedIds(emaIds)) {
    const ids = [...new Set(await tournamentIdsOfEmaId(emaId))];
    if (ids.length === 0) continue;
    const docs = await db.getAll(...ids.map((id) => db.doc(`tournaments/${id}`)));
    const tournaments = docs
      .map((doc) => ({ id: doc.id, name: typeof doc.get("name") === "string" ? String(doc.get("name")) : doc.id }))
      .sort((left, right) => left.name.localeCompare(right.name));
    result.push({ emaId, tournaments });
  }
  return result;
}

/**
 * Deletes EMA players from the Player base. A player with data in a tournament is not deleted: it is deactivated, so
 * the results keep the name. An account is never deleted here. The account of a deleted player only loses the EMA
 * number, because that EMA player no longer exists. Every player has its own result.
 */
export async function deleteEmaPlayers(emaIds: string[]): Promise<EmaPlayerDeleteResult[]> {
  const unique = checkedIds(emaIds);
  const results: EmaPlayerDeleteResult[] = [];
  let unlinkedAccount = false;
  for (const emaId of unique) {
    const fail = (reason: EmaPlayerDeleteFailure): EmaPlayerDeleteResult => ({ emaId, outcome: "FAILED", reason });
    try {
      const ref = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId);
      const stored = await ref.get();
      if (!stored.exists) {
        results.push(fail("NOT_FOUND"));
        continue;
      }
      if ((await tournamentIdsOfEmaId(emaId)).length > 0) {
        await ref.update({ disabled: true });
        results.push({ emaId, outcome: "DISABLED" });
        continue;
      }
      const owner = (await db.doc(`emaIdIndex/${emaId}`).get()).get("uid");
      if (typeof owner === "string" && owner !== "") {
        await setAccountEmaId({ uid: owner, emaId: null });
        unlinkedAccount = true;
      }
      await ref.delete();
      if (typeof stored.get("photoUrl") === "string") {
        await storage.deleteFiles({ prefix: `playerPhotos/${emaId}.` }).catch(() => undefined);
      }
      results.push({ emaId, outcome: "DELETED" });
    } catch (error) {
      console.error("Delete of an EMA player failed", { emaId, error });
      results.push(fail("UNKNOWN"));
    }
  }
  if (results.some((result) => result.outcome !== "FAILED")) await bumpGlobalDataVersion("emaPlayers");
  if (unlinkedAccount) await bumpGlobalDataVersion("users");
  return results;
}
