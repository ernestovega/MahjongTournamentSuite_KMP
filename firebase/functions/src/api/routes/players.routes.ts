import { Router } from "express";

import { badRequest } from "../httpError";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireAuth } from "../middleware/requireAuth";
import { createPlayer, listPlayers, updatePlayer, validateEmaId } from "../../services/playersService";

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
      res.status(200).json(await createPlayer({ emaId, name, country }));
    } catch (error) {
      next(error);
    }
  });

  router.put("/:emaId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.params.emaId);
      const name = String(req.body?.name ?? "").trim();
      const country = String(req.body?.country ?? "").trim();
      if (!name) throw badRequest("Player name is required");
      await updatePlayer({ emaId, name, country });
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
