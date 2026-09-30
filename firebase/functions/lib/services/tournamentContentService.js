"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listTournamentPlayers = listTournamentPlayers;
exports.listTournamentTeams = listTournamentTeams;
exports.updateTournamentTeam = updateTournamentTeam;
exports.assignTournamentPlayer = assignTournamentPlayer;
exports.listTournamentRounds = listTournamentRounds;
exports.listTournamentTables = listTournamentTables;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const mers_1 = require("./mers");
const playersService_1 = require("./playersService");
const tournamentContentRules_1 = require("./tournamentContentRules");
const dataVersionsService_1 = require("./dataVersionsService");
function readNonMember(value) {
    if (value == null || typeof value !== "object")
        return null;
    const data = value;
    const firstName = String(data.firstName ?? "").trim();
    const lastName = String(data.lastName ?? "").trim();
    const country = String(data.country ?? "").trim().toUpperCase();
    return firstName && lastName && country ? { firstName, lastName, country } : null;
}
async function updateTournamentMers(tournamentId) {
    const ref = firebase_1.db.collection("tournaments").doc(tournamentId);
    const [tournament, players] = await Promise.all([ref.get(), ref.collection("players").get()]);
    if (!tournament.exists)
        return;
    await ref.update({
        mers: (0, mers_1.calculateMers)({
            startDate: String(tournament.get("eventStartDate") ?? ""),
            endDate: String(tournament.get("eventEndDate") ?? ""),
            participantCount: Number(tournament.get("numPlayers") ?? 0),
            representedCountries: players.docs.map((player) => String(player.get("assignedCountry") ?? "")),
        }),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
}
async function refreshAssignmentCountries(tournamentId) {
    const players = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("players").get();
    const emaIds = [...new Set(players.docs.map((player) => player.get("assignedEmaId"))
            .filter((value) => typeof value === "string" && value.length > 0))];
    const registry = await Promise.all(emaIds.map((emaId) => firebase_1.db.collection(playersService_1.EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()));
    const countries = new Map(registry.filter((player) => player.exists)
        .map((player) => [player.id, String(player.get("country") ?? "").trim().toUpperCase()]));
    const batch = firebase_1.db.batch();
    players.docs.forEach((player) => {
        const emaId = typeof player.get("assignedEmaId") === "string" ? String(player.get("assignedEmaId")) : null;
        const nonMember = readNonMember(player.get("nonMember"));
        batch.update(player.ref, { assignedCountry: emaId == null ? nonMember?.country ?? "" : countries.get(emaId) ?? "" });
    });
    await batch.commit();
    await updateTournamentMers(tournamentId);
}
function timestampToIso(value) {
    return value instanceof firestore_1.Timestamp ? value.toDate().toISOString() : null;
}
async function listTournamentPlayers(tournamentId) {
    const snap = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("players").get();
    return snap.docs
        .map((d) => ({
        id: Number(d.get("id")),
        name: String(d.get("name") ?? ""),
        team: Number(d.get("team") ?? 0),
        country: String(d.get("country") ?? ""),
        assignedEmaId: typeof d.get("assignedEmaId") === "string" ? d.get("assignedEmaId") : null,
        nonMember: readNonMember(d.get("nonMember")),
        createdAt: timestampToIso(d.get("createdAt")),
        updatedAt: timestampToIso(d.get("updatedAt")),
    }))
        .sort((a, b) => a.id - b.id);
}
async function listTournamentTeams(tournamentId) {
    const tournament = firebase_1.db.collection("tournaments").doc(tournamentId);
    const [tournamentSnapshot, players, teams] = await Promise.all([
        tournament.get(),
        tournament.collection("players").get(),
        tournament.collection("teams").get(),
    ]);
    if (!tournamentSnapshot.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    if (tournamentSnapshot.get("isTeams") !== true)
        return [];
    const names = new Map();
    teams.docs.forEach((team) => {
        const id = Number(team.get("id") ?? team.id);
        if (Number.isInteger(id) && id > 0) {
            names.set(id, String(team.get("name") ?? "").trim());
        }
    });
    const playerIdsByTeam = new Map();
    players.docs.forEach((player) => {
        const teamId = Number(player.get("team") ?? 0);
        const playerId = Number(player.get("id") ?? player.id);
        if (!Number.isInteger(teamId) || teamId <= 0 || !Number.isInteger(playerId))
            return;
        playerIdsByTeam.set(teamId, [...(playerIdsByTeam.get(teamId) ?? []), playerId]);
    });
    return [...playerIdsByTeam.entries()]
        .map(([id, playerIds]) => ({
        id,
        name: names.get(id) || `Team ${id}`,
        playerIds: playerIds.sort((a, b) => a - b),
    }))
        .sort((a, b) => a.id - b.id);
}
/**
 * Updates one team and its fixed schedule slots.
 * Players selected from other teams exchange slots with displaced players.
 */
async function updateTournamentTeam(params) {
    const tournament = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const players = tournament.collection("players");
    const teams = tournament.collection("teams");
    const assignmentRefs = tournament.collection("emaPlayerAssignments");
    const tableSummaries = await listTournamentTables(params.tournamentId, null);
    const tournamentHasProgress = tableSummaries.some((table) => table.hasProgress);
    await firebase_1.db.runTransaction(async (transaction) => {
        const [tournamentSnapshot, playerSnapshot, teamSnapshot, currentTeam] = await Promise.all([
            transaction.get(tournament),
            transaction.get(players),
            transaction.get(teams),
            transaction.get(teams.doc(String(params.teamId))),
        ]);
        if (!tournamentSnapshot.exists)
            throw (0, httpError_1.notFound)("Tournament not found");
        if (tournamentSnapshot.get("isTeams") !== true) {
            throw (0, httpError_1.conflict)("This tournament does not use teams");
        }
        const playerDocs = playerSnapshot.docs.slice().sort((a, b) => {
            return Number(a.get("id") ?? a.id) - Number(b.get("id") ?? b.id);
        });
        const targetSlots = playerDocs.filter((player) => Number(player.get("team") ?? 0) === params.teamId);
        if (targetSlots.length === 0)
            throw (0, httpError_1.notFound)("Team not found");
        if (targetSlots.length > 4 || params.emaIds.length !== targetSlots.length) {
            throw (0, httpError_1.conflict)("Team assignments must match its schedule slots");
        }
        const desiredEmaIds = params.emaIds.map((emaId) => emaId?.trim() || null);
        const assignedDesired = desiredEmaIds.filter((emaId) => emaId != null);
        if (new Set(assignedDesired).size !== assignedDesired.length) {
            throw (0, httpError_1.conflict)("A player cannot occupy two team slots");
        }
        const currentByRef = new Map();
        const sourceByEma = new Map();
        playerDocs.forEach((player) => {
            const emaId = typeof player.get("assignedEmaId") === "string"
                ? String(player.get("assignedEmaId")).trim() || null
                : null;
            currentByRef.set(player.ref.path, emaId);
            if (emaId != null)
                sourceByEma.set(emaId, player);
        });
        const desiredSet = new Set(assignedDesired);
        const displaced = targetSlots
            .map((slot) => currentByRef.get(slot.ref.path) ?? null)
            .filter((emaId) => emaId != null && !desiredSet.has(emaId));
        const finalByRef = new Map(currentByRef);
        targetSlots.forEach((slot, index) => {
            finalByRef.set(slot.ref.path, desiredEmaIds[index] ?? null);
        });
        const vacatedSourceSlots = [];
        desiredEmaIds.forEach((emaId) => {
            if (emaId == null)
                return;
            const source = sourceByEma.get(emaId);
            if (source != null && Number(source.get("team") ?? 0) !== params.teamId) {
                vacatedSourceSlots.push(source);
            }
        });
        vacatedSourceSlots.forEach((source, index) => {
            finalByRef.set(source.ref.path, displaced[index] ?? null);
        });
        const membershipChanged = playerDocs.some((player) => {
            return (currentByRef.get(player.ref.path) ?? null) !== (finalByRef.get(player.ref.path) ?? null);
        });
        if (membershipChanged && tournamentHasProgress) {
            throw (0, httpError_1.conflict)("Team players cannot change after table results have started");
        }
        const namesByTeam = new Map();
        playerDocs.forEach((player) => {
            const teamId = Number(player.get("team") ?? 0);
            if (Number.isInteger(teamId) && teamId > 0)
                namesByTeam.set(teamId, `Team ${teamId}`);
        });
        teamSnapshot.docs.forEach((team) => {
            const teamId = Number(team.get("id") ?? team.id);
            if (Number.isInteger(teamId) && teamId > 0) {
                namesByTeam.set(teamId, String(team.get("name") ?? `Team ${teamId}`).trim());
            }
        });
        const duplicateName = [...namesByTeam.entries()].some(([teamId, name]) => {
            return teamId !== params.teamId && name.localeCompare(params.name, undefined, { sensitivity: "accent" }) === 0;
        });
        if (duplicateName)
            throw (0, httpError_1.conflict)("Team name is already in use");
        const affectedEmaIds = new Set();
        playerDocs.forEach((player) => {
            const previous = currentByRef.get(player.ref.path) ?? null;
            const next = finalByRef.get(player.ref.path) ?? null;
            if (previous === next)
                return;
            if (previous != null)
                affectedEmaIds.add(previous);
            if (next != null)
                affectedEmaIds.add(next);
            transaction.update(player.ref, {
                assignedEmaId: next,
                ...(next == null ? {} : { nonMember: null }),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        });
        affectedEmaIds.forEach((emaId) => {
            const owner = playerDocs.find((player) => finalByRef.get(player.ref.path) === emaId);
            if (owner == null) {
                transaction.delete(assignmentRefs.doc(emaId));
            }
            else {
                transaction.set(assignmentRefs.doc(emaId), {
                    playerId: Number(owner.get("id") ?? owner.id),
                    updatedAt: firestore_1.FieldValue.serverTimestamp(),
                });
            }
        });
        transaction.set(teams.doc(String(params.teamId)), {
            id: params.teamId,
            name: params.name,
            ...(currentTeam.exists ? {} : { createdAt: firestore_1.FieldValue.serverTimestamp() }),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
    });
    await refreshAssignmentCountries(params.tournamentId);
    await Promise.all([
        (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "teams"),
        (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "players"),
        (0, dataVersionsService_1.bumpGlobalDataVersion)("tournaments"),
    ]);
}
async function assignTournamentPlayer(params) {
    const playerRef = firebase_1.db.collection("tournaments").doc(params.tournamentId)
        .collection("players").doc(String(params.playerId));
    const players = playerRef.parent;
    const assignmentRefs = firebase_1.db.collection("tournaments").doc(params.tournamentId)
        .collection("emaPlayerAssignments");
    const tableSummaries = await listTournamentTables(params.tournamentId, null);
    const tournamentHasProgress = tableSummaries.some((table) => table.hasProgress);
    await firebase_1.db.runTransaction(async (transaction) => {
        const player = await transaction.get(playerRef);
        if (!player.exists) {
            throw new Error("Player not found");
        }
        const previousEmaId = typeof player.get("assignedEmaId") === "string"
            ? String(player.get("assignedEmaId"))
            : null;
        const previousNonMember = readNonMember(player.get("nonMember"));
        const assignmentChanged = previousEmaId !== params.emaId
            || JSON.stringify(previousNonMember) !== JSON.stringify(params.nonMember);
        if ((previousEmaId != null || previousNonMember != null) && assignmentChanged && tournamentHasProgress) {
            throw (0, httpError_1.conflict)("Players cannot change after table results have started");
        }
        const nextAssignmentRef = params.emaId == null ? null : assignmentRefs.doc(params.emaId);
        const nextAssignment = nextAssignmentRef == null ? null : await transaction.get(nextAssignmentRef);
        const existingAssignments = params.emaId == null
            ? null
            : await transaction.get(players.where("assignedEmaId", "==", params.emaId));
        if (params.emaId != null && nextAssignmentRef != null) {
            const mappedPlayerId = nextAssignment?.exists ? Number(nextAssignment.get("playerId")) : null;
            if (mappedPlayerId != null && mappedPlayerId !== params.playerId) {
                throw (0, httpError_1.conflict)("EMA player is already assigned in this tournament");
            }
            const assignedElsewhere = existingAssignments?.docs.some((document) => document.id !== playerRef.id) ?? false;
            if (assignedElsewhere) {
                throw (0, httpError_1.conflict)("EMA player is already assigned in this tournament");
            }
        }
        let assignedCountry = params.nonMember?.country ?? "";
        if (params.emaId != null) {
            const registryPlayer = await transaction.get(firebase_1.db.collection(playersService_1.EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId));
            if (!registryPlayer.exists)
                throw (0, httpError_1.notFound)("EMA player not found");
            assignedCountry = String(registryPlayer.get("country") ?? "").trim().toUpperCase();
        }
        if (previousEmaId != null && previousEmaId !== params.emaId) {
            transaction.delete(assignmentRefs.doc(previousEmaId));
        }
        if (nextAssignmentRef != null) {
            transaction.set(nextAssignmentRef, {
                playerId: params.playerId,
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
        transaction.update(playerRef, {
            assignedEmaId: params.emaId,
            nonMember: params.nonMember,
            assignedCountry,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    });
    await refreshAssignmentCountries(params.tournamentId);
    await Promise.all([
        (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "players"),
        (0, dataVersionsService_1.bumpGlobalDataVersion)("tournaments"),
    ]);
}
async function listTournamentRounds(tournamentId) {
    const snap = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("rounds").get();
    return snap.docs
        .map((d) => ({
        roundId: Number(d.get("roundId")),
    }))
        .sort((a, b) => a.roundId - b.roundId);
}
async function listTournamentTables(tournamentId, roundId) {
    const collection = firebase_1.db.collection("tournaments").doc(tournamentId).collection("tables");
    const snap = roundId == null
        ? await collection.get()
        : await collection.where("roundId", "==", roundId).get();
    const tables = await Promise.all(snap.docs.map(async (d) => {
        const storedHasProgress = d.get("hasProgress");
        const storedHasValidManualTotals = d.get("hasValidManualTotals");
        if (typeof storedHasProgress === "boolean" && typeof storedHasValidManualTotals === "boolean") {
            return {
                version: Number(d.get("version") ?? 0),
                roundId: Number(d.get("roundId")),
                tableId: Number(d.get("tableId")),
                playerIds: (d.get("playerIds") ?? []).map((x) => Number(x)),
                isCompleted: Boolean(d.get("isCompleted") ?? false),
                useTotalsOnly: Boolean(d.get("useTotalsOnly") ?? true),
                usePointsCalculation: Boolean(d.get("usePointsCalculation") ?? true),
                hasProgress: storedHasProgress,
                hasValidManualTotals: storedHasValidManualTotals,
            };
        }
        // Old documents use this fallback until the summary backfill runs.
        const hands = await d.ref.collection("hands").get();
        const hasHandProgress = hands.docs.some((hand) => {
            const value = hand.data();
            return [
                value.playerWinnerId,
                value.playerLooserId,
                value.handScore,
                value.playerEastPenalty,
                value.playerSouthPenalty,
                value.playerWestPenalty,
                value.playerNorthPenalty,
            ].some((field) => String(field ?? "").trim().length > 0)
                || Boolean(value.isChickenHand)
                || Boolean(value.isDone);
        });
        const hasTableProgress = [
            d.get("playerEastId"),
            d.get("playerSouthId"),
            d.get("playerWestId"),
            d.get("playerNorthId"),
            d.get("playerEastScore"),
            d.get("playerSouthScore"),
            d.get("playerWestScore"),
            d.get("playerNorthScore"),
            d.get("playerEastPoints"),
            d.get("playerSouthPoints"),
            d.get("playerWestPoints"),
            d.get("playerNorthPoints"),
        ].some((field) => String(field ?? "").trim().length > 0);
        const hasValidManualScores = (0, tournamentContentRules_1.hasFourValidScores)([
            d.get("manualPlayerEastScore") || d.get("playerEastScore"),
            d.get("manualPlayerSouthScore") || d.get("playerSouthScore"),
            d.get("manualPlayerWestScore") || d.get("playerWestScore"),
            d.get("manualPlayerNorthScore") || d.get("playerNorthScore"),
        ]);
        const hasValidManualPoints = (0, tournamentContentRules_1.hasValidTablePoints)([
            d.get("manualPlayerEastPoints") || d.get("playerEastPoints"),
            d.get("manualPlayerSouthPoints") || d.get("playerSouthPoints"),
            d.get("manualPlayerWestPoints") || d.get("playerWestPoints"),
            d.get("manualPlayerNorthPoints") || d.get("playerNorthPoints"),
        ]);
        const useTotalsOnly = Boolean(d.get("useTotalsOnly") ?? true);
        const usePointsCalculation = Boolean(d.get("usePointsCalculation") ?? true);
        return {
            version: Number(d.get("version") ?? 0),
            roundId: Number(d.get("roundId")),
            tableId: Number(d.get("tableId")),
            playerIds: (d.get("playerIds") ?? []).map((x) => Number(x)),
            isCompleted: Boolean(d.get("isCompleted") ?? false),
            useTotalsOnly,
            usePointsCalculation,
            hasProgress: hasTableProgress || hasHandProgress || Boolean(d.get("isCompleted") ?? false),
            hasValidManualTotals: useTotalsOnly ? hasValidManualScores : !usePointsCalculation && hasValidManualPoints,
        };
    }));
    return tables
        .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
//# sourceMappingURL=tournamentContentService.js.map