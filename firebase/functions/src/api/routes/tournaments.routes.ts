import { NextFunction, Request, Response, Router } from "express";
import { parseAcceptLanguage } from "../../services/i18n";
import { recordActivity, tournamentNameOf } from "../../services/activityLog";

import { auth, db } from "../../firebase";
import { requireAuth } from "../middleware/requireAuth";
import { accessOf, requirePermission, requireTournamentPermission } from "../middleware/requirePermission";
import { runAccountAction, savePendingEmails } from "../../services/playerAccountsService";
import { assertSafeDocumentId, badRequest, forbidden } from "../httpError";
import { WORLD_COUNTRY_CODES } from "../../services/countryCodes";
import { canChangeOrganizers, canInOrganization, canInTournament, forgetTournamentOrganizers, isStaff, loadAccess, tournamentOrganizers, tournamentPermissions, type Access } from "../../services/accessService";
import { parseOrganizerIds } from "../../services/organizationsService";
import { listVisibleTournaments, tournamentHasResults, tournamentIsPast, visibleTournamentIdSet } from "../../services/tournamentScope";
import { applyLostAssignments, listTournamentMembers, removeTournamentMember, upsertTournamentMember } from "../../services/membersService";
import {
  createTournament,
  deleteTournament,
  listAllTournaments,
  renameTournament,
  updateTournamentSettings,
} from "../../services/tournamentsService";
import {
  buildIdCardProofPdf,
  generateTournamentIdCards,
  idCardSheetGrid,
  MAX_EXTRA_SHEETS,
  MAX_STAFF_NAME_LENGTH,
  MAX_STAFF_NAMES,
  type IdCardSheetOptions,
} from "../../services/idCardsService";
import { generateTournamentIdList } from "../../services/idListService";
import { generateEmaReport } from "../../services/emaReportService";
import { readSubstitution } from "../../services/playerSubstitution";
import {
  assignTournamentPlayer,
  setPlayerSubstitution,
  listTournamentPlayers,
  listTournamentRounds,
  listTournamentTables,
  listTournamentTeams,
  updateTournamentTeam,
} from "../../services/tournamentContentService";
import { listHiddenEmaIds, playerExists, validateEmaId } from "../../services/playersService";
import {
  getAllTablesWithHands,
  getRoundTablesWithHands,
  getTableWithHands,
  resetTable,
  saveTableState,
} from "../../services/tableManagerService";
import { actorFromToken } from "../../services/changeLog";
import { getUserProfile } from "../../services/usersService";
import { isValidPlayerCount, MIN_TOURNAMENT_PLAYERS } from "../../services/tournamentContentRules";
import { isValidIsoDate, isValidIsoDateRange } from "../../services/tournamentDates";
import { normalizeVenue, venueRequiredProblems } from "../../services/tournamentVenue";
import { getTournamentManifest } from "../../services/dataVersionsService";
import { completeTables, getRoundClocks, updateRoundClock, updateRoundControl } from "../../services/roundClockService";
import { parseRoundClockAction } from "../../services/roundClock";
import { parseRoundControlChange, parseTablesToComplete } from "../../services/roundControl";
import { parseSaveTableRequest } from "../tableRequests";
import { pathParam } from "../pathParam";
import { readGroupFilter } from "../groupFilter";
import { groupTournament, loadGroupStatistics } from "../../services/statisticsGroups";
import { loadOverview, loadPersonStatistics, loadStandings, MAX_STANDINGS_PER_REQUEST, publicTournamentIds } from "../../services/statisticsStore";
import { applyPlayerImport, exportPlayers, previewPlayerImport } from "../../services/playerImportService";
import { runTournamentIntegrityCheck } from "../../services/tournamentIntegrityService";
import {
  adminAddRegistration,
  adminRegistrations,
  adminRemoveRegistration,
  adminUpdateRegistration,
  claimSlug,
  freeSlug,
  importRegistrations,
  updateRegistrationSettings,
} from "../../services/registrationsService";
import { makeDrawFromRegistrations } from "../../services/registrationDraw";
import { changePlayerCount } from "../../services/playerCountChange";
import { defaultSlugs, parseClosesOn, parseMaxPlayers, parsePhase, parseSlug } from "../../services/registrationSettings";

/** Reads the optional A4 sheet layout of the ID cards. No `layout=sheet` value means one card per page. */
function readExtraSheets(value: unknown, kind: string): number {
  const sheets = Number(value ?? 0);
  if (!Number.isInteger(sheets) || sheets < 0 || sheets > MAX_EXTRA_SHEETS) {
    throw badRequest(`Invalid number of ${kind} sheets`, { value });
  }
  return sheets;
}

