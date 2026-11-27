import { Router } from "express";
import { registerDocumentRoutes } from "./tournaments.documents.routes";
import { registerPeopleRoutes } from "./tournaments.people.routes";
import { registerRegistrationRoutes } from "./tournaments.registrations.routes";
import { registerStatisticsRoutes } from "./tournaments.statistics.routes";
import { registerRoundAndTableRoutes } from "./tournaments.tables.routes";
import { recordActivity, tournamentNameOf } from "../../services/activityLog";
import { db } from "../../firebase";
import { requireAuth } from "../middleware/requireAuth";
import { accessOf, requirePermission, requireTournamentPermission } from "../middleware/requirePermission";
import { assertSafeDocumentId, badRequest, forbidden } from "../httpError";
import { WORLD_COUNTRY_CODES } from "../../services/countryCodes";
import {
  canChangeOrganizers,
  canInOrganization,
  forgetTournamentOrganizers,
  isStaff,
  loadAccess,
  tournamentOrganizers,
  tournamentPermissions,
} from "../../services/accessService";
import { parseOrganizerIds } from "../../services/organizationsService";
import { listVisibleTournaments, tournamentHasResults, tournamentIsPast } from "../../services/tournamentScope";
import { applyLostAssignments } from "../../services/membersService";
import { createTournament, deleteTournament, renameTournament, updateTournamentSettings } from "../../services/tournamentsService";
import { actorFromToken } from "../../services/changeLog";
import { isValidPlayerCount, MIN_TOURNAMENT_PLAYERS } from "../../services/tournamentContentRules";
import { isValidIsoDate, isValidIsoDateRange } from "../../services/tournamentDates";
import { normalizeVenue, venueRequiredProblems } from "../../services/tournamentVenue";
import { getTournamentManifest } from "../../services/dataVersionsService";
import { pathParam } from "../pathParam";
import { claimSlug, freeSlug } from "../../services/registrationsService";
import { defaultSlugs, parseClosesOn, parseMaxPlayers, parseSlug } from "../../services/registrationSettings";

