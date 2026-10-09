import type { NextFunction, Request, Response } from "express";

import { auth } from "../../firebase";
import { unauthenticated } from "../httpError";

// The revocation check calls Firebase Auth, so a successful check is reused for this long per user.
const REVOCATION_CHECK_TTL_MS = 30_000;
const MAX_CACHED_USERS = 1000;
const revocationCheckedUntil = new Map<string, number>();

function extractBearerToken(headerValue: string | undefined): string | null {
  if (!headerValue) return null;
  const match = headerValue.match(/^Bearer\s+(.+)$/i);
  return match?.[1] ?? null;
}

export async function requireAuth(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  const token = extractBearerToken(req.header("authorization"));
  if (!token) {
    next(unauthenticated("Missing Authorization bearer token"));
    return;
  }

  try {
    // Check revocation so disabled users lose API access. The check is cached for a short time per user.
    let decoded = await auth.verifyIdToken(token, false);
    const now = Date.now();
    if ((revocationCheckedUntil.get(decoded.uid) ?? 0) <= now) {
      decoded = await auth.verifyIdToken(token, true);
      if (revocationCheckedUntil.size >= MAX_CACHED_USERS) revocationCheckedUntil.clear();
      revocationCheckedUntil.set(decoded.uid, now + REVOCATION_CHECK_TTL_MS);
    }
    res.locals.auth = decoded;
    next();
  } catch {
    next(unauthenticated("Invalid or expired token"));
  }
}
