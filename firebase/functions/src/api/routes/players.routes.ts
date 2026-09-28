import { Router } from "express";

import { badRequest } from "../httpError";
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
      const firstName = String(req.body?.firstName ?? "").trim();
      const lastName = String(req.body?.lastName ?? "").trim();
      const country = String(req.body?.country ?? "").trim().toUpperCase();
      if (!firstName || !lastName) throw badRequest("First name and last name are required");
      if (!/^[A-Z]{3}$/.test(country)) throw badRequest("Player country must use a three-letter EMA code");
      res.status(200).json(await createPlayer({ emaId, firstName, lastName, country }));
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
