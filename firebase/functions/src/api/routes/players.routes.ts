import { Router } from "express";

import { badRequest, forbidden } from "../httpError";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireAuth } from "../middleware/requireAuth";
import { createPlayer, listPlayers, updatePlayer, updatePlayerPhoto, validateEmaId } from "../../services/playersService";

export function playersRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      res.status(200).json({ players: await listPlayers() });
    } catch (error) {
      next(error);
    }
  });

  router.post("/", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.body?.emaId);
      const name = String(req.body?.name ?? "").trim();
      const country = String(req.body?.country ?? "").trim();
      if (!name) throw badRequest("Player name is required");
      if (!country) throw badRequest("Player country is required");
      res.status(200).json(await createPlayer({ emaId, name, country }));
    } catch (error) {
      next(error);
    }
  });

  router.put("/:emaId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.params.emaId);
      const newEmaId = validateEmaId(req.body?.emaId ?? emaId);
      if (newEmaId !== emaId) {
        const decoded = res.locals.auth as { superadmin?: boolean } | undefined;
        if (decoded?.superadmin !== true) throw forbidden("Superadmin required to change an EMA number");
      }
      const name = String(req.body?.name ?? "").trim();
      const country = String(req.body?.country ?? "").trim();
      if (!name) throw badRequest("Player name is required");
      if (!country) throw badRequest("Player country is required");
      await updatePlayer({ previousEmaId: emaId, emaId: newEmaId, name, country });
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
      res.status(200).json(await updatePlayerPhoto({ emaId, contentType, dataBase64 }));
    } catch (error) {
      next(error);
    }
  });

  return router;
}
