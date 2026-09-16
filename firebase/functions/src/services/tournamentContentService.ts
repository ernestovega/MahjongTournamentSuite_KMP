import { db } from "../firebase";

export type TournamentPlayer = {
  id: number;
  name: string;
  team: number;
};

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
};

export async function listTournamentPlayers(tournamentId: string): Promise<TournamentPlayer[]> {
  const snap = await db.collection("tournaments").doc(tournamentId).collection("players").get();
  return snap.docs
    .map((d) => ({
      id: Number(d.get("id")),
      name: String(d.get("name") ?? ""),
      team: Number(d.get("team") ?? 0),
    }))
    .sort((a, b) => a.id - b.id);
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

    return {
      roundId: Number(d.get("roundId")),
      tableId: Number(d.get("tableId")),
      playerIds: (d.get("playerIds") as unknown[] | undefined ?? []).map((x) => Number(x)),
      isCompleted: Boolean(d.get("isCompleted") ?? false),
      useTotalsOnly: Boolean(d.get("useTotalsOnly") ?? true),
      usePointsCalculation: Boolean(d.get("usePointsCalculation") ?? true),
      hasProgress: hasTableProgress || hasHandProgress || Boolean(d.get("isCompleted") ?? false),
    };
  }));

  return tables
    .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
