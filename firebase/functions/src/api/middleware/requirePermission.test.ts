import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { NextFunction, Request, RequestHandler, Response } from "express";

import { HttpError } from "../httpError";
import { accessOf, requirePermission, requireTournamentPermission } from "./requirePermission";

/** Runs [guard] with the route params and the signed-in uid, and returns what it passed to next. */
async function run(guard: RequestHandler, params: Record<string, string>, uid?: string): Promise<unknown> {
  let passed: unknown = "not called";
  const res = { locals: uid ? { auth: { uid } } : {} } as unknown as Response;
  await guard({ params } as unknown as Request, res, ((error?: unknown) => { passed = error; }) as NextFunction);
  return passed;
}

const statusOf = (error: unknown) => (error instanceof HttpError ? error.status : error);

describe("permission guards without a caller", () => {
  it("name the permission and the scope they check", () => {
    assert.equal(requirePermission("tournaments.create").permission, "tournaments.create");
    assert.equal(requirePermission("tournaments.create").scope, "anywhere");
    assert.equal(requireTournamentPermission("tournament.edit").scope, "tournament");
  });

  it("refuse a request that has no signed-in account", async () => {
    const error = await run(requirePermission("tournaments.create"), {});
    assert.equal(statusOf(error), 403);
    assert.match((error as HttpError).message, /tournaments\.create/);
    const inTournament = await run(requireTournamentPermission("tournament.edit"), { tournamentId: "t1" });
    assert.equal(statusOf(inTournament), 403);
    assert.equal((inTournament as HttpError).message, "Auth context missing");
  });

  it("answer 404 to a tournament route without a tournament", async () => {
    const error = await run(requireTournamentPermission("tournament.edit"), {}, "u1");
    assert.equal(statusOf(error), 404);
  });

  it("accessOf refuses a route whose guard did not run", () => {
    assert.throws(() => accessOf({ locals: {} } as unknown as Response), (error: unknown) => statusOf(error) === 403);
    const access = { uid: "u1" };
    assert.equal(accessOf({ locals: { access } } as unknown as Response), access);
  });
});
