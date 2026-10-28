import { HttpError, badRequest, conflict, forbidden, notFound } from "../api/httpError";
import { db } from "../firebase";
import type { ChangeActor } from "./changeLog";
import { handSeatScores, isResultSelectionInvalid, seatIdsOf, tableTotalsPatch } from "./handScoring";
import { tournamentMatchPhase } from "./matchTime";
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
  /** The hand ended with no winner. It scores zero for every seat. */
  isDraw: boolean;
  /** The table ends with this hand, for example because the time is over. The table is marked completed. */
  isLastHand: boolean;
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

const MAX_PENALTIES_PER_HAND = 5;

/**
 * The penalties of one seat. Each ";" separated part belongs to one penalty, like the rows of the admin app.
 * A part is empty or a signed whole number.
 */
function penaltyParts(value: unknown): string[] {
  const text = String(value ?? "").trim();
  if (text === "") return [];
  const parts = text.split(";").map((part) => part.trim());
  if (parts.length > MAX_PENALTIES_PER_HAND) throw badRequest("A hand has at most 5 penalties");
  return parts.map((part) => {
    if (part === "") return "";
    if (!/^[-+]?\d+$/.test(part)) throw badRequest("A penalty must be a whole number");
    const number = Number(part);
    return number === 0 ? "" : String(number);
  });
}

/**
 * Checks the four seats together. Every penalty needs a penalized player with a negative value.
 * The other players can receive positive values from a shared penalty. Returns the normalized texts.
 */
function parsePenalties(values: unknown[]): [string, string, string, string] {
  const seats = values.map(penaltyParts);
  const rows = Math.max(0, ...seats.map((parts) => parts.length));
  for (let row = 0; row < rows; row += 1) {
    const cells = seats.map((parts) => parts[row] ?? "");
    if (cells.some((cell) => cell !== "") && !cells.some((cell) => cell.startsWith("-"))) {
      throw badRequest("A penalty needs at least one player with a negative value");
    }
  }
  const texts = seats.map((parts) => {
    const padded = Array.from({ length: rows }, (_, row) => parts[row] ?? "");
    return padded.some((cell) => cell !== "") ? padded.join(";") : "";
  });
  return texts as [string, string, string, string];
}

/** Parses the body of a request. It accepts only the fields that a player may send. */
export function parseHandInput(body: unknown): HandInput {
  const data = (body != null && typeof body === "object") ? body as Record<string, unknown> : {};
  const input: HandInput = {
    playerWinnerId: String(data.playerWinnerId ?? "").trim(),
    playerLooserId: String(data.playerLooserId ?? "").trim(),
    handScore: String(data.handScore ?? "").trim(),
    isChickenHand: data.isChickenHand === true,
    isDraw: data.isDraw === true,
    isLastHand: data.isLastHand === true,
  };
  // A draw has no winner, no loser and the score 0. This is the same form that the editors use.
  if (input.isDraw) return { ...input, playerWinnerId: "", playerLooserId: "", handScore: "0", isChickenHand: false };
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
  const [tournamentSnap, slotSnap, tableSnap, handSnaps] = await Promise.all([
    read.get(db.doc(`tournaments/${key.tournamentId}`)),
    read.get(db.doc(`tournaments/${key.tournamentId}/players/${registration.playerId}`)),
    read.get(tableRef),
    readAll(Array.from({ length: HANDS_PER_TABLE }, (_, i) => tableRef.collection("hands").doc(String(i + 1)))),
  ]);
  // The slot must still hold the EMA id of the account. A slot that holds another person gives this account no access.
  if (!slotSnap.exists || slotSnap.get("assignedEmaId") !== registration.emaId) {
    throw forbidden("You are not registered in this tournament");
  }
  if (!tableSnap.exists) throw notFound("Table not found");
  const table: Data = tableSnap.data() ?? {};
  const seats = seatIdsOf(table);
  if (Boolean(table.isCompleted)) throw conflict("This table is already completed");
  if (Boolean(table.useTotalsOnly ?? false)) throw conflict("This table uses totals only. An editor enters its results");
  if (seats == null) throw conflict("The seats of this table are not assigned yet");
  if (!seats.includes(String(registration.playerId))) throw forbidden("You do not play at this table");
  // Players write only while the match is on. Before the start, after the end, or without a known schedule, an
  // organizer enters the data.
  const phase = tournamentMatchPhase(tournamentSnap.data() ?? {}, key.roundId, Date.now());
  if (phase == null) throw conflict("The schedule of this match is not set. An organizer must enter the data");
  if (phase === "NOT_STARTED") throw conflict("This match has not started yet");
  if (phase === "OVER") throw conflict("The time of this match is over. An organizer must enter the missing data");
  const hands = handsFromData(handSnaps.map((snap) => snap.data() ?? {}));
  return { registration, table, seats, hands, phase };
}

function buildPatch(table: Data, seats: [string, string, string, string], hands: ReturnType<typeof handsFromData>, input: HandInput) {
  const next = hands.find((hand) => !hand.isDone);
  if (!next) throw conflict("All hands of this table are done");
  if (!input.isDraw) {
    if (!seats.includes(input.playerWinnerId)) throw badRequest("The winner does not play at this table");
    if (input.playerLooserId !== "" && !seats.includes(input.playerLooserId)) {
      throw badRequest("The loser does not play at this table");
    }
    if (input.playerLooserId === input.playerWinnerId) throw badRequest("The winner and the loser must differ");
  }
  const { isLastHand, isDraw, ...handFields } = input;
  const handPatch = { ...handFields, isDone: true };
  const handsAfter = hands.map((hand) => (hand.handId === next.handId ? { ...hand, ...handPatch } : hand));
  const handScores = handSeatScores(handsAfter.find((hand) => hand.handId === next.handId) as Data, seats);
  if (handScores == null) throw badRequest("The hand result is not valid");
  // The last hand closes the table, like the Completed switch of an editor.
  const tableAfter = isLastHand ? { ...table, isCompleted: true } : table;
  const totals = tableTotalsPatch(tableAfter, handsAfter);
  return { next, handPatch, totals, handScores, tablePatch: isLastHand ? { ...totals, isCompleted: true } : totals };
}

