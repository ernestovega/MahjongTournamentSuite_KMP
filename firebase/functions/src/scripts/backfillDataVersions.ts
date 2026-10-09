import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";
import { calculateTableCompletionStatus, hasFourValidScores, hasValidTablePoints, calculateHandSummary } from "../services/tournamentContentRules";

const applyChanges = process.argv.includes("--apply");

function hasText(value: unknown): boolean {
  return String(value ?? "").trim().length > 0;
}

async function backfill(): Promise<void> {
  const tournaments = await db.collection("tournaments").get();
  const [hands, ...tablesByTournament] = await Promise.all([
    db.collectionGroup("hands").get(),
    ...tournaments.docs.map((tournament) => tournament.ref.collection("tables").get()),
  ]);
  const handsByTable = new Map<string, FirebaseFirestore.QueryDocumentSnapshot[]>();
  for (const hand of hands.docs) {
    const tablePath = hand.ref.parent.parent?.path;
    if (!tablePath) continue;
    const tableHands = handsByTable.get(tablePath) ?? [];
    tableHands.push(hand);
    handsByTable.set(tablePath, tableHands);
  }
  let pending: Array<Promise<FirebaseFirestore.WriteResult>> = [];
  let tableCount = 0;

  console.log(`${applyChanges ? "Applying" : "Previewing"} data version backfill for ${firebaseProjectId}.`);

  const flush = async () => {
    await Promise.all(pending);
    pending = [];
  };

  for (const [tournamentIndex, tournament] of tournaments.docs.entries()) {
    const tournamentUpdates: Record<string, unknown> = {};
    for (const resource of ["members", "players", "teams", "rounds", "tables"] as const) {
      if (!Number.isSafeInteger(tournament.get(`dataVersions.${resource}.revision`))) {
        tournamentUpdates[`dataVersions.${resource}.revision`] = 1;
        tournamentUpdates[`dataVersions.${resource}.changedAt`] = FieldValue.serverTimestamp();
      }
    }
    if (applyChanges && Object.keys(tournamentUpdates).length > 0) {
      pending.push(tournament.ref.update(tournamentUpdates));
    }

    const tables = tablesByTournament[tournamentIndex];
    for (const table of tables.docs) {
      const tableHands = handsByTable.get(table.ref.path) ?? [];
      const hasHandProgress = tableHands.some((hand) => {
        const data = hand.data();
        return [
          data.playerWinnerId, data.playerLooserId, data.handScore, data.playerEastPenalty,
          data.playerSouthPenalty, data.playerWestPenalty, data.playerNorthPenalty,
        ].some(hasText) || Boolean(data.isChickenHand) || Boolean(data.isDone);
      });
      const hasTableProgress = [
        table.get("playerEastId"), table.get("playerSouthId"), table.get("playerWestId"), table.get("playerNorthId"),
        table.get("playerEastScore"), table.get("playerSouthScore"), table.get("playerWestScore"), table.get("playerNorthScore"),
        table.get("playerEastPoints"), table.get("playerSouthPoints"), table.get("playerWestPoints"), table.get("playerNorthPoints"),
      ].some(hasText);
      const useTotalsOnly = Boolean(table.get("useTotalsOnly") ?? true);
      const usePointsCalculation = Boolean(table.get("usePointsCalculation") ?? true);
      const validScores = hasFourValidScores([
        table.get("manualPlayerEastScore") || table.get("playerEastScore"),
        table.get("manualPlayerSouthScore") || table.get("playerSouthScore"),
        table.get("manualPlayerWestScore") || table.get("playerWestScore"),
        table.get("manualPlayerNorthScore") || table.get("playerNorthScore"),
      ]);
      const validPoints = hasValidTablePoints([
        table.get("manualPlayerEastPoints") || table.get("playerEastPoints"),
        table.get("manualPlayerSouthPoints") || table.get("playerSouthPoints"),
        table.get("manualPlayerWestPoints") || table.get("playerWestPoints"),
        table.get("manualPlayerNorthPoints") || table.get("playerNorthPoints"),
      ]);
      if (applyChanges) {
        pending.push(table.ref.update({
          hasProgress: hasTableProgress || hasHandProgress || Boolean(table.get("isCompleted")),
          hasValidManualTotals: useTotalsOnly ? validScores : !usePointsCalculation && validPoints,
          completionStatus: calculateTableCompletionStatus({
            hasData: hasTableProgress || hasHandProgress,
            seatIds: [table.get("playerEastId"), table.get("playerSouthId"), table.get("playerWestId"), table.get("playerNorthId")],
            useTotalsOnly,
            hasValidTotals: useTotalsOnly ? validScores : hasFourValidScores([
              table.get("playerEastScore"), table.get("playerSouthScore"), table.get("playerWestScore"), table.get("playerNorthScore"),
            ]),
          }),
          ...calculateHandSummary(tableHands.map((hand) => hand.data())),
          bestHandScore: FieldValue.delete(),
          manualChickenHandCount: FieldValue.delete(),
          manualBestHandScore: FieldValue.delete(),
          ...(Number.isSafeInteger(table.get("version")) ? {} : { version: 0 }),
        }));
      }
      tableCount++;
      if (pending.length >= 300) await flush();
    }
  }

  const global = await db.collection("_meta").doc("dataVersions").get();
  const globalResources: Record<string, unknown> = {};
  for (const resource of ["tournaments", "emaPlayers", "countries", "users"] as const) {
    if (!Number.isSafeInteger(global.get(`resources.${resource}.revision`))) {
      globalResources[resource] = {
        revision: 1,
        changedAt: FieldValue.serverTimestamp(),
      };
    }
  }
  if (applyChanges && Object.keys(globalResources).length > 0) {
    pending.push(global.ref.set({ resources: globalResources }, { merge: true }));
  }
  await flush();

  console.log(`${applyChanges ? "Updated" : "Would update"} ${tournaments.size} tournaments and ${tableCount} tables.`);
  if (!applyChanges) console.log("Run with --apply to write the changes.");
}

backfill().catch((error: unknown) => {
  console.error("Data version backfill failed.", error);
  process.exitCode = 1;
});
