import { Router } from "express";

import { auth } from "../../firebase";
import { requireAuth } from "../middleware/requireAuth";
import { requireAdmin } from "../middleware/requireAdmin";
import { requireTournamentEditor } from "../middleware/requireTournamentEditor";
import { badRequest } from "../httpError";
import { listTournamentMembers, removeTournamentMember, upsertTournamentMember } from "../../services/membersService";
import {
  createTournament,
  deleteTournament,
  listAllTournaments,
  listTournamentsForUser,
  renameTournament,
  updateTournamentSettings,
} from "../../services/tournamentsService";
import { generateTournamentIdCards } from "../../services/idCardsService";
import { generateEmaReport } from "../../services/emaReportService";
import {
  assignTournamentPlayer,
  listTournamentPlayers,
  listTournamentRounds,
  listTournamentTables,
  listTournamentTeams,
  updateTournamentTeam,
} from "../../services/tournamentContentService";
import { playerExists, validateEmaId } from "../../services/playersService";
import { getTableWithHands, resetTable, updateHand, updateTable } from "../../services/tableManagerService";
import { getUserProfile } from "../../services/usersService";
import { isValidIsoDate, isValidIsoDateRange } from "../../services/tournamentDates";

export function tournamentsRouter(): Router {
  const router = Router();

  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; admin?: boolean };
      const tournaments = decoded.admin === true
        ? await listAllTournaments()
        : await listTournamentsForUser(decoded.uid);
      res.status(200).json({ tournaments });
    } catch (e) {
      next(e);
    }
  });

  router.post("/", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };

      const body = (req.body != null && typeof req.body === "object") ? (req.body as Record<string, unknown>) : null;

      const name = String(body?.name ?? "").trim();
      const shortName = String(body?.shortName ?? name.slice(0, 10)).trim();
      const primaryColor = String(body?.primaryColor ?? "#02B16B").trim().toUpperCase();
      const associationLogoContentType = body?.associationLogoContentType == null
        ? null
        : String(body.associationLogoContentType).trim();
      const associationLogoDataBase64 = body?.associationLogoDataBase64 == null
        ? null
        : String(body.associationLogoDataBase64).trim();
      const eventStartDate = String(body?.eventStartDate ?? body?.eventDate ?? "").trim();
      const eventEndDate = String(body?.eventEndDate ?? body?.eventDate ?? "").trim();
      const hostCountry = String(body?.hostCountry ?? "").trim().toUpperCase();
      const hostCity = String(body?.hostCity ?? "").trim();
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
      if (!/^[A-Z]{2,3}$/.test(hostCountry)) {
        issues.push({ field: "hostCountry", message: "Must use a two-letter country code or legacy three-letter EMA code", value: body?.hostCountry });
      }
      if (!hostCity) issues.push({ field: "hostCity", message: "Required", value: body?.hostCity });
      if (!shortName || shortName.length > 10) {
        issues.push({ field: "shortName", message: "Required, with at most 10 characters", value: body?.shortName });
      }
      if (!/^#[0-9A-F]{6}$/.test(primaryColor)) {
        issues.push({ field: "primaryColor", message: "Must use #RRGGBB format", value: body?.primaryColor });
      }
      if ((associationLogoContentType == null) !== (associationLogoDataBase64 == null)) {
        issues.push({ field: "associationLogo", message: "Content type and image data must be supplied together" });
      }
      if (!isValidIsoDate(eventStartDate)) {
        issues.push({ field: "eventStartDate", message: "Must use yyyy-MM-dd", value: body?.eventStartDate });
      }
      if (!isValidIsoDate(eventEndDate)) {
        issues.push({ field: "eventEndDate", message: "Must use yyyy-MM-dd", value: body?.eventEndDate });
      }
      if (isValidIsoDate(eventStartDate) && isValidIsoDate(eventEndDate) && !isValidIsoDateRange(eventStartDate, eventEndDate)) {
        issues.push({ field: "eventEndDate", message: "Must not be before eventStartDate", value: body?.eventEndDate });
      }

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
          received: body == null ? null : {
            ...body,
            associationLogoDataBase64: associationLogoDataBase64 == null ? null : "[image data]",
          },
          inferred: { name, isTeams, numPlayers, numRounds, numTries, playersCount: players?.length, tablesCount: tables?.length },
        });
      }

      const parsedPlayers = (players ?? []).map((p, idx) => {
        const row = (p != null && typeof p === "object") ? (p as Record<string, unknown>) : null;
        const id = Number(row?.id ?? NaN);
        const team = Number(row?.team ?? NaN);
        const playerName = row?.name == null ? undefined : String(row?.name);
        const country = row?.country == null ? undefined : String(row?.country);
        if (!Number.isFinite(id) || !Number.isInteger(id)) throw badRequest("Invalid player id", { idx, value: row?.id });
        if (!Number.isFinite(team) || !Number.isInteger(team)) throw badRequest("Invalid player team", { idx, value: row?.team });
        return { id, team, name: playerName, country };
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
        shortName,
        primaryColor,
        associationLogoContentType,
        associationLogoDataBase64,
        eventStartDate,
        eventEndDate,
        hostCountry,
        hostCity,
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

  router.get("/:tournamentId/id-cards", requireAuth, requireTournamentEditor, async (req, res, next) => {
    try {
      const pdf = await generateTournamentIdCards(req.params.tournamentId);
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${req.params.tournamentId}-id-cards.pdf"`);
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  });

  router.post("/:tournamentId/ema-report", requireAuth, requireTournamentEditor, async (req, res, next) => {
    try {
      const rawRows = Array.isArray(req.body?.rows) ? req.body.rows as unknown[] : null;
      if (rawRows == null || rawRows.length === 0) throw badRequest("Ranking rows are required");
      const rows = rawRows.map((value, index) => {
        if (value == null || typeof value !== "object") throw badRequest("Invalid ranking row", { index });
        const row = value as Record<string, unknown>;
        const parsed = {
          playerId: Number(row.playerId),
          place: Number(row.place),
          tablePoints: Number(row.tablePoints),
          score: Number(row.score),
        };
        if (!Number.isInteger(parsed.playerId) || parsed.playerId <= 0
          || !Number.isInteger(parsed.place) || parsed.place <= 0
          || !Number.isFinite(parsed.tablePoints) || !Number.isFinite(parsed.score)) {
          throw badRequest("Invalid ranking row", { index, value });
        }
        return parsed;
      });
      const report = await generateEmaReport({ tournamentId: req.params.tournamentId, rows });
      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${req.params.tournamentId}-ema.xlsx"`);
      res.status(200).send(report);
    } catch (e) {
      next(e);
    }
  });

  router.put("/:tournamentId/settings", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
      if (body == null) throw badRequest("Request body must be JSON");
      const name = String(body.name ?? "").trim();
      const shortName = String(body.shortName ?? "").trim();
      const primaryColor = String(body.primaryColor ?? "").trim().toUpperCase();
      const eventStartDate = String(body.eventStartDate ?? "").trim();
      const eventEndDate = String(body.eventEndDate ?? "").trim();
      const hostCountry = String(body.hostCountry ?? "").trim().toUpperCase();
      const hostCity = String(body.hostCity ?? "").trim();
      const associationLogoContentType = body.associationLogoContentType == null
        ? null
        : String(body.associationLogoContentType).trim();
      const associationLogoDataBase64 = body.associationLogoDataBase64 == null
        ? null
        : String(body.associationLogoDataBase64).trim();
      if (!name) throw badRequest("Tournament name is required");
      if (!shortName || shortName.length > 10) {
        throw badRequest("Tournament short name must contain 1 to 10 characters");
      }
      if (!/^#[0-9A-F]{6}$/.test(primaryColor)) {
        throw badRequest("Primary color must use #RRGGBB format");
      }
      if (!isValidIsoDateRange(eventStartDate, eventEndDate)) {
        throw badRequest("Tournament dates must use yyyy-MM-dd, and the end date must not be before the start date");
      }
      if (!/^[A-Z]{2,3}$/.test(hostCountry)) {
        throw badRequest("Host country must use a two-letter country code or legacy three-letter EMA code");
      }
      if (!hostCity) throw badRequest("Host city is required");
      if ((associationLogoContentType == null) !== (associationLogoDataBase64 == null)) {
        throw badRequest("Association logo content type and image data must be supplied together");
      }
      const tournament = await updateTournamentSettings({
        tournamentId: req.params.tournamentId,
        name,
        shortName,
        primaryColor,
        eventStartDate,
        eventEndDate,
        hostCountry,
        hostCity,
        associationLogoContentType,
        associationLogoDataBase64,
        removeAssociationLogo: body.removeAssociationLogo === true,
      });
      res.status(200).json(tournament);
    } catch (e) {
      next(e);
    }
  });

  router.delete("/:tournamentId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      await deleteTournament(req.params.tournamentId);
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.put("/:tournamentId", requireAuth, requireAdmin, async (req, res, next) => {
    try {
      const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
      const name = String(body?.name ?? "").trim();
      if (!name) throw badRequest("Tournament name is required");

      await renameTournament(req.params.tournamentId, name);
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.get(
    "/:tournamentId/users/lookup",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const email = String(req.query.email ?? "").trim();
        if (!email.includes("@")) throw badRequest("Enter a user email address");
        const profile = await auth.getUserByEmail(email).then((user) => getUserProfile(user.uid));
        res.status(200).json(profile);
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/members",
    requireAuth,
    requireTournamentEditor,
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
    "/:tournamentId/players/:playerId",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const playerId = Number(req.params.playerId);
        const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
        const rawEmaId = body?.emaId;
        const emaId = rawEmaId == null ? null : validateEmaId(rawEmaId);
        const rawNonMember = body?.nonMember;
        let nonMember: { firstName: string; lastName: string; country: string } | null = null;
        if (rawNonMember != null) {
          if (typeof rawNonMember !== "object") throw badRequest("nonMember must be an object");
          const value = rawNonMember as Record<string, unknown>;
          nonMember = {
            firstName: String(value.firstName ?? "").trim(),
            lastName: String(value.lastName ?? "").trim(),
            country: String(value.country ?? "").trim().toUpperCase(),
          };
          if (!nonMember.firstName || !nonMember.lastName || !/^[A-Z]{3}$/.test(nonMember.country)) {
            throw badRequest("A non-member needs first name, last name, and a three-letter country code");
          }
        }

        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        if (emaId != null && nonMember != null) throw badRequest("Choose an EMA player or a non-member, not both");
        if (emaId != null && !(await playerExists(emaId))) {
          throw badRequest("EMA player does not exist");
        }

        await assignTournamentPlayer({
          tournamentId: req.params.tournamentId,
          playerId,
          emaId,
          nonMember,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/members/:uid",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        await upsertTournamentMember({
          tournamentId: req.params.tournamentId,
          uid: req.params.uid,
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
    requireTournamentEditor,
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
    requireTournamentEditor,
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
    "/:tournamentId/teams",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const teams = await listTournamentTeams(req.params.tournamentId);
        res.status(200).json({ teams });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/teams/:teamId",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const teamId = Number(req.params.teamId);
        const body = (req.body != null && typeof req.body === "object")
          ? req.body as Record<string, unknown>
          : null;
        const name = String(body?.name ?? "").trim();
        const rawEmaIds = Array.isArray(body?.emaIds) ? body.emaIds : null;
        if (!Number.isInteger(teamId) || teamId <= 0) {
          throw badRequest("teamId must be a positive integer");
        }
        if (name.length === 0 || name.length > 80) {
          throw badRequest("Team name must contain 1 to 80 characters");
        }
        if (rawEmaIds == null || rawEmaIds.length > 4) {
          throw badRequest("emaIds must contain up to four team slots");
        }
        const emaIds = rawEmaIds.map((value, index) => {
          if (value == null || String(value).trim().length === 0) return null;
          try {
            return validateEmaId(value);
          } catch (_error) {
            throw badRequest("Invalid EMA player", { index, value });
          }
        });
        const assignedEmaIds = emaIds.filter((emaId): emaId is string => emaId != null);
        if (new Set(assignedEmaIds).size !== assignedEmaIds.length) {
          throw badRequest("A player cannot occupy two team slots");
        }
        const existence = await Promise.all(assignedEmaIds.map((emaId) => playerExists(emaId)));
        const missingIndex = existence.findIndex((exists) => !exists);
        if (missingIndex >= 0) {
          throw badRequest("EMA player does not exist", { emaId: assignedEmaIds[missingIndex] });
        }

        await updateTournamentTeam({
          tournamentId: req.params.tournamentId,
          teamId,
          name,
          emaIds,
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/rounds",
    requireAuth,
    requireTournamentEditor,
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
    requireTournamentEditor,
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
    requireTournamentEditor,
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
    requireTournamentEditor,
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
    requireTournamentEditor,
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

  router.post(
    "/:tournamentId/tables/:roundId/:tableId/reset",
    requireAuth,
    requireTournamentEditor,
    async (req, res, next) => {
      try {
        const roundId = Number(req.params.roundId);
        const tableId = Number(req.params.tableId);
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        await resetTable({
          tournamentId: req.params.tournamentId,
          roundId,
          tableId,
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
    requireTournamentEditor,
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
