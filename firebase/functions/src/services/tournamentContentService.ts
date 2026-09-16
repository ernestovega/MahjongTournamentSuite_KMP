import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../firebase";

export type TournamentPlayer = {
  id: number;
  name: string;
  team: number;
  country: string;
  assignedEmaId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function timestampToIso(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

export type TournamentRound = {
  roundId: number;
};

export type TournamentTable = {
  roundId: number;
  tableId: number;
  playerIds: number[];
  isCompleted: boolean;
  useTotalsOnly: boolean;
  usePointsCalculation: boolean;
  hasProgress: boolean;
  hasValidManualTotals: boolean;
};

function hasValidTotals(
  values: unknown[],
  expectedTotal: number,
  allowDecimal: boolean = false,
): boolean {
  const pattern = allowDecimal ? /^-?\d+(?:[,.]\d+)?$/ : /^-?\d+$/;
  if (values.length !== 4 || !values.every((value) => pattern.test(String(value ?? "").trim()))) {
    return false;
  }

  const total = values.reduce(
    (sum: number, value: unknown) => sum + Number(String(value).replace(",", ".")),
    0,
  );
  return Math.abs(total - expectedTotal) < 0.011;
}

export async function listTournamentPlayers(tournamentId: string): Promise<TournamentPlayer[]> {
  const snap = await db.collection("tournaments").doc(tournamentId).collection("players").get();
  return snap.docs
    .map((d) => ({
      id: Number(d.get("id")),
      name: String(d.get("name") ?? ""),
      team: Number(d.get("team") ?? 0),
      country: String(d.get("country") ?? ""),
      assignedEmaId: typeof d.get("assignedEmaId") === "string" ? d.get("assignedEmaId") : null,
      createdAt: timestampToIso(d.get("createdAt")),
      updatedAt: timestampToIso(d.get("updatedAt")),
    }))
    .sort((a, b) => a.id - b.id);
}

export async function assignTournamentPlayer(params: {
  tournamentId: string;
  playerId: number;
  emaId: string | null;
}): Promise<void> {
  const playerRef = db.collection("tournaments").doc(params.tournamentId)
    .collection("players").doc(String(params.playerId));
  const player = await playerRef.get();
  if (!player.exists) {
    throw new Error("Player not found");
  }

  await playerRef.update({
    assignedEmaId: params.emaId,
    updatedAt: FieldValue.serverTimestamp(),
  });
}

export async function listTournamentRounds(tournamentId: string): Promise<TournamentRound[]> {
  const snap = await db.collection("tournaments").doc(tournamentId).collection("rounds").get();
  return snap.docs
    .map((d) => ({
      roundId: Number(d.get("roundId")),
    }))
    .sort((a, b) => a.roundId - b.roundId);
}

export async function listTournamentTables(
  tournamentId: string,
  roundId: number | null,
): Promise<TournamentTable[]> {
  const collection = db.collection("tournaments").doc(tournamentId).collection("tables");
  const snap = roundId == null
    ? await collection.get()
    : await collection.where("roundId", "==", roundId).get();

  const tables = await Promise.all(snap.docs.map(async (d) => {
    const hands = await d.ref.collection("hands").get();
    const hasHandProgress = hands.docs.some((hand) => {
      const value = hand.data();
      return [
        value.playerWinnerId,
        value.playerLooserId,
        value.handScore,
        value.playerEastPenalty,
        value.playerSouthPenalty,
        value.playerWestPenalty,
        value.playerNorthPenalty,
      ].some((field) => String(field ?? "").trim().length > 0)
        || Boolean(value.isChickenHand)
        || Boolean(value.isDone);
    });
    const hasTableProgress = [
      d.get("playerEastId"),
      d.get("playerSouthId"),
      d.get("playerWestId"),
      d.get("playerNorthId"),
      d.get("playerEastScore"),
      d.get("playerSouthScore"),
      d.get("playerWestScore"),
      d.get("playerNorthScore"),
      d.get("playerEastPoints"),
      d.get("playerSouthPoints"),
      d.get("playerWestPoints"),
      d.get("playerNorthPoints"),
    ].some((field) => String(field ?? "").trim().length > 0);
    const hasValidManualScores = hasValidTotals([
      d.get("manualPlayerEastScore") || d.get("playerEastScore"),
      d.get("manualPlayerSouthScore") || d.get("playerSouthScore"),
      d.get("manualPlayerWestScore") || d.get("playerWestScore"),
      d.get("manualPlayerNorthScore") || d.get("playerNorthScore"),
    ], 0);
    const hasValidManualPoints = hasValidTotals([
      d.get("manualPlayerEastPoints") || d.get("playerEastPoints"),
      d.get("manualPlayerSouthPoints") || d.get("playerSouthPoints"),
      d.get("manualPlayerWestPoints") || d.get("playerWestPoints"),
      d.get("manualPlayerNorthPoints") || d.get("playerNorthPoints"),
    ], 7, true);
    const useTotalsOnly = Boolean(d.get("useTotalsOnly") ?? true);
    const usePointsCalculation = Boolean(d.get("usePointsCalculation") ?? true);

    return {
      roundId: Number(d.get("roundId")),
      tableId: Number(d.get("tableId")),
      playerIds: (d.get("playerIds") as unknown[] | undefined ?? []).map((x) => Number(x)),
      isCompleted: Boolean(d.get("isCompleted") ?? false),
      useTotalsOnly,
      usePointsCalculation,
      hasProgress: hasTableProgress || hasHandProgress || Boolean(d.get("isCompleted") ?? false),
      hasValidManualTotals: useTotalsOnly ? hasValidManualScores : !usePointsCalculation && hasValidManualPoints,
    };
  }));

  return tables
    .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
