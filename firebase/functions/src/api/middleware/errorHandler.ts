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

  console.error(err);

  res.status(500).json({
    error: "internal",
    message: "Internal server error",
  });
}
