import { Router } from "express";
import { recordActivity } from "../../services/activityLog";
import { actorFromToken } from "../../services/changeLog";

import { badRequest, forbidden } from "../httpError";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireAuth } from "../middleware/requireAuth";
import { listPlayerTournamentIds } from "../../services/playerAccountsService";
import { createPlayer, listPlayers, listPlayersOfTournaments, updatePlayer, updatePlayerPhoto, validateEmaId } from "../../services/playersService";

export function playersRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean; player?: boolean };
      if (decoded.admin === true || decoded.editor === true) {
        res.status(200).json({ players: await listPlayers() });
        return;
      }
      if (decoded.player !== true) throw forbidden("Editor or player required");
      const tournamentIds = await listPlayerTournamentIds(decoded.uid);
      res.status(200).json({ players: await listPlayersOfTournaments(tournamentIds) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.body?.emaId);
      const firstName = String(req.body?.firstName ?? "").trim();
      const lastName = String(req.body?.lastName ?? "").trim();
      const country = String(req.body?.country ?? "").trim().toUpperCase();
      if (!firstName || !lastName) throw badRequest("First name and last name are required");
      if (!/^[A-Z]{3}$/.test(country)) throw badRequest("Player country must use a three-letter EMA code");
      const created = await createPlayer({ emaId, firstName, lastName, country });
      await recordActivity({
        action: "registry.create", actor: actorFromToken(res.locals.auth), target: `EMA ${emaId}`,
        details: { firstName, lastName, country },
      });
      res.status(200).json(created);
    } catch (error) {
      next(error);
    }
  });

  router.put("/:emaId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.params.emaId);
      const newEmaId = validateEmaId(req.body?.emaId ?? emaId);
      const firstName = String(req.body?.firstName ?? "").trim();
      const lastName = String(req.body?.lastName ?? "").trim();
      const country = String(req.body?.country ?? "").trim().toUpperCase();
      if (!firstName || !lastName) throw badRequest("First name and last name are required");
      if (!/^[A-Z]{3}$/.test(country)) throw badRequest("Player country must use a three-letter EMA code");
      await updatePlayer({ previousEmaId: emaId, emaId: newEmaId, firstName, lastName, country });
      await recordActivity({
        action: "registry.update", actor: actorFromToken(res.locals.auth), target: `EMA ${emaId}`,
        details: { emaId: newEmaId, firstName, lastName, country },
      });
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  router.put("/:emaId/photo", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.params.emaId);
      const contentType = String(req.body?.contentType ?? "").trim().toLowerCase();
      const dataBase64 = String(req.body?.dataBase64 ?? "").trim();
      if (!dataBase64) throw badRequest("Photo data is required");
      const updated = await updatePlayerPhoto({ emaId, contentType, dataBase64 });
      await recordActivity({
        action: "registry.photo", actor: actorFromToken(res.locals.auth), target: `EMA ${emaId}`, details: { contentType },
      });
      res.status(200).json(updated);
    } catch (error) {
      next(error);
    }
  });

  return router;
}