/** In the extra time after the end of the match, the hand in play is the last hand of the table. */
function inputForPhase(input: HandInput, phase: string | null): HandInput {
  return phase === "EXTRA_TIME" ? { ...input, isLastHand: true } : input;
}

/** True when the table changed after the player looked at it. */
function isVersionConflict(error: unknown): boolean {
  return error instanceof StaleTableVersion
    || (error instanceof HttpError && error.status === 409 && error.message.startsWith("The table changed"));
}

/** Calculates what the table would look like after the hand. It writes nothing. */
export async function previewHand(params: TableKey & { uid: string; input: HandInput }): Promise<HandPreview> {
  const { table, seats, hands, phase } = await loadFor(
    params,
    params.uid,
    { get: (ref) => ref.get() },
    (refs) => db.getAll(...refs),
  );
  const { next, totals, handScores } = buildPatch(table, seats, hands, inputForPhase(params.input, phase));
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
      const { registration, table, seats, hands, phase } = await loadFor(
        params,
        params.uid,
        { get: (ref) => transaction.get(ref) },
        (refs) => transaction.getAll(...refs),
      );
      const version = Number(table.version ?? 0);
      if (version !== params.expectedVersion) {
        throw conflict("The table changed after the review. Check the new totals and confirm again");
      }
      const { next, handPatch, totals, handScores, tablePatch } = buildPatch(table, seats, hands, inputForPhase(params.input, phase));
      const actor: ChangeActor = { uid: params.uid, name: `Player ${registration.emaId}`, source: "player" };
      await saveTableStateInTransaction(transaction, {
        tournamentId: params.tournamentId,
        roundId: params.roundId,
        tableId: params.tableId,
        expectedVersion: version,
        tablePatch,
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

export type PenaltyTexts = [string, string, string, string];

/** Parses the penalties of a request. The four texts are in the order east, south, west, north. */
export function parsePenaltyInput(body: unknown): PenaltyTexts {
  const data = (body != null && typeof body === "object") ? body as Record<string, unknown> : {};
  const texts = parsePenalties([data.playerEastPenalty, data.playerSouthPenalty, data.playerWestPenalty, data.playerNorthPenalty]);
  if (texts.every((text) => text === "")) throw badRequest("A penalty is required");
  return texts;
}

/** Adds the new penalty parts after the parts that the hand already has. Every seat keeps the same number of parts. */
function appendPenalties(current: PenaltyTexts, added: PenaltyTexts): PenaltyTexts {
  const before = current.map((text) => (text === "" ? [] : text.split(";")));
  const after = added.map((text) => (text === "" ? [] : text.split(";")));
  const existingRows = Math.max(0, ...before.map((parts) => parts.length));
  const addedRows = Math.max(0, ...after.map((parts) => parts.length));
  if (existingRows + addedRows > MAX_PENALTIES_PER_HAND) throw conflict("A hand has at most 5 penalties");
  return before.map((parts, seat) => {
    const merged = [
      ...Array.from({ length: existingRows }, (_, row) => parts[row] ?? ""),
      ...Array.from({ length: addedRows }, (_, row) => after[seat][row] ?? ""),
    ];
    return merged.some((cell) => cell !== "") ? merged.join(";") : "";
  }) as PenaltyTexts;
}

/**
 * Adds penalties to the hand in play. The hand is not done, so the hand does not advance.
 * The penalties count in the table totals when the hand is saved.
 */
export async function addPenalties(
  params: TableKey & { uid: string; penalties: PenaltyTexts; expectedVersion: number },
): Promise<{ handId: number; tableVersion: number }> {
  try {
    return await db.runTransaction(async (transaction) => {
      const { registration, table, hands } = await loadFor(
        params,
        params.uid,
        { get: (ref) => transaction.get(ref) },
        (refs) => transaction.getAll(...refs),
      );
      const version = Number(table.version ?? 0);
      if (version !== params.expectedVersion) {
        throw conflict("The table changed. Check the table and add the penalty again");
      }
      const next = hands.find((hand) => !hand.isDone);
      if (!next) throw conflict("All hands of this table are done");
      const current = [next.playerEastPenalty, next.playerSouthPenalty, next.playerWestPenalty, next.playerNorthPenalty]
        .map((value) => String(value ?? "").trim()) as PenaltyTexts;
      const merged = appendPenalties(current, params.penalties);
      await saveTableStateInTransaction(transaction, {
        tournamentId: params.tournamentId,
        roundId: params.roundId,
        tableId: params.tableId,
        expectedVersion: version,
        tablePatch: {},
        handPatches: [{
          handId: next.handId,
          patch: {
            playerEastPenalty: merged[0],
            playerSouthPenalty: merged[1],
            playerWestPenalty: merged[2],
            playerNorthPenalty: merged[3],
          },
        }],
        actor: { uid: params.uid, name: `Player ${registration.emaId}`, source: "player" },
      });
      return { handId: next.handId, tableVersion: version + 1 };
    });
  } catch (error) {
    if (isVersionConflict(error)) {
      await logTableConflict({ ...params, actor: { uid: params.uid, name: null, source: "player" } });
    }
    if (error instanceof StaleTableVersion) throw conflict("The table changed. Check the table and add the penalty again");
    throw error;
  }
}