export function tournamentsRouter(): Router {
  const router = Router();

  router.param("tournamentId", (_req, _res, next, value: string) => {
    try {
      assertSafeDocumentId(value, "tournament id");
      next();
    } catch (error) {
      next(error);
    }
  });

  router.get(
    "/:tournamentId/sync/manifest",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        res.setHeader("Cache-Control", "no-store");
        res.status(200).json(await getTournamentManifest(pathParam(req, "tournamentId")));
      } catch (error) {
        next(error);
      }
    },
  );

  /**
   * Super admins see every tournament, club admins the tournaments of their organizations and editors the ones
   * assigned to them. Each tournament carries the permissions of the account in it, so the app shows only the actions
   * that the account may use. The player app reads /public instead.
   */
  router.get("/", requireAuth, async (_req, res, next) => {
    try {
      const access = await loadAccess((res.locals.auth as { uid: string }).uid);
      if (access.disabled || !isStaff(access)) {
        res.status(200).json({ tournaments: [] });
        return;
      }
      const { tournaments, memberIds } = await listVisibleTournaments(access);
      const withPermissions = await Promise.all(tournaments.map(async (tournament) => ({
        ...tournament,
        permissions: await tournamentPermissions(access, tournament.id, {
          organizerIds: tournament.organizerIds,
          member: memberIds.has(tournament.id),
        }),
      })));
      res.status(200).json({ tournaments: withPermissions });
    } catch (e) {
      next(e);
    }
  });

  registerStatisticsRoutes(router);


  router.post("/", requireAuth, requirePermission("tournaments.create"), async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string };

      const body = (req.body != null && typeof req.body === "object") ? (req.body as Record<string, unknown>) : null;

      const name = String(body?.name ?? "").trim();
      const shortName = String(body?.shortName ?? name.slice(0, 10)).trim();
      const eventStartDate = String(body?.eventStartDate ?? "").trim();
      const eventEndDate = String(body?.eventEndDate ?? "").trim();
      const hostCountry = String(body?.hostCountry ?? "").trim().toUpperCase();
      const hostCity = String(body?.hostCity ?? "").trim();
      const isTeams = Boolean(body?.isTeams ?? false);
      const countBestHands = body?.countBestHands !== false;
      const countChickenHands = body?.countChickenHands !== false;

      const numPlayersValue = body?.numPlayers;
      const numRoundsValue = body?.numRounds;
      const numTriesValue = body?.numTries;

      const numPlayers = Number(numPlayersValue ?? NaN);
      const numRounds = Number(numRoundsValue ?? NaN);
      const numTries = Number(numTriesValue ?? NaN);
      const players = Array.isArray(body?.players) ? (body?.players as unknown[]) : null;
      const tables = Array.isArray(body?.tables) ? (body?.tables as unknown[]) : null;

      const issues: Array<{ field: string; message: string; value?: unknown }> = [];
      let venue = normalizeVenue(null);
      try {
        venue = normalizeVenue(body);
      } catch (error) {
        issues.push({ field: "venue", message: error instanceof Error ? error.message : "Invalid venue" });
      }
      if (issues.length === 0) {
        for (const message of venueRequiredProblems(venue)) {
          issues.push({ field: message === "Required" ? "contactEmail" : "venue", message });
        }
      }
      if (!body) issues.push({ field: "body", message: "Request body must be JSON", value: req.body });
      if (!name) issues.push({ field: "name", message: "Required", value: body?.name });
      if (!WORLD_COUNTRY_CODES.has(hostCountry)) {
        issues.push({ field: "hostCountry", message: "Must be the two-letter code of a real country", value: body?.hostCountry });
      }
      if (!hostCity) issues.push({ field: "hostCity", message: "Required", value: body?.hostCity });
      if (!shortName || shortName.length > 10) {
        issues.push({ field: "shortName", message: "Required, with at most 10 characters", value: body?.shortName });
      }
      if (!Array.isArray(body?.organizerIds) || body.organizerIds.length === 0) {
        issues.push({ field: "organizerIds", message: "Required: at least one organization", value: body?.organizerIds });
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

      // A tournament with registrations has no players and no draw yet: the draw comes when they close.
      const registrationBody = body?.registration != null && typeof body.registration === "object"
        ? body.registration as Record<string, unknown>
        : null;
      if (!registrationBody && !isValidPlayerCount(numPlayers)) {
        issues.push({ field: "numPlayers", message: `Must be a multiple of 4 and at least ${MIN_TOURNAMENT_PLAYERS}`, value: numPlayersValue });
      }
      if (!Number.isFinite(numRounds) || !Number.isInteger(numRounds) || numRounds <= 0) {
        issues.push({ field: "numRounds", message: "Must be a positive integer", value: numRoundsValue });
      }
      if (!registrationBody && (!Number.isFinite(numTries) || !Number.isInteger(numTries) || numTries <= 0)) {
        issues.push({ field: "numTries", message: "Must be a positive integer", value: numTriesValue });
      }
      if (!registrationBody && !players) issues.push({ field: "players", message: "Required (array)", value: body?.players });
      if (!registrationBody && !tables) issues.push({ field: "tables", message: "Required (array)", value: body?.tables });
      let registration: Record<string, unknown> | undefined;
      if (registrationBody) {
        try {
          // No link name typed (null or empty): the tournament gets the shortest free one from its short name (and year).
          const typedSlug = String(registrationBody.slug ?? "").trim();
          const slug = typedSlug === "" ? await freeSlug(defaultSlugs(shortName || name, eventStartDate).map(parseSlug)) : parseSlug(typedSlug);
          registration = {
            status: registrationBody.status === "CLOSED" ? "CLOSED" : "OPEN",
            maxPlayers: parseMaxPlayers(registrationBody.maxPlayers),
            closesOn: parseClosesOn(registrationBody.closesOn),
            slug,
            confirmed: 0,
            waiting: 0,
          };
        } catch (error) {
          issues.push({ field: "registration", message: error instanceof Error ? error.message : "Invalid registration" });
        }
      }

      if (issues.length > 0) {
        throw badRequest("Missing or invalid tournament fields", {
          issues,
          received: body,
          inferred: { name, isTeams, numPlayers, numRounds, numTries, playersCount: players?.length, tablesCount: tables?.length },
        });
      }
      // The first organizer is the main one. A club admin creates tournaments that one of its organizations organizes.
      const organizerIds = await parseOrganizerIds(body?.organizerIds);
      if (!organizerIds.some((id) => canInOrganization(accessOf(res), "tournaments.create", id))) {
        throw forbidden("You can create tournaments only for your organizations");
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
        organizerIds,
        eventStartDate,
        eventEndDate,
        hostCountry,
        hostCity,
        ...venue,
        isTeams,
        countBestHands,
        countChickenHands,
        numPlayers: registration ? 0 : numPlayers,
        numRounds,
        numTries: registration ? 0 : numTries,
        players: parsedPlayers,
        tables: parsedTables,
        registration,
        roundSchedules: body?.roundSchedules,
        agendaItems: body?.agendaItems,
        matchDurationMinutes: body?.matchDurationMinutes,
        matchEndMode: body?.matchEndMode,
        matchExtraMinutes: body?.matchExtraMinutes,
        timeZone: body?.timeZone,
        createdByUid: decoded.uid,
      });

      if (registration) {
        try {
          await claimSlug(String(registration.slug), tournament.id, null);
        } catch (error) {
          await deleteTournament(tournament.id).catch(() => undefined);
          throw error;
        }
      }
      await recordActivity({
        action: "tournament.create", actor: actorFromToken(res.locals.auth), tournamentId: tournament.id,
        target: tournament.name,
        details: { shortName, organizerIds, eventStartDate, eventEndDate, hostCountry, hostCity, isTeams, numPlayers, numRounds, withRegistration: registration != null },
      });
      res.status(200).json(tournament);
    } catch (e) {
      next(e);
    }
  });

  registerDocumentRoutes(router);

  router.put("/:tournamentId/settings", requireAuth, requireTournamentPermission("tournament.configure"), async (req, res, next) => {
    try {
      const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
      if (body == null) throw badRequest("Request body must be JSON");
      const name = String(body.name ?? "").trim();
      const shortName = String(body.shortName ?? "").trim();
      const eventStartDate = String(body.eventStartDate ?? "").trim();
      const eventEndDate = String(body.eventEndDate ?? "").trim();
      const hostCountry = String(body.hostCountry ?? "").trim().toUpperCase();
      const hostCity = String(body.hostCity ?? "").trim();
      if (!name) throw badRequest("Tournament name is required");
      if (!shortName || shortName.length > 10) {
        throw badRequest("Tournament short name must contain 1 to 10 characters");
      }
      if (!isValidIsoDateRange(eventStartDate, eventEndDate)) {
        throw badRequest("Tournament dates must use yyyy-MM-dd, and the end date must not be before the start date");
      }
      if (!WORLD_COUNTRY_CODES.has(hostCountry)) {
        throw badRequest("Host country must be the two-letter code of a real country");
      }
      // The organizers decide who manages the tournament. See canChangeOrganizers.
      const tournamentId = pathParam(req, "tournamentId");
      const currentOrganizerIds = (await tournamentOrganizers(tournamentId)) ?? [];
      const organizerIds = body.organizerIds === undefined ? undefined : await parseOrganizerIds(body.organizerIds);
      const organizersChange = organizerIds !== undefined && organizerIds.join(",") !== currentOrganizerIds.join(",");
      if (organizersChange && !canChangeOrganizers(accessOf(res), currentOrganizerIds, organizerIds)) {
        throw forbidden("Only a super admin or a club admin of the main organizer changes the organizers, and the new ones must hold one of your organizations");
      }
      if (!hostCity) throw badRequest("Host city is required");
      const venue = normalizeVenue(body);
      const venueProblems = venueRequiredProblems(venue);
      if (venueProblems.length > 0) throw badRequest(venueProblems.join(". "));
      const tournament = await updateTournamentSettings({
        tournamentId,
        name,
        shortName,
        organizerIds: organizersChange ? organizerIds : undefined,
        eventStartDate,
        eventEndDate,
        hostCountry,
        hostCity,
        ...venue,
        countBestHands: typeof body.countBestHands === "boolean" ? body.countBestHands : undefined,
        countChickenHands: typeof body.countChickenHands === "boolean" ? body.countChickenHands : undefined,
        isPublic: typeof body.isPublic === "boolean" ? body.isPublic : undefined,
        showTableDiffs: typeof body.showTableDiffs === "boolean" ? body.showTableDiffs : undefined,
        hideResultsFromRound: body.hideResultsFromRound,
        roundSchedules: body.roundSchedules,
        agendaItems: body.agendaItems,
        matchDurationMinutes: body.matchDurationMinutes,
        matchEndMode: body.matchEndMode,
        matchExtraMinutes: body.matchExtraMinutes,
        timeZone: body.timeZone,
      });
      await recordActivity({
        action: "tournament.update", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
        target: name,
        details: {
          shortName, organizerIds: organizersChange ? organizerIds : undefined, eventStartDate, eventEndDate, hostCountry, hostCity,
          countBestHands: body.countBestHands, countChickenHands: body.countChickenHands, isPublic: body.isPublic,
          showTableDiffs: body.showTableDiffs, hideResultsFromRound: body.hideResultsFromRound,
          matchDurationMinutes: body.matchDurationMinutes, matchEndMode: body.matchEndMode,
          matchExtraMinutes: body.matchExtraMinutes, timeZone: body.timeZone,
          roundSchedules: body.roundSchedules, agendaItems: body.agendaItems,
        },
      });
      if (organizersChange) forgetTournamentOrganizers(tournamentId);
      res.status(200).json(tournament);
    } catch (e) {
      next(e);
    }
  });

  router.delete("/:tournamentId", requireAuth, requireTournamentPermission("tournament.delete"), async (req, res, next) => {
    try {
      const tournamentId = pathParam(req, "tournamentId");
      // A tournament that ended with results is history. Only a super admin deletes it.
      const snapshot = await db.doc(`tournaments/${tournamentId}`).get();
      if (snapshot.exists && tournamentIsPast(snapshot.get("eventEndDate")) && await tournamentHasResults(tournamentId)
        && !accessOf(res).superAdmin) {
        throw forbidden("Only a super admin can delete a finished tournament with results");
      }
      const memberUids = (await db.collection(`tournaments/${tournamentId}/members`).get()).docs.map((member) => member.id);
      const removed = await tournamentNameOf(tournamentId);
      await deleteTournament(tournamentId);
      forgetTournamentOrganizers(tournamentId);
      await applyLostAssignments(memberUids);
      await recordActivity({
        action: "tournament.delete", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
        target: removed,
      });
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  router.put("/:tournamentId", requireAuth, requireTournamentPermission("tournament.configure"), async (req, res, next) => {
    try {
      const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
      const name = String(body?.name ?? "").trim();
      if (!name) throw badRequest("Tournament name is required");

      await renameTournament(pathParam(req, "tournamentId"), name);
      await recordActivity({
        action: "tournament.rename", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
        target: name,
      });
      res.status(200).json({ ok: true });
    } catch (e) {
      next(e);
    }
  });

  registerPeopleRoutes(router);
  registerRoundAndTableRoutes(router);
  registerRegistrationRoutes(router);

  // Placeholder: future endpoints for players/teams/tables/hands will live here.
  router.get(
    "/:tournamentId",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res) => {
      res.status(501).json({
        error: "not_implemented",
        message: "Tournament details endpoint not implemented yet",
        tournamentId: pathParam(req, "tournamentId"),
      });
    },
  );

  return router;
}
