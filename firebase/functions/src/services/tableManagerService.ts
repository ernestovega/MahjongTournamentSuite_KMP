import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { notFound } from "../api/httpError";

export type TableHand = {
  handId: number;
  playerWinnerId: string;
  playerLooserId: string;
  handScore: string;
  isChickenHand: boolean;
  playerEastPenalty: string;
  playerSouthPenalty: string;
  playerWestPenalty: string;
  playerNorthPenalty: string;
};

export type TableState = {
  roundId: number;
  tableId: number;
  playerIds: number[];
  playerEastId: string;
  playerSouthId: string;
  playerWestId: string;
  playerNorthId: string;
  playerEastScore: string;
  playerSouthScore: string;
  playerWestScore: string;
  playerNorthScore: string;
  playerEastPoints: string;
  playerSouthPoints: string;
  playerWestPoints: string;
  playerNorthPoints: string;
  manualPlayerEastScore: string;
  manualPlayerSouthScore: string;
  manualPlayerWestScore: string;
  manualPlayerNorthScore: string;
  manualPlayerEastPoints: string;
  manualPlayerSouthPoints: string;
  manualPlayerWestPoints: string;
  manualPlayerNorthPoints: string;
  isCompleted: boolean;
  useTotalsOnly: boolean;
  usePointsCalculation: boolean;
};

