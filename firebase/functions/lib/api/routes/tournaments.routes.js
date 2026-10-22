"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tournamentsRouter = tournamentsRouter;
const express_1 = require("express");
const activityLog_1 = require("../../services/activityLog");
const firebase_1 = require("../../firebase");
const requireAuth_1 = require("../middleware/requireAuth");
const requireEditor_1 = require("../middleware/requireEditor");
const requireAdmin_1 = require("../middleware/requireAdmin");
const requireTournamentEditor_1 = require("../middleware/requireTournamentEditor");
const requireTournamentReader_1 = require("../middleware/requireTournamentReader");
const playerAccountsService_1 = require("../../services/playerAccountsService");
const httpError_1 = require("../httpError");
const membersService_1 = require("../../services/membersService");
const tournamentsService_1 = require("../../services/tournamentsService");
const idCardsService_1 = require("../../services/idCardsService");
const idListService_1 = require("../../services/idListService");
const emaReportService_1 = require("../../services/emaReportService");
const playerSubstitution_1 = require("../../services/playerSubstitution");
const tournamentContentService_1 = require("../../services/tournamentContentService");
const playersService_1 = require("../../services/playersService");
const tableManagerService_1 = require("../../services/tableManagerService");
const changeLog_1 = require("../../services/changeLog");
const usersService_1 = require("../../services/usersService");
const tournamentContentRules_1 = require("../../services/tournamentContentRules");
const tournamentDates_1 = require("../../services/tournamentDates");
const tournamentVenue_1 = require("../../services/tournamentVenue");
const dataVersionsService_1 = require("../../services/dataVersionsService");
const tableRequests_1 = require("../tableRequests");
const statisticsStore_1 = require("../../services/statisticsStore");
const tournamentIntegrityService_1 = require("../../services/tournamentIntegrityService");
/** Reads the optional A4 sheet layout of the ID cards. No `layout=sheet` value means one card per page. */
function readIdCardSheetOptions(query) {
    if (query.layout !== "sheet")
        return undefined;
    const cardsPerSheet = Number(query.cardsPerSheet ?? 10);
    const scalePercent = Number(query.scalePercent ?? 100);
    const landscape = query.landscape === "true";
    if (!Number.isInteger(cardsPerSheet) || cardsPerSheet < 1 || cardsPerSheet > 40) {
        throw (0, httpError_1.badRequest)("Invalid number of cards per sheet", { value: query.cardsPerSheet });
    }
    if (!Number.isFinite(scalePercent) || scalePercent < 25 || scalePercent > 400) {
        throw (0, httpError_1.badRequest)("Invalid card size", { value: query.scalePercent });
    }
    const options = {
        cardsPerSheet,
        scalePercent,
        landscape,
        doubleSided: query.doubleSided !== "false",
        cutMarks: query.cutMarks !== "false",
    };
    if ((0, idCardsService_1.idCardSheetGrid)(options) == null)
        throw (0, httpError_1.badRequest)("The card size does not fit on an A4 sheet", { scalePercent });
    return options;
}
function tournamentsRouter() {
    const router = (0, express_1.Router)();
    router.get("/:tournamentId/sync/manifest", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            res.setHeader("Cache-Control", "no-store");
            res.status(200).json(await (0, dataVersionsService_1.getTournamentManifest)(req.params.tournamentId));
        }
        catch (error) {
            next(error);
        }
    });
    // Admins see every tournament. Editors see assigned ones. Players see the ones where they are registered.
    // The player app sends scope=player. It shows only the tournaments where the account is a player, whatever its role.
    router.get("/", requireAuth_1.requireAuth, async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            let tournaments;
            if (req.query.scope === "player") {
                tournaments = await (0, tournamentsService_1.listTournamentsForPlayer)(decoded.uid);
            }
            else if (decoded.admin === true) {
                tournaments = await (0, tournamentsService_1.listAllTournaments)();
            }
            else {
                const lists = await Promise.all([
                    decoded.editor === true ? (0, tournamentsService_1.listTournamentsForUser)(decoded.uid) : Promise.resolve([]),
                    decoded.player === true ? (0, tournamentsService_1.listTournamentsForPlayer)(decoded.uid) : Promise.resolve([]),
                ]);
                const byId = new Map(lists.flat().map((tournament) => [tournament.id, tournament]));
                tournaments = [...byId.values()];
            }
            res.status(200).json({ tournaments });
        }
        catch (e) {
            next(e);
        }
    });
    /**
     * The statistics of all tournaments together: sums and the leaderboards.
     * The server keeps them up to date when a table changes. This request reads a fixed number of documents.
     */
    router.get("/statistics/overview", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (_req, res, next) => {
        try {
            res.status(200).json(await (0, statisticsStore_1.loadOverview)());
        }
        catch (e) {
            next(e);
        }
    });
    /**
     * The ids of the tournaments that the signed-in account can see, or null for an administrator.
     * It is the same rule as the tournament list above.
     */
    const visibleTournamentIds = async (decoded) => {
        if (decoded.admin === true)
            return null;
        const lists = await Promise.all([
            decoded.editor === true ? (0, tournamentsService_1.listTournamentsForUser)(decoded.uid) : Promise.resolve([]),
            decoded.player === true ? (0, tournamentsService_1.listTournamentsForPlayer)(decoded.uid) : Promise.resolve([]),
        ]);
        return new Set(lists.flat().map((tournament) => tournament.id));
    };
    const readEmaParam = (value) => {
        const emaId = String(value ?? "").trim();
        if (emaId === "" || emaId.length > 64)
            throw (0, httpError_1.badRequest)("Invalid EMA id");
        return emaId;
    };
    /** The EMA number of the signed-in account. It is empty when the account is not linked to a player. */
    const ownEmaId = async (uid) => (await (0, usersService_1.getUserProfile)(uid)).emaId ?? "";
    const readTournamentIds = (value) => {
        const ids = String(value ?? "").split(",").map((id) => id.trim()).filter((id) => id !== "");
        if (ids.length > statisticsStore_1.MAX_STANDINGS_PER_REQUEST)
            throw (0, httpError_1.badRequest)("Too many tournaments", { max: statisticsStore_1.MAX_STANDINGS_PER_REQUEST });
        return ids;
    };
    /**
     * The results of one player, in the tournaments that the account can see. The own route reads the player of the account.
     * The route with an EMA number is open to every signed-in account, so a player can look at the statistics of others.
     */
    const personHandler = (own) => async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(req.params.emaId);
            if (emaId === "") {
                res.status(200).json({ emaId, tournaments: [] });
                return;
            }
            res.status(200).json(await (0, statisticsStore_1.loadPersonStatistics)(emaId, await visibleTournamentIds(decoded)));
        }
        catch (e) {
            next(e);
        }
    };
    /** The final places of one player in a few tournaments. The app asks for the tournaments on screen. */
    const standingsHandler = (own) => async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const emaId = own ? await ownEmaId(decoded.uid) : readEmaParam(req.params.emaId);
            const allowed = await visibleTournamentIds(decoded);
            const ids = readTournamentIds(req.query.tournaments).filter((id) => allowed == null || allowed.has(id));
            res.status(200).json({ standings: emaId === "" ? [] : await (0, statisticsStore_1.loadStandings)(emaId, ids) });
        }
        catch (e) {
            next(e);
        }
    };
    router.get("/statistics/me", requireAuth_1.requireAuth, personHandler(true));
    router.get("/statistics/me/standings", requireAuth_1.requireAuth, standingsHandler(true));
    router.get("/statistics/persons/:emaId", requireAuth_1.requireAuth, personHandler(false));
    router.get("/statistics/persons/:emaId/standings", requireAuth_1.requireAuth, standingsHandler(false));
    router.post("/", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const body = (req.body != null && typeof req.body === "object") ? req.body : null;
            const name = String(body?.name ?? "").trim();
            const shortName = String(body?.shortName ?? name.slice(0, 10)).trim();
            const primaryColor = String(body?.primaryColor ?? "#02B16B").trim().toUpperCase();
            const associationLogoContentType = body?.associationLogoContentType == null
                ? null
                : String(body.associationLogoContentType).trim();
            const associationLogoDataBase64 = body?.associationLogoDataBase64 == null
                ? null
                : String(body.associationLogoDataBase64).trim();
            const associationLogoSourceTournamentId = body?.associationLogoSourceTournamentId == null
                ? null
                : String(body.associationLogoSourceTournamentId).trim();
            const eventStartDate = String(body?.eventStartDate ?? body?.eventDate ?? "").trim();
            const eventEndDate = String(body?.eventEndDate ?? body?.eventDate ?? "").trim();
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
            const players = Array.isArray(body?.players) ? body?.players : null;
            const tables = Array.isArray(body?.tables) ? body?.tables : null;
            const issues = [];
            let venue = (0, tournamentVenue_1.normalizeVenue)(null);
            try {
                venue = (0, tournamentVenue_1.normalizeVenue)(body);
            }
            catch (error) {
                issues.push({ field: "venue", message: error instanceof Error ? error.message : "Invalid venue" });
            }
            if (!body)
                issues.push({ field: "body", message: "Request body must be JSON", value: req.body });
            if (!name)
                issues.push({ field: "name", message: "Required", value: body?.name });
            if (!/^[A-Z]{2,3}$/.test(hostCountry)) {
                issues.push({ field: "hostCountry", message: "Must use a two-letter country code or legacy three-letter EMA code", value: body?.hostCountry });
            }
            if (!hostCity)
                issues.push({ field: "hostCity", message: "Required", value: body?.hostCity });
            if (!shortName || shortName.length > 10) {
                issues.push({ field: "shortName", message: "Required, with at most 10 characters", value: body?.shortName });
            }
            if (!/^#[0-9A-F]{6}$/.test(primaryColor)) {
                issues.push({ field: "primaryColor", message: "Must use #RRGGBB format", value: body?.primaryColor });
            }
            if ((associationLogoContentType == null) !== (associationLogoDataBase64 == null)) {
                issues.push({ field: "associationLogo", message: "Content type and image data must be supplied together" });
            }
            if (associationLogoSourceTournamentId && associationLogoDataBase64) {
                issues.push({ field: "associationLogo", message: "Supply an uploaded logo or a reusable logo, not both" });
            }
            if (!(0, tournamentDates_1.isValidIsoDate)(eventStartDate)) {
                issues.push({ field: "eventStartDate", message: "Must use yyyy-MM-dd", value: body?.eventStartDate });
            }
            if (!(0, tournamentDates_1.isValidIsoDate)(eventEndDate)) {
                issues.push({ field: "eventEndDate", message: "Must use yyyy-MM-dd", value: body?.eventEndDate });
            }
            if ((0, tournamentDates_1.isValidIsoDate)(eventStartDate) && (0, tournamentDates_1.isValidIsoDate)(eventEndDate) && !(0, tournamentDates_1.isValidIsoDateRange)(eventStartDate, eventEndDate)) {
                issues.push({ field: "eventEndDate", message: "Must not be before eventStartDate", value: body?.eventEndDate });
            }
            if (!(0, tournamentContentRules_1.isValidPlayerCount)(numPlayers)) {
                issues.push({ field: "numPlayers", message: `Must be a multiple of 4 and at least ${tournamentContentRules_1.MIN_TOURNAMENT_PLAYERS}`, value: numPlayersValue });
            }
            if (!Number.isFinite(numRounds) || !Number.isInteger(numRounds) || numRounds <= 0) {
                issues.push({ field: "numRounds", message: "Must be a positive integer", value: numRoundsValue });
            }
            if (!Number.isFinite(numTries) || !Number.isInteger(numTries) || numTries <= 0) {
                issues.push({ field: "numTries", message: "Must be a positive integer", value: numTriesValue });
            }
            if (!players)
                issues.push({ field: "players", message: "Required (array)", value: body?.players });
            if (!tables)
                issues.push({ field: "tables", message: "Required (array)", value: body?.tables });
            if (issues.length > 0) {
                throw (0, httpError_1.badRequest)("Missing or invalid tournament fields", {
                    issues,
                    received: body == null ? null : {
                        ...body,
                        associationLogoDataBase64: associationLogoDataBase64 == null ? null : "[image data]",
                    },
                    inferred: { name, isTeams, numPlayers, numRounds, numTries, playersCount: players?.length, tablesCount: tables?.length },
                });
            }
            const parsedPlayers = (players ?? []).map((p, idx) => {
                const row = (p != null && typeof p === "object") ? p : null;
                const id = Number(row?.id ?? NaN);
                const team = Number(row?.team ?? NaN);
                const playerName = row?.name == null ? undefined : String(row?.name);
                const country = row?.country == null ? undefined : String(row?.country);
                if (!Number.isFinite(id) || !Number.isInteger(id))
                    throw (0, httpError_1.badRequest)("Invalid player id", { idx, value: row?.id });
                if (!Number.isFinite(team) || !Number.isInteger(team))
                    throw (0, httpError_1.badRequest)("Invalid player team", { idx, value: row?.team });
                return { id, team, name: playerName, country };
            });
            const parsedTables = (tables ?? []).map((t, idx) => {
                const row = (t != null && typeof t === "object") ? t : null;
                const roundId = Number(row?.roundId ?? NaN);
                const tableId = Number(row?.tableId ?? NaN);
                const playerIdsRaw = Array.isArray(row?.playerIds) ? row?.playerIds : null;
                const isCompletedRaw = row?.isCompleted;
                const useTotalsOnlyRaw = row?.useTotalsOnly;
                if (!Number.isFinite(roundId) || !Number.isInteger(roundId))
                    throw (0, httpError_1.badRequest)("Invalid table roundId", { idx, value: row?.roundId });
                if (!Number.isFinite(tableId) || !Number.isInteger(tableId))
                    throw (0, httpError_1.badRequest)("Invalid table tableId", { idx, value: row?.tableId });
                if (!playerIdsRaw || playerIdsRaw.length !== 4)
                    throw (0, httpError_1.badRequest)("Invalid table playerIds", { idx, value: row?.playerIds });
                if (isCompletedRaw != null && typeof isCompletedRaw !== "boolean")
                    throw (0, httpError_1.badRequest)("Invalid table isCompleted", { idx, value: isCompletedRaw });
                if (useTotalsOnlyRaw != null && typeof useTotalsOnlyRaw !== "boolean")
                    throw (0, httpError_1.badRequest)("Invalid table useTotalsOnly", { idx, value: useTotalsOnlyRaw });
                const playerIds = playerIdsRaw.map((x) => Number(x ?? NaN));
                for (const pid of playerIds) {
                    if (!Number.isFinite(pid) || !Number.isInteger(pid)) {
                        throw (0, httpError_1.badRequest)("Invalid table playerIds entry", { idx, value: row?.playerIds });
                    }
                }
                return {
                    roundId,
                    tableId,
                    playerIds,
                    isCompleted: isCompletedRaw,
                    useTotalsOnly: useTotalsOnlyRaw,
                };
            });
            const tournament = await (0, tournamentsService_1.createTournament)({
                name,
                shortName,
                primaryColor,
                associationLogoContentType,
                associationLogoDataBase64,
                associationLogoSourceTournamentId,
                eventStartDate,
                eventEndDate,
                hostCountry,
                hostCity,
                ...venue,
                isTeams,
                countBestHands,
                countChickenHands,
                numPlayers,
                numRounds,
                numTries,
                players: parsedPlayers,
                tables: parsedTables,
                roundSchedules: body?.roundSchedules,
                agendaItems: body?.agendaItems,
                matchDurationMinutes: body?.matchDurationMinutes,
                matchEndMode: body?.matchEndMode,
                matchExtraMinutes: body?.matchExtraMinutes,
                timeZone: body?.timeZone,
                createdByUid: decoded.uid,
            });
            await (0, activityLog_1.recordActivity)({
                action: "tournament.create", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: tournament.id,
                target: tournament.name,
                details: { shortName, eventStartDate, eventEndDate, hostCountry, hostCity, isTeams, numPlayers, numRounds },
            });
            res.status(200).json(tournament);
        }
        catch (e) {
            next(e);
        }
    });
    const generateIdCardProof = async (req, res, next) => {
        try {
            const body = (req.body != null && typeof req.body === "object")
                ? req.body
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
                throw (0, httpError_1.badRequest)("Invalid ID card preview fields");
            }
            if (associationLogoDataBase64 && associationLogoDataBase64.length > 3000000) {
                throw (0, httpError_1.badRequest)("ID card preview logo is too large");
            }
            const pdf = await (0, idCardsService_1.buildIdCardProofPdf)({
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
        }
        catch (e) {
            next(e);
        }
    };
    router.post("/id-card-proof", requireAuth_1.requireAuth, requireEditor_1.requireEditor, generateIdCardProof);
    // Keep the previous route working for already deployed web bundles.
    router.post("/id-card-preview", requireAuth_1.requireAuth, requireEditor_1.requireEditor, generateIdCardProof);
    // Reads the whole tournament and reports wrong or odd data. It changes nothing.
    router.get("/:tournamentId/integrity", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            res.setHeader("Cache-Control", "no-store");
            res.status(200).json(await (0, tournamentIntegrityService_1.runTournamentIntegrityCheck)(req.params.tournamentId));
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/id-cards", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const pdf = await (0, idCardsService_1.generateTournamentIdCards)(req.params.tournamentId, readIdCardSheetOptions(req.query));
            res.setHeader("Content-Type", "application/pdf");
            res.setHeader("Content-Disposition", `attachment; filename="tournament-${req.params.tournamentId}-id-cards.pdf"`);
            res.status(200).send(pdf);
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/id-list", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const workbook = await (0, idListService_1.generateTournamentIdList)(req.params.tournamentId);
            res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            res.setHeader("Content-Disposition", `attachment; filename="tournament-${req.params.tournamentId}-id-list.xlsx"`);
            res.status(200).send(workbook);
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/:tournamentId/ema-report", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const rawRows = Array.isArray(req.body?.rows) ? req.body.rows : null;
            if (rawRows == null || rawRows.length === 0)
                throw (0, httpError_1.badRequest)("Ranking rows are required");
            const rows = rawRows.map((value, index) => {
                if (value == null || typeof value !== "object")
                    throw (0, httpError_1.badRequest)("Invalid ranking row", { index });
                const row = value;
                const parsed = {
                    playerId: Number(row.playerId),
                    place: Number(row.place),
                    tablePoints: Number(row.tablePoints),
                    score: Number(row.score),
                };
                if (!Number.isInteger(parsed.playerId) || parsed.playerId <= 0
                    || !Number.isInteger(parsed.place) || parsed.place <= 0
                    || !Number.isFinite(parsed.tablePoints) || !Number.isFinite(parsed.score)) {
                    throw (0, httpError_1.badRequest)("Invalid ranking row", { index, value });
                }
                return parsed;
            });
            const report = await (0, emaReportService_1.generateEmaReport)({ tournamentId: req.params.tournamentId, rows });
            res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            res.setHeader("Content-Disposition", `attachment; filename="tournament-${req.params.tournamentId}-ema.xlsx"`);
            res.status(200).send(report);
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/settings", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const body = (req.body != null && typeof req.body === "object") ? req.body : null;
            if (body == null)
                throw (0, httpError_1.badRequest)("Request body must be JSON");
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
            const associationLogoSourceTournamentId = body.associationLogoSourceTournamentId == null
                ? null
                : String(body.associationLogoSourceTournamentId).trim();
            if (!name)
                throw (0, httpError_1.badRequest)("Tournament name is required");
            if (!shortName || shortName.length > 10) {
                throw (0, httpError_1.badRequest)("Tournament short name must contain 1 to 10 characters");
            }
            if (!/^#[0-9A-F]{6}$/.test(primaryColor)) {
                throw (0, httpError_1.badRequest)("Primary color must use #RRGGBB format");
            }
            if (!(0, tournamentDates_1.isValidIsoDateRange)(eventStartDate, eventEndDate)) {
                throw (0, httpError_1.badRequest)("Tournament dates must use yyyy-MM-dd, and the end date must not be before the start date");
            }
            if (!/^[A-Z]{2,3}$/.test(hostCountry)) {
                throw (0, httpError_1.badRequest)("Host country must use a two-letter country code or legacy three-letter EMA code");
            }
            if (!hostCity)
                throw (0, httpError_1.badRequest)("Host city is required");
            if ((associationLogoContentType == null) !== (associationLogoDataBase64 == null)) {
                throw (0, httpError_1.badRequest)("Association logo content type and image data must be supplied together");
            }
            if (associationLogoSourceTournamentId && associationLogoDataBase64) {
                throw (0, httpError_1.badRequest)("Supply an uploaded logo or a reusable logo, not both");
            }
            const tournament = await (0, tournamentsService_1.updateTournamentSettings)({
                tournamentId: req.params.tournamentId,
                name,
                shortName,
                primaryColor,
                eventStartDate,
                eventEndDate,
                hostCountry,
                hostCity,
                ...(0, tournamentVenue_1.normalizeVenue)(body),
                associationLogoContentType,
                associationLogoDataBase64,
                associationLogoSourceTournamentId,
                countBestHands: typeof body.countBestHands === "boolean" ? body.countBestHands : undefined,
                countChickenHands: typeof body.countChickenHands === "boolean" ? body.countChickenHands : undefined,
                removeAssociationLogo: body.removeAssociationLogo === true,
                roundSchedules: body.roundSchedules,
                agendaItems: body.agendaItems,
                matchDurationMinutes: body.matchDurationMinutes,
                matchEndMode: body.matchEndMode,
                matchExtraMinutes: body.matchExtraMinutes,
                timeZone: body.timeZone,
            });
            await (0, activityLog_1.recordActivity)({
                action: "tournament.update", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: name,
                details: {
                    shortName, primaryColor, eventStartDate, eventEndDate, hostCountry, hostCity,
                    countBestHands: body.countBestHands, countChickenHands: body.countChickenHands,
                    matchDurationMinutes: body.matchDurationMinutes, matchEndMode: body.matchEndMode,
                    matchExtraMinutes: body.matchExtraMinutes, timeZone: body.timeZone,
                    logo: body.removeAssociationLogo === true ? "removed"
                        : associationLogoDataBase64 != null || associationLogoSourceTournamentId ? "changed" : undefined,
                    roundSchedules: body.roundSchedules, agendaItems: body.agendaItems,
                },
            });
            res.status(200).json(tournament);
        }
        catch (e) {
            next(e);
        }
    });
    router.delete("/:tournamentId", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const removed = await (0, activityLog_1.tournamentNameOf)(req.params.tournamentId);
            await (0, tournamentsService_1.deleteTournament)(req.params.tournamentId);
            await (0, activityLog_1.recordActivity)({
                action: "tournament.delete", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: removed,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const body = (req.body != null && typeof req.body === "object") ? req.body : null;
            const name = String(body?.name ?? "").trim();
            if (!name)
                throw (0, httpError_1.badRequest)("Tournament name is required");
            await (0, tournamentsService_1.renameTournament)(req.params.tournamentId, name);
            await (0, activityLog_1.recordActivity)({
                action: "tournament.rename", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: name,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/users/lookup", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const email = String(req.query.email ?? "").trim();
            if (!email.includes("@"))
                throw (0, httpError_1.badRequest)("Enter a user email address");
            const profile = await firebase_1.auth.getUserByEmail(email).then((user) => (0, usersService_1.getUserProfile)(user.uid));
            res.status(200).json(profile);
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/members", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const members = await (0, membersService_1.listTournamentMembers)(req.params.tournamentId);
            res.status(200).json({ members });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/players/:playerId", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const playerId = Number(req.params.playerId);
            const body = (req.body != null && typeof req.body === "object") ? req.body : null;
            const rawEmaId = body?.emaId;
            const emaId = rawEmaId == null ? null : (0, playersService_1.validateEmaId)(rawEmaId);
            const rawNonMember = body?.nonMember;
            let nonMember = null;
            if (rawNonMember != null) {
                if (typeof rawNonMember !== "object")
                    throw (0, httpError_1.badRequest)("nonMember must be an object");
                const value = rawNonMember;
                nonMember = {
                    firstName: String(value.firstName ?? "").trim(),
                    lastName: String(value.lastName ?? "").trim(),
                    country: String(value.country ?? "").trim().toUpperCase(),
                };
                if (!nonMember.firstName || !nonMember.lastName || !/^[A-Z]{3}$/.test(nonMember.country)) {
                    throw (0, httpError_1.badRequest)("A non-member needs first name, last name, and a three-letter country code");
                }
            }
            if (!Number.isInteger(playerId) || playerId <= 0)
                throw (0, httpError_1.badRequest)("playerId must be a positive integer");
            if (emaId != null && nonMember != null)
                throw (0, httpError_1.badRequest)("Choose an EMA player or a non-member, not both");
            if (emaId != null && !(await (0, playersService_1.playerExists)(emaId))) {
                throw (0, httpError_1.badRequest)("EMA player does not exist");
            }
            await (0, tournamentContentService_1.assignTournamentPlayer)({
                tournamentId: req.params.tournamentId,
                playerId,
                emaId,
                nonMember,
                uid: res.locals.auth.uid,
            });
            await (0, activityLog_1.recordActivity)({
                action: "player.assign", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: `player ${playerId}`,
                details: { emaId, nonMember: nonMember == null ? undefined : { ...nonMember } },
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/players/:playerId/substitution", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const playerId = Number(req.params.playerId);
            if (!Number.isInteger(playerId) || playerId <= 0)
                throw (0, httpError_1.badRequest)("playerId must be a positive integer");
            const substitution = (0, playerSubstitution_1.readSubstitution)(req.body);
            if (substitution == null)
                throw (0, httpError_1.badRequest)("A substitution needs a reason and a round");
            await (0, tournamentContentService_1.setPlayerSubstitution)({
                tournamentId: req.params.tournamentId,
                playerId,
                substitution,
                uid: res.locals.auth.uid,
            });
            await (0, activityLog_1.recordActivity)({
                action: "player.substitute", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: `player ${playerId}`, details: { substitution: { ...substitution } },
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.delete("/:tournamentId/players/:playerId/substitution", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const playerId = Number(req.params.playerId);
            if (!Number.isInteger(playerId) || playerId <= 0)
                throw (0, httpError_1.badRequest)("playerId must be a positive integer");
            await (0, tournamentContentService_1.setPlayerSubstitution)({
                tournamentId: req.params.tournamentId,
                playerId,
                substitution: null,
                uid: res.locals.auth.uid,
            });
            await (0, activityLog_1.recordActivity)({
                action: "player.clearSubstitution", actor: (0, changeLog_1.actorFromToken)(res.locals.auth),
                tournamentId: req.params.tournamentId, target: `player ${playerId}`,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/members/:uid", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            await (0, membersService_1.upsertTournamentMember)({
                tournamentId: req.params.tournamentId,
                uid: req.params.uid,
            });
            await (0, activityLog_1.recordActivity)({
                action: "member.add", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: `user ${req.params.uid}`,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.delete("/:tournamentId/members/:uid", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            await (0, membersService_1.removeTournamentMember)({
                tournamentId: req.params.tournamentId,
                uid: req.params.uid,
            });
            await (0, activityLog_1.recordActivity)({
                action: "member.remove", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: `user ${req.params.uid}`,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/players", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const players = await (0, tournamentContentService_1.listTournamentPlayers)(req.params.tournamentId);
            if (res.locals.reader === "PLAYER") {
                // Players see who plays and where, never the accounts or emails of other players.
                res.status(200).json({
                    players: players.map((player) => ({
                        ...player, accountUid: null, invitationStatus: "NONE", invitedEmail: null,
                    })),
                });
                return;
            }
            res.status(200).json({ players });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/teams", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const teams = await (0, tournamentContentService_1.listTournamentTeams)(req.params.tournamentId);
            res.status(200).json({ teams });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/teams/:teamId", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const teamId = Number(req.params.teamId);
            const body = (req.body != null && typeof req.body === "object")
                ? req.body
                : null;
            const name = String(body?.name ?? "").trim();
            const rawEmaIds = Array.isArray(body?.emaIds) ? body.emaIds : null;
            if (!Number.isInteger(teamId) || teamId <= 0) {
                throw (0, httpError_1.badRequest)("teamId must be a positive integer");
            }
            if (name.length === 0 || name.length > 80) {
                throw (0, httpError_1.badRequest)("Team name must contain 1 to 80 characters");
            }
            if (rawEmaIds == null || rawEmaIds.length > 4) {
                throw (0, httpError_1.badRequest)("emaIds must contain up to four team slots");
            }
            const emaIds = rawEmaIds.map((value, index) => {
                if (value == null || String(value).trim().length === 0)
                    return null;
                try {
                    return (0, playersService_1.validateEmaId)(value);
                }
                catch (_error) {
                    throw (0, httpError_1.badRequest)("Invalid EMA player", { index, value });
                }
            });
            const assignedEmaIds = emaIds.filter((emaId) => emaId != null);
            if (new Set(assignedEmaIds).size !== assignedEmaIds.length) {
                throw (0, httpError_1.badRequest)("A player cannot occupy two team slots");
            }
            const existence = await Promise.all(assignedEmaIds.map((emaId) => (0, playersService_1.playerExists)(emaId)));
            const missingIndex = existence.findIndex((exists) => !exists);
            if (missingIndex >= 0) {
                throw (0, httpError_1.badRequest)("EMA player does not exist", { emaId: assignedEmaIds[missingIndex] });
            }
            await (0, tournamentContentService_1.updateTournamentTeam)({
                tournamentId: req.params.tournamentId,
                teamId,
                name,
                emaIds,
            });
            await (0, activityLog_1.recordActivity)({
                action: "team.update", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: `team ${teamId}`, details: { name, emaIds },
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/rounds", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const rounds = await (0, tournamentContentService_1.listTournamentRounds)(req.params.tournamentId);
            res.status(200).json({ rounds });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/tables", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const roundIdParam = req.query.roundId;
            const roundId = roundIdParam == null ? null : Number(roundIdParam);
            if (roundId != null && (!Number.isFinite(roundId) || !Number.isInteger(roundId) || roundId <= 0)) {
                throw (0, httpError_1.badRequest)("roundId must be a positive integer");
            }
            const tables = await (0, tournamentContentService_1.listTournamentTables)(req.params.tournamentId, roundId);
            res.status(200).json({ tables });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/tables-with-hands", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const tables = await (0, tableManagerService_1.getAllTablesWithHands)({ tournamentId: req.params.tournamentId });
            res.status(200).json({ tables });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/rounds/:roundId/tables-with-hands", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const roundId = Number(req.params.roundId);
            if (!Number.isInteger(roundId) || roundId <= 0) {
                throw (0, httpError_1.badRequest)("roundId must be a positive integer");
            }
            const tables = await (0, tableManagerService_1.getRoundTablesWithHands)({ tournamentId: req.params.tournamentId, roundId });
            res.status(200).json({ tables });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/tables/:roundId/:tableId", requireAuth_1.requireAuth, requireTournamentReader_1.requireTournamentReader, async (req, res, next) => {
        try {
            const roundId = Number(req.params.roundId);
            const tableId = Number(req.params.tableId);
            if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
                throw (0, httpError_1.badRequest)("roundId and tableId must be positive integers");
            }
            const data = await (0, tableManagerService_1.getTableWithHands)({
                tournamentId: req.params.tournamentId,
                roundId,
                tableId,
            });
            res.status(200).json(data);
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/tables/:roundId/:tableId/state", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const request = (0, tableRequests_1.parseSaveTableRequest)(req.params, req.body);
            res.status(200).json(await (0, tableManagerService_1.saveTableState)({
                tournamentId: req.params.tournamentId,
                ...request,
                actor: (0, changeLog_1.actorFromToken)(res.locals.auth),
            }));
        }
        catch (error) {
            next(error);
        }
    });
    router.post("/:tournamentId/tables/:roundId/:tableId/reset", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            const roundId = Number(req.params.roundId);
            const tableId = Number(req.params.tableId);
            if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
                throw (0, httpError_1.badRequest)("roundId and tableId must be positive integers");
            }
            await (0, tableManagerService_1.resetTable)({
                tournamentId: req.params.tournamentId,
                roundId,
                tableId,
                actor: (0, changeLog_1.actorFromToken)(res.locals.auth),
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/player-invitations", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            res.status(200).json({ invitations: await (0, playerAccountsService_1.listInvitations)(req.params.tournamentId) });
        }
        catch (e) {
            next(e);
        }
    });
    // Send, resend and revoke work for one player or for many. The body always lists the players.
    for (const action of ["send", "resend", "revoke"]) {
        router.post(`/:tournamentId/player-accounts/${action}`, requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
            try {
                const raw = Array.isArray(req.body?.players) ? req.body.players : [];
                const items = raw.map((entry) => {
                    const value = entry;
                    const playerId = Number(value.playerId);
                    if (!Number.isInteger(playerId) || playerId <= 0)
                        throw (0, httpError_1.badRequest)("playerId must be a positive integer");
                    return { playerId, email: typeof value.email === "string" ? value.email : undefined };
                });
                const actor = res.locals.auth;
                const results = await (0, playerAccountsService_1.runAccountAction)({
                    tournamentId: req.params.tournamentId, action, items, invitedBy: actor.uid,
                });
                await (0, activityLog_1.recordActivity)({
                    action: `account.${action}`, actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                    target: `${items.length} players`, details: { playerIds: items.map((item) => item.playerId) },
                });
                res.status(200).json({ results });
            }
            catch (e) {
                next(e);
            }
        });
    }
    router.put("/:tournamentId/player-accounts/emails", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const raw = Array.isArray(req.body?.players) ? req.body.players : [];
            const items = raw.map((entry) => {
                const value = entry;
                const playerId = Number(value.playerId);
                if (!Number.isInteger(playerId) || playerId <= 0)
                    throw (0, httpError_1.badRequest)("playerId must be a positive integer");
                return { playerId, email: String(value.email ?? "") };
            });
            await (0, playerAccountsService_1.saveContactEmails)({ tournamentId: req.params.tournamentId, items });
            await (0, activityLog_1.recordActivity)({
                action: "account.saveEmails", actor: (0, changeLog_1.actorFromToken)(res.locals.auth), tournamentId: req.params.tournamentId,
                target: `${items.length} players`, details: { playerIds: items.map((item) => item.playerId) },
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    // Placeholder: future endpoints for players/teams/tables/hands will live here.
    router.get("/:tournamentId", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res) => {
        res.status(501).json({
            error: "not_implemented",
            message: "Tournament details endpoint not implemented yet",
            tournamentId: req.params.tournamentId,
        });
    });
    return router;
}
//# sourceMappingURL=tournaments.routes.js.map