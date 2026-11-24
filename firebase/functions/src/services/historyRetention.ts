/**
 * The activity log and the change log of the tournaments keep each entry for two years. Firestore deletes it after
 * that, by the field "expireAt" (see the TTL field overrides in firebase/firestore.indexes.json).
 */
export const HISTORY_DAYS = 730;
const DAY_MS = 24 * 60 * 60 * 1000;

export function historyExpireAt(now: number = Date.now()): Date {
  return new Date(now + HISTORY_DAYS * DAY_MS);
}
