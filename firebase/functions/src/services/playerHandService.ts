import { HttpError, badRequest, conflict, forbidden, notFound } from "../api/httpError";
import { db } from "../firebase";
import type { ChangeActor } from "./changeLog";
import { handSeatScores, isResultSelectionInvalid, seatIdsOf, tableTotalsPatch } from "./handScoring";
import { getPlayerRegistration } from "./playerAccountsService";
import {
  HANDS_PER_TABLE,
  handsFromData,
  saveTableStateInTransaction,
  StaleTableVersion,
  logTableConflict,
} from "./tableManagerService";

/**
 * Hand entry for players.
 *
 * One player of the table writes the hands down. The app first asks for a preview that shows the hand and the
 * table totals, shows it to everyone at the table, and then records the hand with the version of the preview.
 * A table that changed in between is refused, so the player reviews the new totals.
 */

type Data = Record<string, unknown>;

export type HandInput = {
  playerWinnerId: string;
  playerLooserId: string;
  handScore: string;
  isChickenHand: boolean;
};

export type HandPreview = {
  handId: number;
  /** Slot ids by seat in the order east, south, west, north. */
  seats: [string, string, string, string];
  /** Score change of every seat from this hand. */
  handScores: [number, number, number, number];
  /** Table scores and points after this hand. */
  tableScores: [string, string, string, string];
  tablePoints: [string, string, string, string];
  /** The table version that the preview is based on. Send it when recording the hand. */
  tableVersion: number;
};

/** Parses the body of a request. It accepts only the fields that a player may send. */
export function parseHandInput(body: unknown): HandInput {
  const data = (body != null && typeof body === "object") ? body as Record<string, unknown> : {};
  const input: HandInput = {
    playerWinnerId: String(data.playerWinnerId ?? "").trim(),
    playerLooserId: String(data.playerLooserId ?? "").trim(),
    handScore: String(data.handScore ?? "").trim(),
    isChickenHand: data.isChickenHand === true,
  };
  if (input.playerLooserId === "-") input.playerLooserId = "";
  if (!input.playerWinnerId) throw badRequest("A winner is required");
  if (isResultSelectionInvalid({ ...input })) throw badRequest("The hand result is not valid");
  if (input.isChickenHand && input.playerLooserId === "") throw badRequest("A chicken hand needs a loser");
  return input;
}

type TableKey = { tournamentId: string; roundId: number; tableId: number };

async function loadFor(
  key: TableKey,
  uid: string,
  read: { get(ref: FirebaseFirestore.DocumentReference): Promise<FirebaseFirestore.DocumentSnapshot> },
  readAll: (refs: FirebaseFirestore.DocumentReference[]) => Promise<FirebaseFirestore.DocumentSnapshot[]>,
) {
  const registration = await getPlayerRegistration(uid, key.tournamentId);
  if (!registration) throw forbidden("You are not registered in this tournament");
  const tableRef = db.doc(`tournaments/${key.tournamentId}/tables/${key.roundId}_${key.tableId}`);
  const [tableSnap, handSnaps] = await Promise.all([
    read.get(tableRef),
    readAll(Array.from({ length: HANDS_PER_TABLE }, (_, i) => tableRef.collection("hands").doc(String(i + 1)))),
  ]);
  if (!tableSnap.exists) throw notFound("Table not found");
  const table: Data = tableSnap.data() ?? {};
  const seats = seatIdsOf(table);
  if (Boolean(table.isCompleted)) throw conflict("This table is already completed");
  if (Boolean(table.useTotalsOnly ?? false)) throw conflict("This table uses totals only. An editor enters its results");
  if (seats == null) throw conflict("The seats of this table are not assigned yet");
  if (!seats.includes(String(registration.playerId))) throw forbidden("You do not play at this table");
  const hands = handsFromData(handSnaps.map((snap) => snap.data() ?? {}));
  return { registration, table, seats, hands };
}

function buildPatch(table: Data, seats: [string, string, string, string], hands: ReturnType<typeof handsFromData>, input: HandInput) {
  const next = hands.find((hand) => !hand.isDone);
  if (!next) throw conflict("All hands of this table are done");
  if (!seats.includes(input.playerWinnerId)) throw badRequest("The winner does not play at this table");
  if (input.playerLooserId !== "" && !seats.includes(input.playerLooserId)) {
    throw badRequest("The loser does not play at this table");
  }
  if (input.playerLooserId === input.playerWinnerId) throw badRequest("The winner and the loser must differ");
  const handPatch = { ...input, isDone: true };
  const handsAfter = hands.map((hand) => (hand.handId === next.handId ? { ...hand, ...handPatch } : hand));
  const handScores = handSeatScores(handsAfter.find((hand) => hand.handId === next.handId) as Data, seats);
  if (handScores == null) throw badRequest("The hand result is not valid");
  return { next, handPatch, totals: tableTotalsPatch(table, handsAfter), handScores };
}

/** True when the table changed after the player looked at it. */
function isVersionConflict(error: unknown): boolean {
  return error instanceof StaleTableVersion
    || (error instanceof HttpError && error.status === 409 && error.message.startsWith("The table changed"));
}

/** Calculates what the table would look like after the hand. It writes nothing. */
export async function previewHand(params: TableKey & { uid: string; input: HandInput }): Promise<HandPreview> {
  const { table, seats, hands } = await loadFor(
    params,
    params.uid,
    { get: (ref) => ref.get() },
    (refs) => db.getAll(...refs),
  );
  const { next, totals, handScores } = buildPatch(table, seats, hands, params.input);
  return {
    handId: next.handId,
    seats,
    handScores,
    tableScores: [totals.playerEastScore, totals.playerSouthScore, totals.playerWestScore, totals.playerNorthScore],
    tablePoints: [totals.playerEastPoints, totals.playerSouthPoints, totals.playerWestPoints, totals.playerNorthPoints],
    tableVersion: Number(table.version ?? 0),
  };
}

