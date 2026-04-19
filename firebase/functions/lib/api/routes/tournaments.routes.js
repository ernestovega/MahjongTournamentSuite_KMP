"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tournamentsRouter = tournamentsRouter;
const express_1 = require("express");
const requireAuth_1 = require("../middleware/requireAuth");
const requireSuperadmin_1 = require("../middleware/requireSuperadmin");
const requireTournamentRole_1 = require("../middleware/requireTournamentRole");
const httpError_1 = require("../httpError");
const role_1 = require("../../models/role");
const membersService_1 = require("../../services/membersService");
const tournamentsService_1 = require("../../services/tournamentsService");
const tournamentContentService_1 = require("../../services/tournamentContentService");
const tableManagerService_1 = require("../../services/tableManagerService");
function tournamentsRouter() {
    const router = (0, express_1.Router)();
    router.get("/", requireAuth_1.requireAuth, async (_req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const tournaments = decoded.superadmin === true
                ? await (0, tournamentsService_1.listAllTournaments)()
                : await (0, tournamentsService_1.listTournamentsForUser)(decoded.uid);
            res.status(200).json({ tournaments });
        }
        catch (e) {
            next(e);
        }
    });
    router.post("/", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (req, res, next) => {
        try {
            const decoded = res.locals.auth;
            const body = (req.body != null && typeof req.body === "object") ? req.body : null;
            const name = String(body?.name ?? "").trim();
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
                    received: body,
                    inferred: { name, isTeams, numPlayers, numRounds, numTries, playersCount: players?.length, tablesCount: tables?.length },
                });
            }
            const parsedPlayers = (players ?? []).map((p, idx) => {
                const row = (p != null && typeof p === "object") ? p : null;
                const id = Number(row?.id ?? NaN);
                const team = Number(row?.team ?? NaN);
                const playerName = row?.name == null ? undefined : String(row?.name);
                if (!Number.isFinite(id) || !Number.isInteger(id))
                    throw (0, httpError_1.badRequest)("Invalid player id", { idx, value: row?.id });
                if (!Number.isFinite(team) || !Number.isInteger(team))
                    throw (0, httpError_1.badRequest)("Invalid player team", { idx, value: row?.team });
                return { id, team, name: playerName };
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
    router.delete("/:tournamentId", requireAuth_1.requireAuth, requireSuperadmin_1.requireSuperadmin, async (req, res, next) => {
        try {
            await (0, tournamentsService_1.deleteTournament)(req.params.tournamentId);
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/members", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("ADMIN"), async (req, res, next) => {
        try {
            const members = await (0, membersService_1.listTournamentMembers)(req.params.tournamentId);
            res.status(200).json({ members });
        }
        catch (e) {
            next(e);
        }
    });
    router.put("/:tournamentId/members/:uid", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("ADMIN"), async (req, res, next) => {
        try {
            const role = (0, role_1.parseRole)(req.body?.role);
            if (!role) {
                throw (0, httpError_1.badRequest)("Invalid role");
            }
            await (0, membersService_1.upsertTournamentMember)({
                tournamentId: req.params.tournamentId,
                uid: req.params.uid,
                role,
            });
            res.status(200).json({ ok: true });
        }
        catch (e) {
            next(e);
        }
    });
    router.delete("/:tournamentId/members/:uid", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("ADMIN"), async (req, res, next) => {
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
    router.get("/:tournamentId/players", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("READER"), async (req, res, next) => {
        try {
            const players = await (0, tournamentContentService_1.listTournamentPlayers)(req.params.tournamentId);
            res.status(200).json({ players });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/rounds", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("READER"), async (req, res, next) => {
        try {
            const rounds = await (0, tournamentContentService_1.listTournamentRounds)(req.params.tournamentId);
            res.status(200).json({ rounds });
        }
        catch (e) {
            next(e);
        }
    });
    router.get("/:tournamentId/tables", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("READER"), async (req, res, next) => {
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
    router.get("/:tournamentId/tables/:roundId/:tableId", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("READER"), async (req, res, next) => {
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
    router.put("/:tournamentId/tables/:roundId/:tableId", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("EDITOR"), async (req, res, next) => {
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
    router.put("/:tournamentId/tables/:roundId/:tableId/hands/:handId", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("EDITOR"), async (req, res, next) => {
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
    // Placeholder: future endpoints for players/teams/tables/hands will live here.
    router.get("/:tournamentId", requireAuth_1.requireAuth, (0, requireTournamentRole_1.requireTournamentRole)("READER"), async (req, res) => {
        res.status(501).json({
            error: "not_implemented",
            message: "Tournament details endpoint not implemented yet",
            tournamentId: req.params.tournamentId,
        });
    });
    return router;
}
//# sourceMappingURL=tournaments.routes.js.map