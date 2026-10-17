import { db } from "../firebase";
import { notFound } from "../api/httpError";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { checkTournamentIntegrity, type IntegrityInput, type IntegrityReport } from "./tournamentIntegrity";

/** Loads one tournament and runs every integrity check. It only reads data. */
export async function runTournamentIntegrityCheck(tournamentId: string): Promise<IntegrityReport> {
  const ref = db.collection("tournaments").doc(tournamentId);
  const [tournament, players, teams, rounds, tables] = await Promise.all([
    ref.get(), ref.collection("players").get(), ref.collection("teams").get(),
    ref.collection("rounds").get(), ref.collection("tables").get(),
  ]);
  if (!tournament.exists) throw notFound("Tournament not found");

  const tableEntries: IntegrityInput["tables"] = [];
  const queue = [...tables.docs];
  // A few reads at a time keep a 550 table tournament fast without flooding the database.
  await Promise.all(Array.from({ length: 12 }, async () => {
    while (queue.length > 0) {
      const table = queue.shift()!;
      // eslint-disable-next-line no-await-in-loop
      const hands = await table.ref.collection("hands").get();
      tableEntries.push({
        id: table.id,
        data: table.data(),
        hands: hands.docs.map((hand) => ({ id: hand.id, data: hand.data() })),
      });
    }
  }));
  tableEntries.sort((a, b) => a.id.localeCompare(b.id, undefined, { numeric: true }));

  const emaIds = [...new Set(players.docs.map((slot) => String(slot.get("assignedEmaId") ?? "").trim()).filter((id) => id !== ""))];
  const registry = new Map<string, { country: string } | null>();
  if (emaIds.length > 0) {
    const documents = await db.getAll(...emaIds.map((id) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(id)));
    documents.forEach((document) => {
      registry.set(document.id, document.exists ? { country: String(document.get("country") ?? "").trim().toUpperCase() } : null);
    });
  }

  return checkTournamentIntegrity(tournamentId, {
    tournament: tournament.data() ?? {},
    players: players.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
    teams: teams.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
    rounds: rounds.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
    tables: tableEntries,
    registry,
  });
}
