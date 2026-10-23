import { Router, type Request, type Response } from "express";

import { auth } from "../../firebase";
import { assertSafeDocumentId, badRequest } from "../httpError";
import { rateLimit } from "../middleware/rateLimit";
import { requireAuth } from "../middleware/requireAuth";
import { requireRegisteredPlayer } from "../middleware/requireRegisteredPlayer";
import { acceptInvitation } from "../../services/playerAccountsService";
import { addPenalties, parseHandInput, parsePenaltyInput, previewHand, recordHand } from "../../services/playerHandService";

/** Routes for player accounts. The invitation route is public because a new player has no account yet. */
export function playerRouter(): Router {
  const router = Router();

  router.post("/invitations/accept", rateLimit({ windowMs: 60_000, max: 20 }), async (req, res, next) => {
    try {
      const token = String(req.body?.token ?? "").trim();
      if (!token) throw badRequest("Missing token");
      const password = typeof req.body?.password === "string" ? req.body.password : undefined;
      const bearer = req.header("authorization")?.match(/^Bearer\s+(.+)$/i)?.[1];
      // An existing account signs in first. A new account sends a password instead.
      const actorUid = bearer ? (await auth.verifyIdToken(bearer, true).catch(() => null))?.uid : undefined;
      const result = await acceptInvitation({ token, password, actorUid });
      res.status(200).json(result);
    } catch (e) {
      next(e);
    }
  });

  router.param("tournamentId", (_req, _res, next, value: string) => {
    try {
      assertSafeDocumentId(value, "tournament id");
      next();
    } catch (error) {
      next(error);
    }
  });

  const tableRoute = "/tournaments/:tournamentId/tables/:roundId/:tableId";
  const tableParams = (req: Request) => {
    const roundId = Number(req.params.roundId);
    const tableId = Number(req.params.tableId);
    if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
      throw badRequest("roundId and tableId must be positive integers");
    }
    return { tournamentId: req.params.tournamentId, roundId, tableId };
  };
  const uidOf = (res: Response) => (res.locals.auth as { uid: string }).uid;

  // The app shows this result to the table first. Nothing is written.
  router.post(`${tableRoute}/hands/preview`, requireAuth, requireRegisteredPlayer, async (req, res, next) => {
    try {
      const input = parseHandInput(req.body);
      res.status(200).json(await previewHand({ ...tableParams(req), uid: uidOf(res), input }));
    } catch (e) {
      next(e);
    }
  });

  // Penalties belong to the hand in play. They do not finish the hand.
  router.post(`${tableRoute}/penalties`, requireAuth, requireRegisteredPlayer, async (req, res, next) => {
    try {
      const penalties = parsePenaltyInput(req.body);
      const expectedVersion = req.body?.expectedVersion;
      if (!Number.isInteger(expectedVersion) || expectedVersion < 0) {
        throw badRequest("expectedVersion must be the table version that the app shows");
      }
      res.status(200).json(await addPenalties({ ...tableParams(req), uid: uidOf(res), penalties, expectedVersion }));
    } catch (e) {
      next(e);
    }
  });

  router.post(`${tableRoute}/hands`, requireAuth, requireRegisteredPlayer, async (req, res, next) => {
    try {
      const input = parseHandInput(req.body);
      const expectedVersion = req.body?.expectedVersion;
      if (!Number.isInteger(expectedVersion) || expectedVersion < 0) {
        throw badRequest("expectedVersion must be the table version of the preview");
      }
      res.status(200).json(await recordHand({ ...tableParams(req), uid: uidOf(res), input, expectedVersion }));
    } catch (e) {
      next(e);
    }
  });

  return router;
}
