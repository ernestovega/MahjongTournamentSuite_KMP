import type { NextFunction, Request, Response } from "express";

import { HttpError } from "../httpError";

export function errorHandler(
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
): void {
  if (err instanceof HttpError) {
    res.status(err.status).json({
      error: err.code,
      message: err.message,
      details: err.details ?? null,
    });
    return;
  }

  // The body parser reports a bad or too large body with a status of its own. It is the fault of the caller.
  const status = (err as { status?: unknown } | null)?.status;
  if (typeof status === "number" && status >= 400 && status < 500 && (err as { type?: unknown }).type !== undefined) {
    res.status(status).json({
      error: status === 413 ? "payload_too_large" : "bad_request",
      message: status === 413 ? "The request is too large" : "The request body is not valid",
    });
    return;
  }

  console.error(err);

  res.status(500).json({
    error: "internal",
    message: "Internal server error",
  });
}
