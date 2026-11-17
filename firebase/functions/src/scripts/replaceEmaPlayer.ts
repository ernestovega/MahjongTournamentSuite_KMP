import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

import { db, firebaseProjectId } from "../firebase";
import { deleteEmaPlayers } from "../services/emaPlayerDeletes";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";
import { assignTournamentPlayer } from "../services/tournamentContentService";
import { runScript } from "./scriptRun";

/**
 * Takes one EMA player out of the database for good: every tournament slot of that player gets another EMA player,
 * and then the player is deleted from the Player base. The account of the player is never deleted: it only loses the
 * EMA number (the rule of 2026-11-17). The tables, hands and team stay on each slot, as when an editor changes the
 * person of a slot in the admin app, and the statistics of each tournament are computed again.
 *
 * Run it from firebase/functions with application default credentials. It previews by default:
 *   npm run build && node lib/scripts/replaceEmaPlayer.js --project=<id> --name="Test Player 1 EMA Guest" --out=<file>
 * The preview lists the players with that name (or with --ema=<id>), their account, the tournaments and slots where
 * they play, and some EMA players that could take their place. To write, pass the replacement and --apply:
 *   ... --ema=<id> --with=<emaId of the replacement> --apply
 * --project must name the project of the credentials (EMA_FIREBASE_PROJECT, default production), as a confirmation.
 * Before it writes, it saves the registry document and the slots in output/replaced-ema-player-<id>-<date>.json.
 */
const args = process.argv.slice(2);
const option = (name: string) => args.find((arg) => arg.startsWith(`--${name}=`))?.slice(name.length + 3).trim() ?? "";
const applyChanges = args.includes("--apply");

type Registry = { id: string; firstName: string; lastName: string; country: string; disabled: boolean };
type Slot = { tournamentId: string; tournamentName: string; status: string; tablesWithProgress: number; slotId: number; team: unknown };

const normalized = (text: string) => text.normalize("NFKD").replace(/[̀-ͯ]/g, "").replace(/\s+/g, " ").trim().toLowerCase();
const fullName = (player: Registry) => `${player.firstName} ${player.lastName}`.trim();

async function loadRegistry(): Promise<Registry[]> {
  const snapshot = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    firstName: String(doc.get("firstName") ?? "").trim(),
    lastName: String(doc.get("lastName") ?? "").trim(),
    country: String(doc.get("country") ?? "").trim(),
    disabled: doc.get("disabled") === true,
  }));
}

async function slotsOf(emaId: string): Promise<Slot[]> {
  const slots = await db.collectionGroup("players").where("assignedEmaId", "==", emaId).get();
  const result: Slot[] = [];
  for (const slot of slots.docs) {
    const tournamentRef = slot.ref.parent.parent;
    if (tournamentRef == null || tournamentRef.parent.id !== "tournaments") continue;
    const tournament = await tournamentRef.get();
    result.push({
      tournamentId: tournamentRef.id,
      tournamentName: String(tournament.get("name") ?? tournamentRef.id),
      status: String(tournament.get("status") ?? ""),
      tablesWithProgress: Number(tournament.get("tablesWithProgress") ?? 0),
      slotId: Number(slot.get("id") ?? slot.id),
      team: slot.get("team") ?? slot.get("teamId") ?? null,
    });
  }
  return result.sort((left, right) => left.tournamentId.localeCompare(right.tournamentId) || left.slotId - right.slotId);
}

async function accountOf(emaId: string): Promise<{ uid: string; email: string | null; alias: string | null } | null> {
  const uid = (await db.doc(`emaIdIndex/${emaId}`).get()).get("uid");
  if (typeof uid !== "string" || uid === "") return null;
  const profile = await db.doc(`users/${uid}`).get();
  const text = (field: string) => (typeof profile.get(field) === "string" ? String(profile.get(field)) : null);
  return { uid, email: text("email"), alias: text("alias") ?? text("displayName") };
}

/** The EMA ids that play in each of the tournaments. */
async function seatedIn(tournamentIds: string[]): Promise<Set<string>> {
  const seated = new Set<string>();
  for (const id of tournamentIds) {
    const players = await db.collection("tournaments").doc(id).collection("players").select("assignedEmaId").get();
    players.docs.forEach((doc) => {
      const emaId = doc.get("assignedEmaId");
      if (typeof emaId === "string" && emaId !== "") seated.add(emaId);
    });
  }
  return seated;
}

