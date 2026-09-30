"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tournamentsRouter = tournamentsRouter;
const express_1 = require("express");
const firebase_1 = require("../../firebase");
const requireAuth_1 = require("../middleware/requireAuth");
const requireAdmin_1 = require("../middleware/requireAdmin");
const requireTournamentEditor_1 = require("../middleware/requireTournamentEditor");
const httpError_1 = require("../httpError");
const membersService_1 = require("../../services/membersService");
const tournamentsService_1 = require("../../services/tournamentsService");
const idCardsService_1 = require("../../services/idCardsService");
const idListService_1 = require("../../services/idListService");
const emaReportService_1 = require("../../services/emaReportService");
const tournamentContentService_1 = require("../../services/tournamentContentService");
const playersService_1 = require("../../services/playersService");
const tableManagerService_1 = require("../../services/tableManagerService");
const usersService_1 = require("../../services/usersService");
const tournamentDates_1 = require("../../services/tournamentDates");
function tournamentsRouter() {
    const router = (0, express_1.Router)();
    router.get("/", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const tournaments = decoded.admin === true
                ? await (0, tournamentsService_1.listAllTournaments)()
                : await (0, tournamentsService_1.listTournamentsForUser)(decoded.uid);
            res.status(200).json({ tournaments });
        }
        catch (e) {
            next(e);
        }
    });
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
            const numPlayersValue = body?.numPlayers;
            const numRoundsValue = body?.numRounds;
            const numTriesValue = body?.numTries;
            const numPlayers = Number(numPlayersValue ?? NaN);
            const numRounds = Number(numRoundsValue ?? NaN);
            const numTries = Number(numTriesValue ?? NaN);
            const players = Array.isArray(body?.players) ? body?.players : null;
            const tables = Array.isArray(body?.tables) ? body?.tables : null;
            const issues = [];
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
            if (!Number.isFinite(numPlayers) || !Number.isInteger(numPlayers) || numPlayers <= 0 || numPlayers % 4 !== 0) {
                issues.push({ field: "numPlayers", message: "Must be a positive integer multiple of 4", value: numPlayersValue });
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
                isTeams,
                numPlayers,
                numRounds,
                numTries,
                players: parsedPlayers,
                tables: parsedTables,
                createdByUid: decoded.uid,
            });
            res.status(200).json(tournament);
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/id-card-proof", requireAuth_1.requireAuth, async (req, res, next) => {
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
            });
            res.setHeader("Content-Type", "application/pdf");
            res.setHeader("Cache-Control", "private, max-age=3600");
            res.status(200).send(pdf);
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/id-cards", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const pdf = await (0, idCardsService_1.generateTournamentIdCards)(req.params.tournamentId);
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
                associationLogoContentType,
                associationLogoDataBase64,
                associationLogoSourceTournamentId,
                removeAssociationLogo: body.removeAssociationLogo === true,
            });
            res.status(200).json(tournament);
        }
        catch (e) {
            next(e);
        }
    });
    router.delete("/:tournamentId", requireAuth_1.requireAuth, requireAdmin_1.requireAdmin, async (req, res, next) => {
        try {
            await (0, tournamentsService_1.deleteTournament)(req.params.tournamentId);
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
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/players", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const players = await (0, tournamentContentService_1.listTournamentPlayers)(req.params.tournamentId);
            res.status(200).json({ players });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/teams", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
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
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/rounds", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const rounds = await (0, tournamentContentService_1.listTournamentRounds)(req.params.tournamentId);
            res.status(200).json({ rounds });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/tables", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
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
    router.get("/:tournamentId/tables/:roundId/:tableId", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
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
    router.put("/:tournamentId/tables/:roundId/:tableId", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const roundId = Number(req.params.roundId);
            const tableId = Number(req.params.tableId);
            if (!Number.isInteger(roundId) || roundId <= 0 || !Number.isInteger(tableId) || tableId <= 0) {
                throw (0, httpError_1.badRequest)("roundId and tableId must be positive integers");
            }
            const patch = req.body ?? {};
            await (0, tableManagerService_1.updateTable)({
                tournamentId: req.params.tournamentId,
                roundId,
                tableId,
                patch,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/tables/:roundId/:tableId/hands/:handId", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
        try {
            const roundId = Number(req.params.roundId);
            const tableId = Number(req.params.tableId);
            const handId = Number(req.params.handId);
            if (!Number.isInteger(roundId) || roundId <= 0
                || !Number.isInteger(tableId) || tableId <= 0
                || !Number.isInteger(handId) || handId <= 0) {
                throw (0, httpError_1.badRequest)("roundId, tableId and handId must be positive integers");
            }
            const patch = req.body ?? {};
            await (0, tableManagerService_1.updateHand)({
                tournamentId: req.params.tournamentId,
                roundId,
                tableId,
                handId,
                patch,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/:tournamentId/tables/:roundId/:tableId/reset", requireAuth_1.requireAuth, requireTournamentEditor_1.requireTournamentEditor, async (req, res, next) => {
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