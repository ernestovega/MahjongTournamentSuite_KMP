import { Router } from "express";
import { recordActivity } from "../../services/activityLog";
import { deleteEmaPlayers, tournamentsOfEmaPlayers } from "../../services/emaPlayerDeletes";
import { actorFromToken } from "../../services/changeLog";

import { badRequest, forbidden } from "../httpError";
import { requirePermission } from "../middleware/requirePermission";
import { isStaff, loadAccess } from "../../services/accessService";
import { requireAuth } from "../middleware/requireAuth";
import { listPlayerTournamentIds } from "../../services/playerAccountsService";
import { createPlayer, listPlayers, listPlayersOfTournaments, updatePlayer, updatePlayerPhoto, validateEmaId } from "../../services/playersService";

export function playersRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; player?: boolean };
      if (isStaff(await loadAccess(decoded.uid))) {
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

  router.post("/", requireAuth, requirePermission("ema.create"), async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.body?.emaId);
      const firstName = String(req.body?.firstName ?? "").trim();
      const lastName = String(req.body?.lastName ?? "").trim();
      const country = String(req.body?.country ?? "").trim().toUpperCase();
      if (!firstName || !lastName) throw badRequest("First name and last name are required");
      // An empty country is a player without country (a guest).
      if (!/^([A-Z]{3})?$/.test(country)) throw badRequest("Player country must use a three-letter EMA code or be empty");
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

  // Before a delete: the tournaments where each chosen player has data. Those players are deactivated, not deleted.
  router.post("/delete/preview", requireAuth, requirePermission("ema.delete"), async (req, res, next) => {
    try {
      const raw = Array.isArray(req.body?.emaIds) ? req.body.emaIds as unknown[] : [];
      const emaIds = raw.map((value) => validateEmaId(value));
      res.status(200).json({ players: await tournamentsOfEmaPlayers(emaIds) });
    } catch (error) {
      next(error);
    }
  });

  // Deletes many EMA players at once, from the Player base. Their accounts stay, without the EMA number.
  router.post("/delete", requireAuth, requirePermission("ema.delete"), async (req, res, next) => {
    try {
      const raw = Array.isArray(req.body?.emaIds) ? req.body.emaIds as unknown[] : [];
      const emaIds = raw.map((value) => validateEmaId(value));
      const results = await deleteEmaPlayers(emaIds);
      const deleted = results.filter((result) => result.outcome === "DELETED").map((result) => result.emaId);
      const disabled = results.filter((result) => result.outcome === "DISABLED").map((result) => result.emaId);
      if (deleted.length + disabled.length > 0) {
        await recordActivity({
          action: "registry.delete", actor: actorFromToken(res.locals.auth), target: `${deleted.length + disabled.length} EMA players`,
          details: { deleted, disabled },
        });
      }
      res.status(200).json({ results });
    } catch (error) {
      next(error);
    }
  });

  router.put("/:emaId", requireAuth, requirePermission("ema.edit"), async (req, res, next) => {
    try {
      const emaId = validateEmaId(req.params.emaId);
      const newEmaId = validateEmaId(req.body?.emaId ?? emaId);
      const firstName = String(req.body?.firstName ?? "").trim();
      const lastName = String(req.body?.lastName ?? "").trim();
      const country = String(req.body?.country ?? "").trim().toUpperCase();
      if (!firstName || !lastName) throw badRequest("First name and last name are required");
      // An empty country is a player without country (a guest).
      if (!/^([A-Z]{3})?$/.test(country)) throw badRequest("Player country must use a three-letter EMA code or be empty");
      const hiddenFromPublic = typeof req.body?.hiddenFromPublic === "boolean" ? req.body.hiddenFromPublic : undefined;
      const disabled = typeof req.body?.disabled === "boolean" ? req.body.disabled : undefined;
      await updatePlayer({ previousEmaId: emaId, emaId: newEmaId, firstName, lastName, country, hiddenFromPublic, disabled });
      await recordActivity({
        action: "registry.update", actor: actorFromToken(res.locals.auth), target: `EMA ${emaId}`,
        details: { emaId: newEmaId, firstName, lastName, country, hiddenFromPublic, disabled },
      });
      res.status(200).json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  router.put("/:emaId/photo", requireAuth, requirePermission("ema.edit"), async (req, res, next) => {
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
