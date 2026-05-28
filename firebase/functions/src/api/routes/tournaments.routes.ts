import { Router } from "express";

import { requireAuth } from "../middleware/requireAuth";
import { requireSuperadmin } from "../middleware/requireSuperadmin";
import { requireTournamentRole } from "../middleware/requireTournamentRole";
import { badRequest } from "../httpError";
import { parseRole } from "../../models/role";
import { listTournamentMembers, removeTournamentMember, upsertTournamentMember } from "../../services/membersService";
import { createTournament, deleteTournament, listAllTournaments, listTournamentsForUser } from "../../services/tournamentsService";
import { listTournamentPlayers, listTournamentRounds, listTournamentTables } from "../../services/tournamentContentService";
import { getTableWithHands, updateHand, updateTable } from "../../services/tableManagerService";

export function tournamentsRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; superadmin?: boolean };
      const tournaments = decoded.superadmin === true
        ? await listAllTournaments()
        : await listTournamentsForUser(decoded.uid);
      res.status(200).json({ tournaments });
    } catch (e) {
      next(e);
    }
  });

  router.post("/", requireAuth, requireSuperadmin, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };

      const body = (req.body != null && typeof req.body === "object") ? (req.body as Record<string, unknown>) : null;

      const name = String(body?.name ?? "").trim();
      const isTeams = Boolean(body?.isTeams ?? false);

      const numPlayersValue = body?.numPlayers;
      const numRoundsValue = body?.numRounds;
      const numTriesValue = body?.numTries;

      const numPlayers = Number(numPlayersValue ?? NaN);
      const numRounds = Number(numRoundsValue ?? NaN);
      const numTries = Number(numTriesValue ?? NaN);
      const players = Array.isArray(body?.players) ? (body?.players as unknown[]) : null;
      const tables = Array.isArray(body?.tables) ? (body?.tables as unknown[]) : null;

      const issues: Array<{ field: string; message: string; value?: unknown }> = [];
      if (!body) issues.push({ field: "body", message: "Request body must be JSON", value: req.body });
      if (!name) issues.push({ field: "name", message: "Required", value: body?.name });

      if (!Number.isFinite(numPlayers) || !Number.isInteger(numPlayers) || numPlayers <= 0 || numPlayers % 4 !== 0) {
        issues.push({ field: "numPlayers", message: "Must be a positive integer multiple of 4", value: numPlayersValue });
      }
      if (!Number.isFinite(numRounds) || !Number.isInteger(numRounds) || numRounds <= 0) {
        issues.push({ field: "numRounds", message: "Must be a positive integer", value: numRoundsValue });
      }
      if (!Number.isFinite(numTries) || !Number.isInteger(numTries) || numTries <= 0) {
        issues.push({ field: "numTries", message: "Must be a positive integer", value: numTriesValue });
      }
      if (!players) issues.push({ field: "players", message: "Required (array)", value: body?.players });
      if (!tables) issues.push({ field: "tables", message: "Required (array)", value: body?.tables });

      if (issues.length > 0) {
        throw badRequest("Missing or invalid tournament fields", {
          issues,
          received: body,
          inferred: { name, isTeams, numPlayers, numRounds, numTries, playersCount: players?.length, tablesCount: tables?.length },
        });
      }

      const parsedPlayers = (players ?? []).map((p, idx) => {
        const row = (p != null && typeof p === "object") ? (p as Record<string, unknown>) : null;
        const id = Number(row?.id ?? NaN);
        const team = Number(row?.team ?? NaN);
        const playerName = row?.name == null ? undefined : String(row?.name);
        if (!Number.isFinite(id) || !Number.isInteger(id)) throw badRequest("Invalid player id", { idx, value: row?.id });
        if (!Number.isFinite(team) || !Number.isInteger(team)) throw badRequest("Invalid player team", { idx, value: row?.team });
        return { id, team, name: playerName };
      });

      const parsedTables = (tables ?? []).map((t, idx) => {
        const row = (t != null && typeof t === "object") ? (t as Record<string, unknown>) : null;
        const roundId = Number(row?.roundId ?? NaN);
        const tableId = Number(row?.tableId ?? NaN);
        const playerIdsRaw = Array.isArray(row?.playerIds) ? (row?.playerIds as unknown[]) : null;
        const isCompletedRaw = row?.isCompleted;
        const useTotalsOnlyRaw = row?.useTotalsOnly;
        if (!Number.isFinite(roundId) || !Number.isInteger(roundId)) throw badRequest("Invalid table roundId", { idx, value: row?.roundId });
        if (!Number.isFinite(tableId) || !Number.isInteger(tableId)) throw badRequest("Invalid table tableId", { idx, value: row?.tableId });
        if (!playerIdsRaw || playerIdsRaw.length !== 4) throw badRequest("Invalid table playerIds", { idx, value: row?.playerIds });
        if (isCompletedRaw != null && typeof isCompletedRaw !== "boolean") throw badRequest("Invalid table isCompleted", { idx, value: isCompletedRaw });
        if (useTotalsOnlyRaw != null && typeof useTotalsOnlyRaw !== "boolean") throw badRequest("Invalid table useTotalsOnly", { idx, value: useTotalsOnlyRaw });
        const playerIds = playerIdsRaw.map((x) => Number(x ?? NaN));
        for (const pid of playerIds) {
          if (!Number.isFinite(pid) || !Number.isInteger(pid)) {
            throw badRequest("Invalid table playerIds entry", { idx, value: row?.playerIds });
          }
        }
        return {
          roundId,
          tableId,
          playerIds,
          isCompleted: isCompletedRaw as boolean | undefined,
          useTotalsOnly: useTotalsOnlyRaw as boolean | undefined,
        };
      });

      const tournament = await createTournament({
        name,
        isTeams,
        numPlayers,
        numRounds,
        numTries,
        players: parsedPlayers,
        tables: parsedTables,
        createdByUid: decoded.uid,
      });

      res.status(200).json(tournament);
    } catch (e) {
      next(e);
    }
  });

  router.delete("/:tournamentId", requireAuth, requireSuperadmin, async (req, res, next) => {
    try {
      await deleteTournament(req.params.tournamentId);
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.get(
    "/:tournamentId/members",
    requireAuth,
    requireTournamentRole("ADMIN"),
    async (req, res, next) => {
      try {
        const members = await listTournamentMembers(req.params.tournamentId);
        res.status(200).json({ members });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/members/:uid",
    requireAuth,
    requireTournamentRole("ADMIN"),
    async (req, res, next) => {
      try {
        const role = parseRole(req.body?.role);
        if (!role) {
          throw badRequest("Invalid role");
        }

        await upsertTournamentMember({
          tournamentId: req.params.tournamentId,
          uid: req.params.uid,
          role,
        });

        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.delete(
    "/:tournamentId/members/:uid",
    requireAuth,
    requireTournamentRole("ADMIN"),
    async (req, res, next) => {
      try {
        await removeTournamentMember({
          tournamentId: req.params.tournamentId,
          uid: req.params.uid,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/players",
    requireAuth,
    requireTournamentRole("READER"),
    async (req, res, next) => {
      try {
        const players = await listTournamentPlayers(req.params.tournamentId);
        res.status(200).json({ players });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/rounds",
    requireAuth,
    requireTournamentRole("READER"),
    async (req, res, next) => {
      try {
        const rounds = await listTournamentRounds(req.params.tournamentId);
        res.status(200).json({ rounds });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables",
    requireAuth,
    requireTournamentRole("READER"),
    async (req, res, next) => {
      try {
        const roundIdParam = req.query.roundId;
        const roundId = roundIdParam == null ? null : Number(roundIdParam);
        if (roundId != null && (!Number.isFinite(roundId) || !Number.isInteger(roundId) || roundId <= 0)) {
          throw badRequest("roundId must be a positive integer");
        }

        const tables = await listTournamentTables(req.params.tournamentId, roundId);
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables/:roundId/:tableId",
    requireAuth,
    requireTournamentRole("READER"),
    async (req, res, next) => {
      try {
        const roundId = Number(req.params.roundId);
        const tableId = Number(req.params.tableId);
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        const data = await getTableWithHands({
          tournamentId: req.params.tournamentId,
          roundId,
          tableId,
        });
        res.status(200).json(data);
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/tables/:roundId/:tableId",
    requireAuth,
    requireTournamentRole("EDITOR"),
    async (req, res, next) => {
      try {
        const roundId = Number(req.params.roundId);
        const tableId = Number(req.params.tableId);
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        const patch = req.body ?? {};
        await updateTable({
          tournamentId: req.params.tournamentId,
          roundId,
          tableId,
          patch,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/tables/:roundId/:tableId/hands/:handId",
    requireAuth,
    requireTournamentRole("EDITOR"),
    async (req, res, next) => {
      try {
        const roundId = Number(req.params.roundId);
        const tableId = Number(req.params.tableId);
        const handId = Number(req.params.handId);
        if (
          !Number.isInteger(roundId) || roundId <= 0
          || !Number.isInteger(tableId) || tableId <= 0
          || !Number.isInteger(handId) || handId <= 0
        ) {
          throw badRequest("roundId, tableId and handId must be positive integers");
        }

        const patch = req.body ?? {};
        await updateHand({
          tournamentId: req.params.tournamentId,
          roundId,
          tableId,
          handId,
          patch,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  // Placeholder: future endpoints for players/teams/tables/hands will live here.
  router.get(
    "/:tournamentId",
    requireAuth,
    requireTournamentRole("READER"),
    async (req, res) => {
      res.status(501).json({
        error: "not_implemented",
        message: "Tournament details endpoint not implemented yet",
        tournamentId: req.params.tournamentId,
      });
    },
  );

  return router;
}
