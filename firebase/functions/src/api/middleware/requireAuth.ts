import type { NextFunction, Request, Response } from "express";

import { auth } from "../../firebase";
import { unauthenticated } from "../httpError";

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
    const decoded = await auth.verifyIdToken(token);
    res.locals.auth = decoded;
    next();
  } catch {
    next(unauthenticated("Invalid or expired token"));
  }
}
