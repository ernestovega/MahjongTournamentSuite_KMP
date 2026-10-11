import { badRequest } from "./httpError";
import { HANDS_PER_TABLE } from "../services/tableManagerService";

const TABLE_STRING_FIELDS = [
  "playerEastId", "playerSouthId", "playerWestId", "playerNorthId",
  "playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
  "playerEastPoints", "playerSouthPoints", "playerWestPoints", "playerNorthPoints",
  "manualPlayerEastScore", "manualPlayerSouthScore", "manualPlayerWestScore", "manualPlayerNorthScore",
  "manualPlayerEastPoints", "manualPlayerSouthPoints", "manualPlayerWestPoints", "manualPlayerNorthPoints",
] as const;
const TABLE_BOOLEAN_FIELDS = ["isCompleted", "useTotalsOnly", "usePointsCalculation"] as const;
const HAND_STRING_FIELDS = [
  "playerWinnerId", "playerLooserId", "handScore", "playerEastPenalty",
  "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty",
] as const;
const HAND_BOOLEAN_FIELDS = ["isChickenHand", "isDone"] as const;

function parsePatch(
  value: unknown,
  stringFields: readonly string[],
  booleanFields: readonly string[],
): Record<string, string | boolean> {
  const source = value != null && typeof value === "object" ? value as Record<string, unknown> : {};
  const result: Record<string, string | boolean> = {};
  for (const field of stringFields) {
    if (!(field in source)) continue;
    if (typeof source[field] !== "string") throw badRequest(`${field} must be a string`);
    result[field] = source[field] as string;
  }
  for (const field of booleanFields) {
    if (!(field in source)) continue;
    if (typeof source[field] !== "boolean") throw badRequest(`${field} must be a boolean`);
    result[field] = source[field] as boolean;
  }
  return result;
}

export function parseTablePatch(value: unknown) {
  return parsePatch(value, TABLE_STRING_FIELDS, TABLE_BOOLEAN_FIELDS);
}

export function parseHandPatch(value: unknown) {
  return parsePatch(value, HAND_STRING_FIELDS, HAND_BOOLEAN_FIELDS);
}


export type SaveTableRequest = {
  roundId: number;
  tableId: number;
  expectedVersion: number;
  tablePatch: Record<string, string | boolean>;
  handPatches: Array<{ handId: number; patch: Record<string, string | boolean> }>;
};

/** Checks the URL and the body of a table save. Every problem becomes a 400 answer. */
export function parseSaveTableRequest(
  params: { roundId?: unknown; tableId?: unknown },
  body: unknown,
): SaveTableRequest {
  const roundId = Number(params.roundId);
  const tableId = Number(params.tableId);
  if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
    throw badRequest("roundId and tableId must be positive integers");
  }
  const source = body != null && typeof body === "object" ? body as Record<string, unknown> : {};
  // Number(null), Number("") and Number(false) are 0. Only a real number is a version.
  const expectedVersion = source.expectedVersion;
  if (typeof expectedVersion !== "number" || !Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw badRequest("expectedVersion must be a non-negative integer");
  }
  const tablePatch = parseTablePatch(source.tablePatch);
  const handPatchesRaw = Array.isArray(source.handPatches) ? source.handPatches : [];
  const handPatches = handPatchesRaw.map((value: unknown) => {
    const item = value != null && typeof value === "object" ? value as Record<string, unknown> : {};
    const handId = item.handId;
    if (typeof handId !== "number" || !Number.isInteger(handId) || handId <= 0 || handId > HANDS_PER_TABLE) {
      throw badRequest(`handId must be an integer from 1 to ${HANDS_PER_TABLE}`);
    }
    return { handId, patch: parseHandPatch(item.patch) };
  });
  if (new Set(handPatches.map((item) => item.handId)).size !== handPatches.length) {
    throw badRequest("Each hand can occur only once");
  }
  return { roundId, tableId, expectedVersion, tablePatch, handPatches };
}
