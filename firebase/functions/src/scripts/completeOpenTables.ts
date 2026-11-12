import { db, firebaseProjectId } from "../firebase";
import { refreshTournamentStats } from "../services/statisticsStore";
import { saveTableState } from "../services/tableManagerService";
import { runScript } from "./scriptRun";

const applyChanges = process.argv.includes("--apply");
const tournamentId = process.argv.slice(2).find((arg) => !arg.startsWith("--"));

/**
 * Marks every open table of one tournament as completed in the manual totals mode, with its saved scores as the
 * manual totals, through the same save as the API. The save bumps the table
 * versions, the tournament revision, the statistics digest and the change log, and completes the tournament.
 * Usage: node lib/scripts/completeOpenTables.js <tournamentId> [--apply]
 */
async function main(): Promise<{ tournamentId: string; tables: number }> {
  if (!tournamentId) throw new Error("Give a tournament id");
  const ref = db.collection("tournaments").doc(tournamentId);
  const open = await ref.collection("tables").where("isCompleted", "==", false).get();
  console.log(`${applyChanges ? "Completing" : "Previewing"} ${open.size} open tables of ${tournamentId} in ${firebaseProjectId}.`);
  for (const document of open.docs) {
    const data = document.data();
    const roundId = Number(data.roundId);
    const tableId = Number(data.tableId);
    console.log(`  round ${roundId} table ${tableId} (version ${Number(data.version ?? 0)}, status ${String(data.completionStatus ?? "")})`);
    if (!applyChanges) continue;
    await saveTableState({
      tournamentId, roundId, tableId,
      expectedVersion: Number(data.version ?? 0),
      // The real hands do not add up to the official totals, so the table keeps its totals as manual totals.
      tablePatch: {
        isCompleted: true,
        useTotalsOnly: true,
        manualPlayerEastScore: String(data.playerEastScore ?? ""),
        manualPlayerSouthScore: String(data.playerSouthScore ?? ""),
        manualPlayerWestScore: String(data.playerWestScore ?? ""),
        manualPlayerNorthScore: String(data.playerNorthScore ?? ""),
      },
      handPatches: [],
      actor: { uid: "script:completeOpenTables", name: "completeOpenTables script" },
    });
  }
  if (applyChanges) {
    const refreshed = await refreshTournamentStats(tournamentId);
    const after = (await ref.get()).data() ?? {};
    console.log(`Refreshed the statistics of ${refreshed} tables. Tournament isCompleted = ${after.isCompleted}.`);
  }
  return { tournamentId, tables: open.size };
}

runScript("completeOpenTables", main, applyChanges);
