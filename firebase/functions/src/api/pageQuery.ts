import { badRequest } from "./httpError";

/** The most entries that one page may hold. */
export const MAX_PAGE_SIZE = 200;

/** Reads the page size. A missing or invalid value gives [fallback]. The result stays between 1 and [MAX_PAGE_SIZE]. */
export function limitQuery(value: unknown, fallback: number): number {
  const number = Number(value ?? fallback);
  return Math.min(Math.max(Number.isFinite(number) && number !== 0 ? Math.trunc(number) : fallback, 1), MAX_PAGE_SIZE);
}

/** Reads a number that must not be negative. */
export function numberQuery(value: unknown, name: string): number {
  const number = Number(value);
  if (value === "" || !Number.isFinite(number) || number < 0) throw badRequest(`${name} must be a positive number`);
  return number;
}

/** Reads the cursor of a page from `cursorAt` and `cursorId`. Both are needed. A request with none starts at the first page. */
export function cursorQuery(query: Record<string, unknown>): { at: number; id: string } | undefined {
  if (query.cursorAt === undefined && query.cursorId === undefined) return undefined;
  if (query.cursorAt === undefined || typeof query.cursorId !== "string" || !query.cursorId) {
    throw badRequest("cursorAt and cursorId go together");
  }
  return { at: numberQuery(query.cursorAt, "cursorAt"), id: query.cursorId };
}
