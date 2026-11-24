import { NextFunction, Request, Response, Router } from "express";
import { parseAcceptLanguage } from "../../services/i18n";
import { recordActivity } from "../../services/activityLog";
import { requireAuth } from "../middleware/requireAuth";
import { accessOf, requireTournamentPermission } from "../middleware/requirePermission";
import { runAccountAction, savePendingEmails } from "../../services/playerAccountsService";
import { badRequest } from "../httpError";
import { canInTournament } from "../../services/accessService";
import { validateEmaId } from "../../services/playersService";
import { actorFromToken } from "../../services/changeLog";
import { pathParam } from "../pathParam";
import { applyPlayerImport, exportPlayers, previewPlayerImport } from "../../services/playerImportService";
import {
  adminAddRegistration,
  adminRegistrations,
  adminRemoveRegistration,
  adminUpdateRegistration,
  importRegistrations,
  updateRegistrationSettings,
} from "../../services/registrationsService";
import { makeDrawFromRegistrations } from "../../services/registrationDraw";
import { changePlayerCount } from "../../services/playerCountChange";
import { parseClosesOn, parseMaxPlayers, parsePhase, parseSlug } from "../../services/registrationSettings";

/** Player accounts, player import, registrations, the draw and the change of player count. Registered by tournamentsRouter, in its order. */
export function registerRegistrationRoutes(router: Router): void {
  // One action for one player or for many. The server invites the players with no account and resets the others.
  router.post(
    "/:tournamentId/player-accounts/send",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const raw = Array.isArray(req.body?.players) ? req.body.players as unknown[] : [];
        const items = raw.map((entry) => {
          const playerId = Number((entry as { playerId?: unknown }).playerId);
          if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
          return { playerId };
        });
        const results = await runAccountAction({
          tournamentId: pathParam(req, "tournamentId"), items, language: parseAcceptLanguage(req.headers["accept-language"]),
        });
        await recordActivity({
          action: "account.send", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `${items.length} players`, details: { playerIds: items.map((item) => item.playerId) },
        });
        res.status(200).json({ results });
      } catch (e) {
        next(e);
      }
    },
  );

  // Sets the pending email of slots, so "Invite" can create their accounts. An empty email clears it.
  router.put(
    "/:tournamentId/player-emails",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const raw = Array.isArray(req.body?.players) ? req.body.players as unknown[] : [];
        const items = raw.map((entry) => {
          const value = entry as { playerId?: unknown; email?: unknown };
          const playerId = Number(value.playerId);
          if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
          return { playerId, email: String(value.email ?? "") };
        });
        const results = await savePendingEmails({ tournamentId: pathParam(req, "tournamentId"), items });
        await recordActivity({
          action: "player.email", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `${items.length} players`, details: { playerIds: items.map((item) => item.playerId) },
        });
        res.status(200).json({ results });
      } catch (e) {
        next(e);
      }
    },
  );

  /** Reads the file of the request. The apply step reads it again, so the server never trusts rows from the client. */
  const readImportFile = (body: unknown): { fileName: string; buffer: Buffer } => {
    const value = (body != null && typeof body === "object") ? body as Record<string, unknown> : {};
    const dataBase64 = String(value.dataBase64 ?? "").trim();
    if (!dataBase64) throw badRequest("The file is required");
    return { fileName: String(value.fileName ?? "players.csv"), buffer: Buffer.from(dataBase64, "base64") };
  };

  router.get(
    "/:tournamentId/player-import/export",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const file = await exportPlayers(pathParam(req, "tournamentId"), {
          includeEmails: await canInTournament(accessOf(res), "tournament.playerEmails", pathParam(req, "tournamentId")),
        });
        res.status(200).json({ fileName: file.fileName, dataBase64: file.buffer.toString("base64") });
      } catch (e) {
        next(e);
      }
    },
  );

  router.post(
    "/:tournamentId/player-import/preview",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const file = readImportFile(req.body);
        res.status(200).json(await previewPlayerImport({ tournamentId: pathParam(req, "tournamentId"), ...file }));
      } catch (e) {
        next(e);
      }
    },
  );

  router.post(
    "/:tournamentId/player-import/apply",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const file = readImportFile(req.body);
        const uid = (res.locals.auth as { uid: string }).uid;
        const result = await applyPlayerImport({ tournamentId: pathParam(req, "tournamentId"), ...file, uid });
        await recordActivity({
          action: "player.import", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `${result.players} players`, details: { ...result },
        });
        res.status(200).json(result);
      } catch (e) {
        next(e);
      }
    },
  );

  // Registrations, before the draw. See docs/registrations.md.
  const registrationRoute = (handler: (req: Request, res: Response) => Promise<void>) =>
    async (req: Request, res: Response, next: NextFunction) => {
      try {
        await handler(req, res);
      } catch (e) {
        next(e);
      }
    };
  const registrationActivity = async (res: Response, tournamentId: string, target: string, details: Record<string, unknown>) => {
    await recordActivity({ action: "registration.update", actor: actorFromToken(res.locals.auth), tournamentId, target, details });
  };

  router.get(
    "/:tournamentId/registrations",
    requireAuth,
    requireTournamentPermission("tournament.playerEmails"),
    registrationRoute(async (req, res) => {
      res.status(200).json(await adminRegistrations(pathParam(req, "tournamentId")));
    }),
  );

  router.post(
    "/:tournamentId/registrations",
    requireAuth,
    requireTournamentPermission("tournament.playerEmails"),
    registrationRoute(async (req, res) => {
      const body = (req.body ?? {}) as Record<string, unknown>;
      const tournamentId = pathParam(req, "tournamentId");
      const result = await adminAddRegistration({
        tournamentId,
        emaId: body.emaId,
        email: body.email,
        status: body.status,
        paid: body.paid,
        foodRestrictions: body.foodRestrictions,
        teamName: body.teamName,
        notify: body.notify !== false,
        language: parseAcceptLanguage(req.header("accept-language")),
      });
      await registrationActivity(res, tournamentId, `registration ${result.emaId}`, { added: true, status: result.status });
      res.status(200).json(result);
    }),
  );

  router.post(
    "/:tournamentId/registrations/import",
    requireAuth,
    requireTournamentPermission("tournament.playerEmails"),
    registrationRoute(async (req, res) => {
      const body = (req.body ?? {}) as Record<string, unknown>;
      const tournamentId = pathParam(req, "tournamentId");
      const result = await importRegistrations({
        tournamentId, csv: body.csv, fileName: body.fileName, dataBase64: body.dataBase64, apply: body.apply === true, language: parseAcceptLanguage(req.header("accept-language")),
      });
      if (body.apply === true) await registrationActivity(res, tournamentId, `${result.imported} registrations`, { imported: result.imported, replaced: result.replaced });
      res.status(200).json(result);
    }),
  );

  router.put(
    "/:tournamentId/registrations/:emaId",
    requireAuth,
    requireTournamentPermission("tournament.playerEmails"),
    registrationRoute(async (req, res) => {
      const body = (req.body ?? {}) as Record<string, unknown>;
      const tournamentId = pathParam(req, "tournamentId");
      const result = await adminUpdateRegistration({
        tournamentId,
        emaId: pathParam(req, "emaId"),
        email: body.email,
        status: body.status,
        paid: body.paid,
        teamName: body.teamName,
        foodRestrictions: body.foodRestrictions,
        newEmaId: body.newEmaId,
      });
      await registrationActivity(res, tournamentId, `registration ${result.emaId}`, {
        status: body.status, paid: body.paid, teamName: body.teamName, emailChanged: body.email !== undefined,
        ...(body.newEmaId != null ? { from: pathParam(req, "emaId") } : {}),
      });
      res.status(200).json(result);
    }),
  );

  router.delete(
    "/:tournamentId/registrations/:emaId",
    requireAuth,
    requireTournamentPermission("tournament.playerEmails"),
    registrationRoute(async (req, res) => {
      const tournamentId = pathParam(req, "tournamentId");
      const emaId = pathParam(req, "emaId");
      await adminRemoveRegistration({ tournamentId, emaId, notify: req.query.notify !== "false" });
      await registrationActivity(res, tournamentId, `registration ${emaId}`, { removed: true });
      res.status(200).json({ ok: true });
    }),
  );

  router.put(
    "/:tournamentId/registration-settings",
    requireAuth,
    requireTournamentPermission("tournament.configure"),
    registrationRoute(async (req, res) => {
      const body = (req.body ?? {}) as Record<string, unknown>;
      const tournamentId = pathParam(req, "tournamentId");
      const settings = await updateRegistrationSettings({
        tournamentId,
        status: parsePhase(body.status),
        maxPlayers: parseMaxPlayers(body.maxPlayers),
        closesOn: parseClosesOn(body.closesOn),
        slug: parseSlug(body.slug),
      });
      await registrationActivity(res, tournamentId, "registration settings", { ...settings });
      res.status(200).json(settings);
    }),
  );

  router.post(
    "/:tournamentId/draw",
    requireAuth,
    requireTournamentPermission("tournament.configure"),
    registrationRoute(async (req, res) => {
      const body = (req.body ?? {}) as Record<string, unknown>;
      const tournamentId = pathParam(req, "tournamentId");
      const list = (value: unknown, name: string) => {
        if (!Array.isArray(value)) throw badRequest(`${name} must be an array`);
        return value.map((item) => (item != null && typeof item === "object" ? item as Record<string, unknown> : {}));
      };
      const integer = (value: unknown, name: string) => {
        const number = Number(value);
        if (!Number.isInteger(number)) throw badRequest(`Invalid ${name}`);
        return number;
      };
      const tournament = await makeDrawFromRegistrations({
        tournamentId,
        numTries: integer(body.numTries ?? 1, "numTries"),
        players: list(body.players, "players").map((row) => ({ id: integer(row.id, "player id"), team: integer(row.team ?? 0, "player team") })),
        tables: list(body.tables, "tables").map((row) => {
          const ids = Array.isArray(row.playerIds) ? row.playerIds.map((id) => integer(id, "table playerIds")) : [];
          if (ids.length !== 4) throw badRequest("Invalid table playerIds");
          return { roundId: integer(row.roundId, "table roundId"), tableId: integer(row.tableId, "table tableId"), playerIds: ids };
        }),
        assignments: list(body.assignments, "assignments").map((row) => ({
          playerId: integer(row.playerId, "assignment playerId"), emaId: validateEmaId(row.emaId),
        })),
        uid: (res.locals.auth as { uid?: string } | undefined)?.uid ?? null,
      });
      await recordActivity({
        action: "registration.draw", actor: actorFromToken(res.locals.auth), tournamentId, target: tournament.name,
        details: { numPlayers: tournament.numPlayers, numRounds: tournament.numRounds },
      });
      res.status(200).json(tournament);
    }),
  );

  // Change of the number of players before the first hand: whole tables leave or come, and the draw is new.
  // See docs/player-count-change.md.
  router.post(
    "/:tournamentId/player-count",
    requireAuth,
    requireTournamentPermission("tournament.configure"),
    registrationRoute(async (req, res) => {
      const body = (req.body ?? {}) as Record<string, unknown>;
      const tournamentId = pathParam(req, "tournamentId");
      const list = (value: unknown, name: string) => {
        if (!Array.isArray(value)) throw badRequest(`${name} must be an array`);
        return value;
      };
      const row = (item: unknown) => (item != null && typeof item === "object" ? item as Record<string, unknown> : {});
      const integer = (value: unknown, name: string) => {
        const number = Number(value);
        if (value === null || value === "" || !Number.isInteger(number)) throw badRequest(`Invalid ${name}`);
        return number;
      };
      const result = await changePlayerCount({
        tournamentId,
        numTries: integer(body.numTries ?? 1, "numTries"),
        players: list(body.players, "players").map(row).map((item) => ({ id: integer(item.id, "player id"), team: integer(item.team ?? 0, "player team") })),
        tables: list(body.tables, "tables").map(row).map((item) => {
          const ids = Array.isArray(item.playerIds) ? item.playerIds.map((id) => integer(id, "table playerIds")) : [];
          if (ids.length !== 4) throw badRequest("Invalid table playerIds");
          return { roundId: integer(item.roundId, "table roundId"), tableId: integer(item.tableId, "table tableId"), playerIds: ids };
        }),
        removedSlotIds: list(body.removedSlotIds ?? [], "removedSlotIds").map((id) => integer(id, "removed slot")),
        waitingEmaIds: list(body.waitingEmaIds ?? [], "waitingEmaIds").map((emaId) => validateEmaId(emaId)),
        uid: (res.locals.auth as { uid?: string } | undefined)?.uid ?? null,
      });
      await recordActivity({
        action: "tournament.playerCount", actor: actorFromToken(res.locals.auth), tournamentId, target: result.tournament.name,
        details: {
          from: result.previousPlayers,
          to: result.tournament.numPlayers,
          removed: result.removed.map((person) => person.emaId ?? `#${person.slotId}`),
          promoted: result.promoted,
          notified: result.emails.sent,
          notNotified: [...result.emails.noEmail, ...result.emails.failed],
        },
      });
      res.status(200).json({ tournament: result.tournament, emails: result.emails });
    }),
  );
}