function readStaffNames(value: unknown): string[] {
  const values = value == null ? [] : Array.isArray(value) ? value : [value];
  if (values.length > MAX_STAFF_NAMES) throw badRequest("Too many staff names", { count: values.length });
  return values.map((name) => {
    if (typeof name !== "string" || name.trim().length > MAX_STAFF_NAME_LENGTH) {
      throw badRequest("Invalid staff name", { value: name });
    }
    return name.trim();
  });
}

function readIdCardSheetOptions(query: Request["query"]): IdCardSheetOptions | undefined {
  if (query.layout !== "sheet") return undefined;
  const cardsPerSheet = Number(query.cardsPerSheet ?? 10);
  const scalePercent = Number(query.scalePercent ?? 100);
  const landscape = query.landscape === "true";
  if (!Number.isInteger(cardsPerSheet) || cardsPerSheet < 1 || cardsPerSheet > 40) {
    throw badRequest("Invalid number of cards per sheet", { value: query.cardsPerSheet });
  }
  if (!Number.isFinite(scalePercent) || scalePercent < 25 || scalePercent > 400) {
    throw badRequest("Invalid card size", { value: query.scalePercent });
  }
  const options: IdCardSheetOptions = {
    cardsPerSheet,
    scalePercent,
    landscape,
    doubleSided: query.doubleSided !== "false",
    cutMarks: query.cutMarks !== "false",
    backs: query.backs !== "false",
    substituteSheets: readExtraSheets(query.substituteSheets, "substitute"),
    staffSheets: readExtraSheets(query.staffSheets, "staff"),
    staffNames: readStaffNames(query.staffName),
  };
  if (idCardSheetGrid(options) == null) throw badRequest("The card size does not fit on an A4 sheet", { scalePercent });
  return options;
}

/**
 * Only a super admin or a club admin of the tournament changes the tables of a completed tournament, so late
 * changes to the results are traceable.
 */