/** Writes the hand and the new table totals. It refuses when the table changed after the preview. */
export async function recordHand(
  params: TableKey & { uid: string; input: HandInput; expectedVersion: number },
): Promise<HandPreview> {
  try {
    return await db.runTransaction(async (transaction) => {
      const { registration, table, seats, hands } = await loadFor(
        params,
        params.uid,
        { get: (ref) => transaction.get(ref) },
        (refs) => transaction.getAll(...refs),
      );
      const version = Number(table.version ?? 0);
      if (version !== params.expectedVersion) {
        throw conflict("The table changed after the review. Check the new totals and confirm again");
      }
      const { next, handPatch, totals, handScores } = buildPatch(table, seats, hands, params.input);
      const actor: ChangeActor = { uid: params.uid, name: `Player ${registration.emaId}`, source: "player" };
      await saveTableStateInTransaction(transaction, {
        tournamentId: params.tournamentId,
        roundId: params.roundId,
        tableId: params.tableId,
        expectedVersion: version,
        tablePatch: totals,
        handPatches: [{ handId: next.handId, patch: handPatch }],
        actor,
      });
      return {
        handId: next.handId,
        seats,
        handScores,
        tableScores: [totals.playerEastScore, totals.playerSouthScore, totals.playerWestScore, totals.playerNorthScore],
        tablePoints: [totals.playerEastPoints, totals.playerSouthPoints, totals.playerWestPoints, totals.playerNorthPoints],
        tableVersion: version + 1,
      } satisfies HandPreview;
    });
  } catch (error) {
    if (isVersionConflict(error)) {
      await logTableConflict({ ...params, actor: { uid: params.uid, name: null, source: "player" } });
    }
    if (error instanceof StaleTableVersion) throw conflict("The table changed. Review the hand again");
    throw error;
  }
}

export type SeatAssignment = { east: string; south: string; west: string; north: string };

/** Parses the body of a seat assignment. */
export function parseSeatAssignment(body: unknown): SeatAssignment {
  const data = (body != null && typeof body === "object") ? body as Record<string, unknown> : {};
  const text = (value: unknown) => String(value ?? "").trim();
  const seats = {
    east: text(data.playerEastId),
    south: text(data.playerSouthId),
    west: text(data.playerWestId),
    north: text(data.playerNorthId),
  };
  if (Object.values(seats).some((id) => id === "")) throw badRequest("Every seat needs a player");
  if (new Set(Object.values(seats)).size !== 4) throw badRequest("A player can take only one seat");
  return seats;
}

/**
 * A player of the table assigns the four seats. It is allowed until the first hand is saved.
 */
export async function assignSeats(
  params: TableKey & { uid: string; seats: SeatAssignment; expectedVersion: number },
): Promise<{ seats: [string, string, string, string]; tableVersion: number }> {
  const registration = await getPlayerRegistration(params.uid, params.tournamentId);
  if (!registration) throw forbidden("You are not registered in this tournament");
  const tableRef = db.doc(`tournaments/${params.tournamentId}/tables/${params.roundId}_${params.tableId}`);

  try {
    return await db.runTransaction(async (transaction) => {
      const [tableSnap, handSnaps] = await Promise.all([
        transaction.get(tableRef),
        transaction.getAll(...Array.from({ length: HANDS_PER_TABLE }, (_, i) => tableRef.collection("hands").doc(String(i + 1)))),
      ]);
      if (!tableSnap.exists) throw notFound("Table not found");
      const table: Data = tableSnap.data() ?? {};
      const playerIds = ((table.playerIds as unknown[] | undefined) ?? []).map(String);
      if (!playerIds.includes(String(registration.playerId))) throw forbidden("You do not play at this table");
      if (Boolean(table.isCompleted)) throw conflict("This table is already completed");
      if (handSnaps.some((snap) => Boolean(snap.get("isDone")))) {
        throw conflict("A hand is already saved. A staff member changes the seats now");
      }
      // A staff member may have entered totals already. The seats then stay with the staff.
      const hasManualTotals = ["playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
        "manualPlayerEastScore", "manualPlayerSouthScore", "manualPlayerWestScore", "manualPlayerNorthScore"]
        .some((field) => String(table[field] ?? "").trim() !== "");
      if (hasManualTotals) throw conflict("A staff member already entered totals for this table");

      const chosen = [params.seats.east, params.seats.south, params.seats.west, params.seats.north];
      if (chosen.some((id) => !playerIds.includes(id))) throw badRequest("A seat has a player who is not at this table");
      const version = Number(table.version ?? 0);
      if (version !== params.expectedVersion) {
        throw conflict("The table changed. Check the seats again");
      }
      await saveTableStateInTransaction(transaction, {
        tournamentId: params.tournamentId,
        roundId: params.roundId,
        tableId: params.tableId,
        expectedVersion: version,
        tablePatch: {
          playerEastId: params.seats.east,
          playerSouthId: params.seats.south,
          playerWestId: params.seats.west,
          playerNorthId: params.seats.north,
        },
        handPatches: [],
        actor: { uid: params.uid, name: `Player ${registration.emaId}`, source: "player" },
      });
      return { seats: chosen as [string, string, string, string], tableVersion: version + 1 };
    });
  } catch (error) {
    if (isVersionConflict(error)) {
      await logTableConflict({ ...params, actor: { uid: params.uid, name: null, source: "player" } });
    }
    if (error instanceof StaleTableVersion) throw conflict("The table changed. Check the seats again");
    throw error;
  }
}