async function main() {
  const project = option("project");
  if (project === "" || project !== firebaseProjectId) {
    throw new Error(`Pass --project=${firebaseProjectId} to confirm the project.`);
  }
  const name = option("name");
  const emaOption = option("ema");
  const replacementId = option("with");
  if (name === "" && emaOption === "") throw new Error("Pass --name=\"<first and last name>\" or --ema=<id>.");

  const registry = await loadRegistry();
  const wanted = normalized(name);
  const matches = registry.filter((player) =>
    (emaOption !== "" && player.id === emaOption) || (wanted !== "" && normalized(fullName(player)) === wanted));
  const similar = wanted === ""
    ? []
    : registry.filter((player) => !matches.includes(player) && normalized(fullName(player)).includes("test"));

  const found = [];
  for (const player of matches) {
    found.push({ ...player, name: fullName(player), account: await accountOf(player.id), slots: await slotsOf(player.id) });
  }
  const tournamentIds = [...new Set(found.flatMap((player) => player.slots.map((slot) => slot.tournamentId)))];
  const seated = await seatedIn(tournamentIds);
  const owned = new Set((await db.collection("emaIdIndex").select().get()).docs.map((doc) => doc.id));
  const candidates = registry
    .filter((player) => !player.disabled && !seated.has(player.id) && !matches.includes(player)
      && !normalized(fullName(player)).includes("test"))
    .map((player) => ({ id: player.id, name: fullName(player), country: player.country, hasAccount: owned.has(player.id) }))
    .sort((left, right) => Number(left.hasAccount) - Number(right.hasAccount) || left.name.localeCompare(right.name))
    .slice(0, 40);

  const report = {
    project: firebaseProjectId,
    at: new Date().toISOString(),
    registrySize: registry.length,
    matches: found,
    similarNamesWithTest: similar.map((player) => ({ ...player, name: fullName(player) })),
    candidates,
  };
  const out = option("out");
  if (out !== "") {
    mkdirSync(dirname(out), { recursive: true });
    writeFileSync(out, JSON.stringify(report, null, 2));
  }
  console.log(JSON.stringify(report, null, 2));

  if (!applyChanges) {
    console.log("Preview only. Pass --ema=<id> --with=<emaId> --apply to replace and delete.");
    return;
  }
  if (found.length !== 1) throw new Error(`Exactly one player must match, and ${found.length} match. Use --ema=<id>.`);
  const target = found[0];
  if (target.slots.length > 0) {
    if (replacementId === "") throw new Error("The player plays in tournaments. Pass --with=<emaId of the replacement>.");
    const replacement = registry.find((player) => player.id === replacementId);
    if (replacement == null) throw new Error(`EMA player ${replacementId} is not in the Player base.`);
    if (replacement.disabled) throw new Error(`EMA player ${replacementId} is deactivated.`);
    if (replacement.id === target.id) throw new Error("The replacement is the same player.");
    if (seated.has(replacement.id)) throw new Error(`EMA player ${replacementId} already plays in one of these tournaments.`);
  }

  mkdirSync("output", { recursive: true });
  const backup = `output/replaced-ema-player-${target.id}-${new Date().toISOString().slice(0, 10)}.json`;
  const registryDoc = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(target.id).get();
  writeFileSync(backup, JSON.stringify({ registry: registryDoc.data(), ...target }, null, 2));
  console.log(`Backup: ${backup}`);

  for (const slot of target.slots) {
    await assignTournamentPlayer({ tournamentId: slot.tournamentId, playerId: slot.slotId, emaId: replacementId, uid: null });
    console.log(`  ${slot.tournamentId} slot ${slot.slotId}: ${target.id} -> ${replacementId}`);
  }
  const [result] = await deleteEmaPlayers([target.id]);
  console.log(`  EMA player ${target.id}: ${result.outcome}${result.reason ? ` (${result.reason})` : ""}`);
  if (result.outcome !== "DELETED") process.exitCode = 1;
  return { emaId: target.id, replacement: replacementId || null, slots: target.slots.length, outcome: result.outcome };
}

runScript("replaceEmaPlayer", main, applyChanges);
