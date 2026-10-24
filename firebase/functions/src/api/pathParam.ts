import type { Request } from "express";

/** Reads a path parameter of a route. A path parameter without a wildcard is always one text. */
export function pathParam(req: Request, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? value.join("/") : String(value ?? "");
}
