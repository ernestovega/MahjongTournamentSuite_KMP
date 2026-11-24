import type { NextFunction, Request, Response } from "express";

import { clientAddress } from "../clientAddress";
import { HttpError } from "../httpError";

const MAX_TRACKED_CLIENTS = 5000;

/**
 * Limits the requests of one client address in a time window. The counters live in the memory of one function
 * instance, so this is a brake against bulk use and not an exact limit.
 */
export function rateLimit(options: {
  windowMs: number;
  max: number;
  /** Who the counter belongs to. The default is the client address. */
  key?: (req: Request) => string;
  now?: () => number;
}) {
  const now = options.now ?? Date.now;
  const windows = new Map<string, { resetAt: number; count: number }>();

  return (req: Request, _res: Response, next: NextFunction): void => {
    const time = now();
    const key = options.key?.(req) ?? clientAddress(req);
    let entry = windows.get(key);
    if (!entry || entry.resetAt <= time) {
      // A full map loses only the windows that are over, never the counts of the clients still being counted: else a
      // flood of new keys would reset everybody. While no window is over, a new key is not counted (fail open).
      if (windows.size >= MAX_TRACKED_CLIENTS) {
        for (const [other, window] of windows) if (window.resetAt <= time) windows.delete(other);
        if (windows.size >= MAX_TRACKED_CLIENTS) {
          next();
          return;
        }
      }
      entry = { resetAt: time + options.windowMs, count: 0 };
      windows.set(key, entry);
    }
    entry.count += 1;
    if (entry.count > options.max) {
      next(new HttpError(429, "too_many_requests", "Too many requests. Try again later."));
      return;
    }
    next();
  };
}