export async function getTableWithHands(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
}): Promise<{ table: TableState; hands: TableHand[] }> {
  const tableDocId = `${params.roundId}_${params.tableId}`;
  const tableRef = db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
  const tableSnap = await tableRef.get();
  if (!tableSnap.exists) throw notFound("Table not found");

  const handsCollection = tableRef.collection("hands");
  let handsSnap = await handsCollection.get();
  if (handsSnap.empty) {
    // Hands are created lazily to keep tournament creation write volume manageable.
    const batch = db.batch();
    for (let handId = 1; handId <= 16; handId++) {
      const handRef = handsCollection.doc(String(handId));
      batch.set(handRef, {
        handId,
        playerWinnerId: "",
        playerLooserId: "",
        handScore: "",
        isChickenHand: false,
        playerEastPenalty: "",
        playerSouthPenalty: "",
        playerWestPenalty: "",
        playerNorthPenalty: "",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    await batch.commit();
    handsSnap = await handsCollection.get();
  }

  const table: TableState = {
    roundId: Number(tableSnap.get("roundId")),
    tableId: Number(tableSnap.get("tableId")),
    playerIds: (tableSnap.get("playerIds") as unknown[] | undefined ?? []).map((x) => Number(x)),
    playerEastId: String(tableSnap.get("playerEastId") ?? ""),
    playerSouthId: String(tableSnap.get("playerSouthId") ?? ""),
    playerWestId: String(tableSnap.get("playerWestId") ?? ""),
    playerNorthId: String(tableSnap.get("playerNorthId") ?? ""),
    playerEastScore: String(tableSnap.get("playerEastScore") ?? ""),
    playerSouthScore: String(tableSnap.get("playerSouthScore") ?? ""),
    playerWestScore: String(tableSnap.get("playerWestScore") ?? ""),
    playerNorthScore: String(tableSnap.get("playerNorthScore") ?? ""),
    playerEastPoints: String(tableSnap.get("playerEastPoints") ?? ""),
    playerSouthPoints: String(tableSnap.get("playerSouthPoints") ?? ""),
    playerWestPoints: String(tableSnap.get("playerWestPoints") ?? ""),
    playerNorthPoints: String(tableSnap.get("playerNorthPoints") ?? ""),
    manualPlayerEastScore: String(tableSnap.get("manualPlayerEastScore") ?? ""),
    manualPlayerSouthScore: String(tableSnap.get("manualPlayerSouthScore") ?? ""),
    manualPlayerWestScore: String(tableSnap.get("manualPlayerWestScore") ?? ""),
    manualPlayerNorthScore: String(tableSnap.get("manualPlayerNorthScore") ?? ""),
    manualPlayerEastPoints: String(tableSnap.get("manualPlayerEastPoints") ?? ""),
    manualPlayerSouthPoints: String(tableSnap.get("manualPlayerSouthPoints") ?? ""),
    manualPlayerWestPoints: String(tableSnap.get("manualPlayerWestPoints") ?? ""),
    manualPlayerNorthPoints: String(tableSnap.get("manualPlayerNorthPoints") ?? ""),
    isCompleted: Boolean(tableSnap.get("isCompleted") ?? false),
    useTotalsOnly: Boolean(tableSnap.get("useTotalsOnly") ?? false),
    usePointsCalculation: Boolean(tableSnap.get("usePointsCalculation") ?? true),
  };

  const hands: TableHand[] = handsSnap.docs
    .map((d) => ({
      handId: Number(d.get("handId")),
      playerWinnerId: String(d.get("playerWinnerId") ?? ""),
      playerLooserId: String(d.get("playerLooserId") ?? ""),
      handScore: String(d.get("handScore") ?? ""),
      isChickenHand: Boolean(d.get("isChickenHand") ?? false),
      playerEastPenalty: String(d.get("playerEastPenalty") ?? ""),
      playerSouthPenalty: String(d.get("playerSouthPenalty") ?? ""),
      playerWestPenalty: String(d.get("playerWestPenalty") ?? ""),
      playerNorthPenalty: String(d.get("playerNorthPenalty") ?? ""),
    }))
    .sort((a, b) => a.handId - b.handId);

  return { table, hands };
}

export async function updateTable(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
  patch: Partial<Pick<TableState,
    | "playerEastId"
    | "playerSouthId"
    | "playerWestId"
    | "playerNorthId"
    | "playerEastScore"
    | "playerSouthScore"
    | "playerWestScore"
    | "playerNorthScore"
    | "playerEastPoints"
    | "playerSouthPoints"
    | "playerWestPoints"
    | "playerNorthPoints"
    | "manualPlayerEastScore"
    | "manualPlayerSouthScore"
    | "manualPlayerWestScore"
    | "manualPlayerNorthScore"
    | "manualPlayerEastPoints"
    | "manualPlayerSouthPoints"
    | "manualPlayerWestPoints"
    | "manualPlayerNorthPoints"
    | "isCompleted"
    | "useTotalsOnly"
    | "usePointsCalculation"
  >>;
}): Promise<void> {
  const tableDocId = `${params.roundId}_${params.tableId}`;
  const tableRef = db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
  const snap = await tableRef.get();
  if (!snap.exists) throw notFound("Table not found");

  await tableRef.update({
    ...params.patch,
    updatedAt: FieldValue.serverTimestamp(),
  });

  if (Object.prototype.hasOwnProperty.call(params.patch, "isCompleted")) {
    const tournamentRef = db.collection("tournaments").doc(params.tournamentId);
    const newIsCompleted = Boolean(params.patch.isCompleted);

    if (!newIsCompleted) {
      await tournamentRef.update({
        isCompleted: false,
        updatedAt: FieldValue.serverTimestamp(),
      });
      return;
    }

    const incomplete = await tournamentRef
      .collection("tables")
      .where("isCompleted", "==", false)
      .limit(1)
      .get();

    await tournamentRef.update({
      isCompleted: incomplete.empty,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
}

export async function updateHand(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
  handId: number;
  patch: Partial<Omit<TableHand, "handId">>;
}): Promise<void> {
  const tableDocId = `${params.roundId}_${params.tableId}`;
  const tableRef = db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
  const tableSnap = await tableRef.get();
  if (!tableSnap.exists) throw notFound("Table not found");

  const handRef = tableRef.collection("hands").doc(String(params.handId));
  const handSnap = await handRef.get();
  if (!handSnap.exists) throw notFound("Hand not found");

  await handRef.update({
    ...params.patch,
    updatedAt: FieldValue.serverTimestamp(),
  });
}