async function assertTournamentOpenForEditor(tournamentId: string, access: Access): Promise<void> {
  if (await canInTournament(access, "tournament.editCompleted", tournamentId)) return;
  const snapshot = await db.collection("tournaments").doc(tournamentId).get();
  if (snapshot.get("isCompleted") === true) {
    throw forbidden("The tournament is completed. Only an admin of the tournament can change its tables");
  }
}

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

  /**
   * The statistics of all tournaments together: sums and the leaderboards.
   * The server keeps them up to date when a table changes. This request reads a fixed number of documents.
   * Statistics are public data, so every signed-in account can read them.
   */
  router.get("/statistics/overview", requireAuth, async (_req, res, next) => {
    try {
      res.status(200).json(await loadOverview());
    } catch (e) {
      next(e);
    }
  });

  /**
   * The ids of the tournaments whose statistics the signed-in account can see, or null for a super admin.
   * Statistics are public data: every account sees the public tournaments, and the staff also the ones that it manages.
   */
  const visibleTournamentIds = async (decoded: { uid: string }) => {
    const access = await loadAccess(decoded.uid);
    if (access.superAdmin && !access.disabled) return null;
    const visible = await publicTournamentIds();
    if (!access.disabled && isStaff(access)) ((await visibleTournamentIdSet(access)) ?? []).forEach((id) => visible.add(id));
    return visible;
  };

  const readEmaParam = (value: unknown): string => {
    const emaId = String(value ?? "").trim();
    if (emaId === "" || emaId.length > 64) throw badRequest("Invalid EMA id");
    return emaId;
  };

  /** The EMA number of the signed-in account. It is empty when the account is not linked to a player. */
  const ownEmaId = async (uid: string): Promise<string> => (await getUserProfile(uid)).emaId ?? "";

  const readTournamentIds = (value: unknown): string[] => {
    const ids = String(value ?? "").split(",").map((id) => id.trim()).filter((id) => id !== "");
    if (ids.length > MAX_STANDINGS_PER_REQUEST) throw badRequest("Too many tournaments", { max: MAX_STANDINGS_PER_REQUEST });
    return ids;
  };

  /**
   * The results of one player, in the tournaments that the account can see. The own route reads the player of the account.
   * The route with an EMA number is open to every signed-in account, so a player can look at the statistics of others.
   */
  const personHandler = (own: boolean) => async (req: Request, res: Response, next: NextFunction) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(pathParam(req, "emaId"));
      if (emaId === "") {
        res.status(200).json({ emaId, tournaments: [] });
        return;
      }
      res.status(200).json(await loadPersonStatistics(emaId, await visibleTournamentIds(decoded)));
    } catch (e) {
      next(e);
    }
  };

  /** The final places of one player in a few tournaments. The app asks for the tournaments on screen. */
  const standingsHandler = (own: boolean) => async (req: Request, res: Response, next: NextFunction) => {
    try {
      const decoded = res.locals.auth as { uid: string };
      const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(pathParam(req, "emaId"));
      const allowed = await visibleTournamentIds(decoded);
      const ids = readTournamentIds(req.query.tournaments).filter((id) => allowed == null || allowed.has(id));
      res.status(200).json({ standings: emaId === "" ? [] : await loadStandings(emaId, ids) });
    } catch (e) {
      next(e);
    }
  };

  /** The statistics of a group of tournaments and players, in the tournaments that the account can see. See readGroupFilter. */
  router.get("/statistics/groups", requireAuth, async (req, res, next) => {
    try {
      const decoded = res.locals.auth as { uid: string; admin?: boolean; editor?: boolean };
      const filter = readGroupFilter(req.query as Record<string, unknown>);
      const [tournaments, allowed] = await Promise.all([listAllTournaments(), visibleTournamentIds(decoded)]);
      const visible = tournaments.filter((tournament) => allowed == null || allowed.has(tournament.id)).map(groupTournament);
      // An administrator also sees the people who asked not to be shown publicly.
      const hidden = allowed == null ? new Set<string>() : await listHiddenEmaIds();
      res.status(200).json(await loadGroupStatistics(visible, filter, hidden));
    } catch (e) {
      next(e);
    }
  });

  router.get("/statistics/me", requireAuth, personHandler(true));
  router.get("/statistics/me/standings", requireAuth, standingsHandler(true));
  router.get("/statistics/persons/:emaId", requireAuth, personHandler(false));
  router.get("/statistics/persons/:emaId/standings", requireAuth, standingsHandler(false));

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

  const generateIdCardProof = async (req: Request, res: Response, next: NextFunction) => {
    try {
      const body = (req.body != null && typeof req.body === "object")
        ? req.body as Record<string, unknown>
        : {};
      const shortName = String(body.shortName ?? "").trim();
      const year = String(body.year ?? "").trim();
      const primaryColor = String(body.primaryColor ?? "#02B16B").trim().toUpperCase();
      const associationLogoContentType = body.associationLogoContentType == null
        ? null
        : String(body.associationLogoContentType).trim();
      const associationLogoDataBase64 = body.associationLogoDataBase64 == null
        ? null
        : String(body.associationLogoDataBase64).trim();
      const associationLogoUrl = body.associationLogoUrl == null
        ? null
        : String(body.associationLogoUrl).trim();
      const roundSchedules = body.roundSchedules;

      if (shortName.length > 32 || year.length > 4 || !/^#[0-9A-F]{6}$/.test(primaryColor)) {
        throw badRequest("Invalid ID card preview fields");
      }
      if (associationLogoDataBase64 && associationLogoDataBase64.length > 3_000_000) {
        throw badRequest("ID card preview logo is too large");
      }

      const pdf = await buildIdCardProofPdf({
        shortName,
        year,
        primaryColor,
        associationLogoContentType,
        associationLogoDataBase64,
        associationLogoUrl,
        roundSchedules,
      });
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Cache-Control", "private, max-age=3600");
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  };

  router.post("/id-card-proof", requireAuth, requirePermission("tools.idCards"), generateIdCardProof);
  // Keep the previous route working for already deployed web bundles.
  router.post("/id-card-preview", requireAuth, requirePermission("tools.idCards"), generateIdCardProof);

  // Reads the whole tournament and reports wrong or odd data. It changes nothing.
  router.get("/:tournamentId/integrity", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      res.setHeader("Cache-Control", "no-store");
      res.status(200).json(await runTournamentIntegrityCheck(pathParam(req, "tournamentId")));
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-cards", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const pdf = await generateTournamentIdCards(pathParam(req, "tournamentId"), readIdCardSheetOptions(req.query));
      res.setHeader("Content-Type", "application/pdf");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-id-cards.pdf"`);
      res.status(200).send(pdf);
    } catch (e) {
      next(e);
    }
  });

  router.get("/:tournamentId/id-list", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
    try {
      const workbook = await generateTournamentIdList(pathParam(req, "tournamentId"));
      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-id-list.xlsx"`);
      res.status(200).send(workbook);
    } catch (e) {
      next(e);
    }
  });

  router.post("/:tournamentId/ema-report", requireAuth, requireTournamentPermission("tournament.view"), async (req, res, next) => {
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
      const report = await generateEmaReport({ tournamentId: pathParam(req, "tournamentId"), rows });
      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="tournament-${pathParam(req, "tournamentId")}-ema.xlsx"`);
      res.status(200).send(report);
    } catch (e) {
      next(e);
    }
  });

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
          showTableDiffs: body.showTableDiffs,
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

  router.get(
    "/:tournamentId/users/lookup",
    requireAuth,
    requireTournamentPermission("tournament.view"),
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
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const members = await listTournamentMembers(pathParam(req, "tournamentId"));
        res.status(200).json({ members });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/players/:playerId",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const playerId = Number(pathParam(req, "playerId"));
        const body = (req.body != null && typeof req.body === "object") ? req.body as Record<string, unknown> : null;
        const rawEmaId = body?.emaId;
        const emaId = rawEmaId == null ? null : validateEmaId(rawEmaId);
        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        if (emaId != null && !(await playerExists(emaId))) {
          throw badRequest("EMA player does not exist");
        }

        await assignTournamentPlayer({
          tournamentId: pathParam(req, "tournamentId"),
          playerId,
          emaId,
          uid: (res.locals.auth as { uid: string }).uid,
        });
        await recordActivity({
          action: "player.assign", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `player ${playerId}`,
          details: { emaId },
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/players/:playerId/substitution",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const playerId = Number(pathParam(req, "playerId"));
        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        const substitution = readSubstitution(req.body);
        if (substitution == null) throw badRequest("A substitution needs a reason and a round");
        await setPlayerSubstitution({
          tournamentId: pathParam(req, "tournamentId"),
          playerId,
          substitution,
          uid: (res.locals.auth as { uid: string }).uid,
        });
        await recordActivity({
          action: "player.substitute", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `player ${playerId}`, details: { substitution: { ...substitution } },
        });
        res.status(200).json({ ok: true });
      } catch (e) {
        next(e);
      }
    },
  );

  router.delete(
    "/:tournamentId/players/:playerId/substitution",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const playerId = Number(pathParam(req, "playerId"));
        if (!Number.isInteger(playerId) || playerId <= 0) throw badRequest("playerId must be a positive integer");
        await setPlayerSubstitution({
          tournamentId: pathParam(req, "tournamentId"),
          playerId,
          substitution: null,
          uid: (res.locals.auth as { uid: string }).uid,
        });
        await recordActivity({
          action: "player.clearSubstitution", actor: actorFromToken(res.locals.auth),
          tournamentId: pathParam(req, "tournamentId"), target: `player ${playerId}`,
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
    requireTournamentPermission("tournament.members"),
    async (req, res, next) => {
      try {
        await upsertTournamentMember({
          tournamentId: pathParam(req, "tournamentId"),
          uid: pathParam(req, "uid"),
        });
        await recordActivity({
          action: "member.add", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `user ${pathParam(req, "uid")}`,
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
    requireTournamentPermission("tournament.members"),
    async (req, res, next) => {
      try {
        const result = await removeTournamentMember({
          tournamentId: pathParam(req, "tournamentId"),
          uid: pathParam(req, "uid"),
        });
        await recordActivity({
          action: "member.remove", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `user ${pathParam(req, "uid")}`,
        });
        res.status(200).json({ ok: true, result });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/players",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        // Editors get the names, so the tables do not need the whole EMA registry.
        const players = await listTournamentPlayers(pathParam(req, "tournamentId"), { withNames: true });
        // Emails go to the accounts that manage the tournament. The others see whether a slot has an account.
        const withEmails = await canInTournament(accessOf(res), "tournament.playerEmails", pathParam(req, "tournamentId"));
        res.status(200).json({ players: players.map((player) => (withEmails ? player : { ...player, email: null })) });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/teams",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const teams = await listTournamentTeams(pathParam(req, "tournamentId"));
        res.status(200).json({ teams });
      } catch (e) {
        next(e);
      }
    },
  );

  router.put(
    "/:tournamentId/teams/:teamId",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const teamId = Number(pathParam(req, "teamId"));
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
          tournamentId: pathParam(req, "tournamentId"),
          teamId,
          name,
          emaIds,
        });
        await recordActivity({
          action: "team.update", actor: actorFromToken(res.locals.auth), tournamentId: pathParam(req, "tournamentId"),
          target: `team ${teamId}`, details: { name, emaIds },
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
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const rounds = await listTournamentRounds(pathParam(req, "tournamentId"));
        res.status(200).json({ rounds });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/round-clocks",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        res.status(200).json(await getRoundClocks(pathParam(req, "tournamentId")));
      } catch (e) {
        next(e);
      }
    },
  );

  // Play, Pause, Resume, Reset and "Set time" of the Timer. The server keeps the clock of each round.
  router.post(
    "/:tournamentId/rounds/:roundId/clock",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        if (!Number.isInteger(roundId) || roundId <= 0) throw badRequest("roundId must be a positive integer");
        const answer = await updateRoundClock({
          tournamentId: pathParam(req, "tournamentId"),
          roundId,
          action: parseRoundClockAction(req.body),
          force: req.body?.force === true,
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json(answer);
      } catch (e) {
        next(e);
      }
    },
  );

  // The plan B of the Timer: an admin or an editor of the tournament opens or closes the hand entry and the seats by hand, and opens closed tables.
  router.post(
    "/:tournamentId/rounds/:roundId/control",
    requireAuth,
    requireTournamentPermission("tournament.roundControl"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        if (!Number.isInteger(roundId) || roundId <= 0) throw badRequest("roundId must be a positive integer");
        const answer = await updateRoundControl({
          tournamentId: pathParam(req, "tournamentId"),
          roundId,
          change: parseRoundControlChange(req.body),
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json(answer);
      } catch (e) {
        next(e);
      }
    },
  );

  // The round control closes in one go the tables that stayed open in the finished rounds.
  router.post(
    "/:tournamentId/complete-open-tables",
    requireAuth,
    requireTournamentPermission("tournament.roundControl"),
    async (req, res, next) => {
      try {
        const answer = await completeTables({
          tournamentId: pathParam(req, "tournamentId"),
          tables: parseTablesToComplete(req.body),
          actor: actorFromToken(res.locals.auth),
        });
        res.status(200).json(answer);
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const roundIdParam = req.query.roundId;
        const roundId = roundIdParam == null ? null : Number(roundIdParam);
        if (roundId != null && (!Number.isFinite(roundId) || !Number.isInteger(roundId) || roundId <= 0)) {
          throw badRequest("roundId must be a positive integer");
        }

        const tables = await listTournamentTables(pathParam(req, "tournamentId"), roundId);
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables-with-hands",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const tables = await getAllTablesWithHands({ tournamentId: pathParam(req, "tournamentId") });
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/rounds/:roundId/tables-with-hands",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        if (!Number.isInteger(roundId) || roundId <= 0) {
          throw badRequest("roundId must be a positive integer");
        }

        const tables = await getRoundTablesWithHands({ tournamentId: pathParam(req, "tournamentId"), roundId });
        res.status(200).json({ tables });
      } catch (e) {
        next(e);
      }
    },
  );

  router.get(
    "/:tournamentId/tables/:roundId/:tableId",
    requireAuth,
    requireTournamentPermission("tournament.view"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        const tableId = Number(pathParam(req, "tableId"));
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        const data = await getTableWithHands({
          tournamentId: pathParam(req, "tournamentId"),
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
    "/:tournamentId/tables/:roundId/:tableId/state",
    requireAuth,
    requireTournamentPermission("tournament.edit"),
    async (req, res, next) => {
      try {
        const request = parseSaveTableRequest(req.params, req.body);
        await assertTournamentOpenForEditor(pathParam(req, "tournamentId"), accessOf(res));
        res.status(200).json(await saveTableState({
          tournamentId: pathParam(req, "tournamentId"),
          ...request,
          actor: actorFromToken(res.locals.auth),
        }));
      } catch (error) {
        next(error);
      }
    },
  );

  router.post(
    "/:tournamentId/tables/:roundId/:tableId/reset",
    requireAuth,
    requireTournamentPermission("tournament.resetTable"),
    async (req, res, next) => {
      try {
        const roundId = Number(pathParam(req, "roundId"));
        const tableId = Number(pathParam(req, "tableId"));
        if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
          throw badRequest("roundId and tableId must be positive integers");
        }

        const tournamentId = pathParam(req, "tournamentId");
        await resetTable({
          tournamentId,
          roundId,
          tableId,
          actor: actorFromToken(res.locals.auth),
        });
        // The admin app shows the empty table at once, so the answer carries it like a save does.
        res.status(200).json(await getTableWithHands({ tournamentId, roundId, tableId }));
      } catch (e) {
        next(e);
      }
    },
  );

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
      if (body.apply === true) await registrationActivity(res, tournamentId, `${result.imported} registrations`, { imported: result.imported });
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
      });
      await registrationActivity(res, tournamentId, `registration ${result.emaId}`, {
        status: body.status, paid: body.paid, teamName: body.teamName, emailChanged: body.email !== undefined,
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
