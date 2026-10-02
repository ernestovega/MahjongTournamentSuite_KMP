"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createTournament = createTournament;
exports.listAllTournaments = listAllTournaments;
exports.renameTournament = renameTournament;
exports.updateTournamentSettings = updateTournamentSettings;
exports.listTournamentsForUser = listTournamentsForUser;
exports.deleteTournamentResources = deleteTournamentResources;
exports.deleteTournament = deleteTournament;
const firestore_1 = require("firebase-admin/firestore");
const node_crypto_1 = require("node:crypto");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const tournamentDates_1 = require("./tournamentDates");
const usersService_1 = require("./usersService");
const dataVersionsService_1 = require("./dataVersionsService");
const tournamentSchedule_1 = require("./tournamentSchedule");
function toIsoString(value) {
    if (value instanceof firestore_1.Timestamp)
        return value.toDate().toISOString();
    return null;
}
function formatDisplayName(email) {
    const rawName = email
        .split("@", 1)[0]
        .split(".").join(" ")
        .split("_").join(" ")
        .split("-").join(" ")
        .trim()
        .split(" ")
        .filter((part) => part.trim().length > 0)
        .map((part) => {
        const lower = part.toLowerCase();
        return lower.charAt(0).toUpperCase() + lower.slice(1);
    })
        .join(" ");
    return rawName.length > 0 ? rawName : email;
}
async function resolveCreatedByName(uid) {
    if (uid == null || uid.trim().length === 0)
        return null;
    try {
        const profile = await (0, usersService_1.getUserProfile)(uid);
        return formatDisplayName(profile.email);
    }
    catch (_error) {
        return null;
    }
}
async function mapTournamentDoc(d) {
    const createdByUid = d.get("createdByUid") ?? d.get("createdBy") ?? null;
    const createdByName = d.get("createdByName") ?? await resolveCreatedByName(createdByUid);
    const numRounds = Number(d.get("numRounds") ?? 0);
    const eventStartDate = d.get("eventStartDate") ?? d.get("eventDate") ?? null;
    const eventEndDate = d.get("eventEndDate") ?? d.get("eventDate") ?? null;
    const safeStartDate = eventStartDate ?? "0000-01-01";
    const safeEndDate = eventEndDate ?? "9999-12-31";
    return {
        id: d.id,
        name: d.get("name") ?? "",
        shortName: String(d.get("shortName") ?? d.get("name") ?? "").trim().slice(0, 10),
        primaryColor: /^#[0-9A-F]{6}$/i.test(String(d.get("primaryColor") ?? ""))
            ? String(d.get("primaryColor")).toUpperCase()
            : "#02B16B",
        associationLogoUrl: typeof d.get("associationLogoUrl") === "string" ? d.get("associationLogoUrl") : null,
        hostCountry: String(d.get("hostCountry") ?? "").trim().toUpperCase(),
        hostCity: String(d.get("hostCity") ?? "").trim(),
        isTeams: d.get("isTeams") ?? false,
        numPlayers: d.get("numPlayers") ?? 0,
        numRounds,
        eventStartDate,
        eventEndDate,
        roundSchedules: (0, tournamentSchedule_1.readStoredRoundSchedules)(d.get("roundSchedules"), numRounds, safeStartDate, safeEndDate),
        agendaItems: (0, tournamentSchedule_1.readStoredAgendaItems)(d.get("agendaItems"), safeStartDate, safeEndDate),
        numTries: d.get("numTries") ?? 0,
        isCompleted: d.get("isCompleted") ?? false,
        createdByUid,
        createdByName,
        createdAt: toIsoString(d.get("createdAt")),
        updatedAt: toIsoString(d.get("updatedAt")),
    };
}
async function createTournament(params) {
    const numTablesPerRound = params.numPlayers / 4;
    const expectedTables = params.numRounds * numTablesPerRound;
    if (params.players.length !== params.numPlayers) {
        throw (0, httpError_1.badRequest)("Invalid players payload", { expected: params.numPlayers, received: params.players.length });
    }
    if (params.tables.length !== expectedTables) {
        throw (0, httpError_1.badRequest)("Invalid tables payload", { expected: expectedTables, received: params.tables.length });
    }
    const roundSchedules = (0, tournamentSchedule_1.normalizeRoundSchedules)(params.roundSchedules, params.numRounds, params.eventStartDate, params.eventEndDate);
    const agendaItems = (0, tournamentSchedule_1.normalizeAgendaItems)(params.agendaItems, params.eventStartDate, params.eventEndDate);
    const ref = firebase_1.db.collection("tournaments").doc();
    const logo = params.associationLogoContentType && params.associationLogoDataBase64
        ? await saveAssociationLogo(ref.id, params.associationLogoContentType, params.associationLogoDataBase64)
        : params.associationLogoSourceTournamentId
            ? await copyAssociationLogo(params.associationLogoSourceTournamentId, ref.id)
            : null;
    const tournamentDoc = {
        name: params.name,
        shortName: normalizeShortName(params.shortName),
        primaryColor: normalizePrimaryColor(params.primaryColor),
        associationLogoUrl: logo?.url ?? null,
        associationLogoPath: logo?.path ?? null,
        eventStartDate: params.eventStartDate,
        eventEndDate: params.eventEndDate,
        hostCountry: params.hostCountry.trim().toUpperCase(),
        hostCity: params.hostCity.trim(),
        isTeams: params.isTeams,
        numPlayers: params.numPlayers,
        numRounds: params.numRounds,
        roundSchedules,
        agendaItems,
        numTries: params.numTries,
        isCompleted: false,
        createdByUid: params.createdByUid,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        dataVersions: {
            members: { revision: 0, changedAt: null },
            players: { revision: 1, changedAt: firestore_1.FieldValue.serverTimestamp() },
            teams: { revision: params.isTeams ? 1 : 0, changedAt: firestore_1.FieldValue.serverTimestamp() },
            rounds: { revision: 1, changedAt: firestore_1.FieldValue.serverTimestamp() },
            tables: { revision: 1, changedAt: firestore_1.FieldValue.serverTimestamp() },
        },
    };
    await ref.set(tournamentDoc);
    // Persist the client-generated schedule payload (players/rounds/tables).
    // Hands are created lazily when a table is first opened to keep write volume manageable.
    const batchCommits = [];
    let batch = firebase_1.db.batch();
    let opsInBatch = 0;
    const flushBatch = async () => {
        if (opsInBatch === 0)
            return;
        batchCommits.push(batch.commit());
        batch = firebase_1.db.batch();
        opsInBatch = 0;
    };
    const addSet = (docRef, data) => {
        batch.set(docRef, data);
        opsInBatch++;
        if (opsInBatch >= 450) {
            // Keep a margin under 500 for safety if we add more writes later.
            return flushBatch();
        }
        return Promise.resolve();
    };
    for (const player of params.players) {
        const playerRef = ref.collection("players").doc(String(player.id));
        // eslint-disable-next-line no-await-in-loop
        await addSet(playerRef, {
            id: player.id,
            name: player.name ?? `Player ${player.id}`,
            team: player.team,
            country: player.country ?? "",
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    if (params.isTeams) {
        const teamIds = [...new Set(params.players.map((player) => player.team))]
            .filter((teamId) => Number.isInteger(teamId) && teamId > 0)
            .sort((a, b) => a - b);
        for (const teamId of teamIds) {
            const teamRef = ref.collection("teams").doc(String(teamId));
            // eslint-disable-next-line no-await-in-loop
            await addSet(teamRef, {
                id: teamId,
                name: `Team ${teamId}`,
                createdAt: firestore_1.FieldValue.serverTimestamp(),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
    }
    for (let roundId = 1; roundId <= params.numRounds; roundId++) {
        const roundRef = ref.collection("rounds").doc(String(roundId));
        // eslint-disable-next-line no-await-in-loop
        await addSet(roundRef, {
            roundId,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    for (const table of params.tables) {
        const tableRef = ref.collection("tables").doc(`${table.roundId}_${table.tableId}`);
        // eslint-disable-next-line no-await-in-loop
        await addSet(tableRef, {
            roundId: table.roundId,
            tableId: table.tableId,
            playerIds: table.playerIds,
            playerEastId: "",
            playerSouthId: "",
            playerWestId: "",
            playerNorthId: "",
            playerEastScore: "",
            playerSouthScore: "",
            playerWestScore: "",
            playerNorthScore: "",
            playerEastPoints: "",
            playerSouthPoints: "",
            playerWestPoints: "",
            playerNorthPoints: "",
            manualPlayerEastScore: "",
            manualPlayerSouthScore: "",
            manualPlayerWestScore: "",
            manualPlayerNorthScore: "",
            manualPlayerEastPoints: "",
            manualPlayerSouthPoints: "",
            manualPlayerWestPoints: "",
            manualPlayerNorthPoints: "",
            isCompleted: Boolean(table.isCompleted ?? false),
            useTotalsOnly: Boolean(table.useTotalsOnly ?? true),
            usePointsCalculation: true,
            hasProgress: Boolean(table.isCompleted ?? false),
            hasValidManualTotals: false,
            version: 0,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    await flushBatch();
    await Promise.all(batchCommits);
    const snap = await ref.get();
    await (0, dataVersionsService_1.bumpGlobalDataVersion)("tournaments");
    return mapTournamentDoc(snap);
}
async function listAllTournaments() {
    const snap = await firebase_1.db.collection("tournaments").orderBy("updatedAt", "desc").get();
    return Promise.all(snap.docs.map((d) => mapTournamentDoc(d)));
}
async function renameTournament(tournamentId, name) {
    const normalizedName = name.trim();
    if (normalizedName.length === 0)
        throw (0, httpError_1.badRequest)("Tournament name is required");
    const ref = firebase_1.db.collection("tournaments").doc(tournamentId);
    const snap = await ref.get();
    if (!snap.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    await ref.update({
        name: normalizedName,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    await (0, dataVersionsService_1.bumpGlobalDataVersion)("tournaments");
}
const logoExtensions = {
    "image/jpeg": "jpg",
    "image/png": "png",
};
function normalizeShortName(value) {
    const shortName = value.trim();
    if (shortName.length === 0)
        throw (0, httpError_1.badRequest)("Tournament short name is required");
    if (shortName.length > 10)
        throw (0, httpError_1.badRequest)("Tournament short name must contain at most 10 characters");
    return shortName;
}
function normalizePrimaryColor(value) {
    const color = value.trim().toUpperCase();
    if (!/^#[0-9A-F]{6}$/.test(color))
        throw (0, httpError_1.badRequest)("Primary color must use #RRGGBB format");
    return color;
}
function hasValidLogoSignature(bytes, contentType) {
    if (contentType === "image/jpeg") {
        return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    }
    return bytes.length >= 8
        && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
}
async function saveAssociationLogo(tournamentId, contentType, dataBase64) {
    const extension = logoExtensions[contentType];
    if (!extension)
        throw (0, httpError_1.badRequest)("Association logo must be a JPEG or PNG image");
    const bytes = Buffer.from(dataBase64, "base64");
    if (bytes.length === 0)
        throw (0, httpError_1.badRequest)("Association logo is empty");
    if (bytes.length > 2 * 1024 * 1024)
        throw (0, httpError_1.badRequest)("Association logo must be 2 MB or smaller");
    if (!hasValidLogoSignature(bytes, contentType)) {
        throw (0, httpError_1.badRequest)("Association logo data does not match its image type");
    }
    const path = `tournamentLogos/${tournamentId}.${extension}`;
    const file = firebase_1.storage.file(path);
    const token = (0, node_crypto_1.randomUUID)();
    await file.save(bytes, {
        contentType,
        metadata: {
            cacheControl: "public, max-age=604800",
            metadata: { firebaseStorageDownloadTokens: token },
        },
    });
    const objectName = encodeURIComponent(path);
    return {
        path,
        url: `https://firebasestorage.googleapis.com/v0/b/${firebase_1.storage.name}/o/${objectName}?alt=media&token=${token}`,
    };
}
async function copyAssociationLogo(sourceTournamentId, targetTournamentId) {
    const source = await firebase_1.db.collection("tournaments").doc(sourceTournamentId).get();
    if (!source.exists)
        throw (0, httpError_1.notFound)("Source tournament not found");
    const sourcePath = String(source.get("associationLogoPath") ?? "").trim();
    if (sourcePath.length === 0)
        throw (0, httpError_1.badRequest)("The selected tournament has no reusable logo");
    const sourceFile = firebase_1.storage.file(sourcePath);
    const [metadata] = await sourceFile.getMetadata();
    const contentType = String(metadata.contentType ?? "").trim();
    if (!(contentType in logoExtensions)) {
        throw (0, httpError_1.badRequest)("The selected tournament logo has an unsupported image type");
    }
    const [bytes] = await sourceFile.download();
    return saveAssociationLogo(targetTournamentId, contentType, bytes.toString("base64"));
}
async function updateTournamentSettings(params) {
    const normalizedName = params.name.trim();
    if (normalizedName.length === 0)
        throw (0, httpError_1.badRequest)("Tournament name is required");
    const shortName = normalizeShortName(params.shortName);
    const primaryColor = normalizePrimaryColor(params.primaryColor);
    if (!(0, tournamentDates_1.isValidIsoDateRange)(params.eventStartDate, params.eventEndDate)) {
        throw (0, httpError_1.badRequest)("Tournament dates must use yyyy-MM-dd, and the end date must not be before the start date");
    }
    const ref = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const before = await ref.get();
    if (!before.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const numRounds = Number(before.get("numRounds") ?? 0);
    const roundSchedules = params.roundSchedules === undefined
        ? (0, tournamentSchedule_1.readStoredRoundSchedules)(before.get("roundSchedules"), numRounds, params.eventStartDate, params.eventEndDate)
        : (0, tournamentSchedule_1.normalizeRoundSchedules)(params.roundSchedules, numRounds, params.eventStartDate, params.eventEndDate);
    const agendaItems = params.agendaItems === undefined
        ? (0, tournamentSchedule_1.readStoredAgendaItems)(before.get("agendaItems"), params.eventStartDate, params.eventEndDate)
        : (0, tournamentSchedule_1.normalizeAgendaItems)(params.agendaItems, params.eventStartDate, params.eventEndDate);
    const oldLogoPath = String(before.get("associationLogoPath") ?? "").trim();
    let logo;
    if (params.removeAssociationLogo === true) {
        logo = null;
    }
    else if (params.associationLogoContentType && params.associationLogoDataBase64) {
        logo = await saveAssociationLogo(params.tournamentId, params.associationLogoContentType, params.associationLogoDataBase64);
    }
    else if (params.associationLogoSourceTournamentId) {
        if (params.associationLogoSourceTournamentId === params.tournamentId) {
            throw (0, httpError_1.badRequest)("Select a different tournament logo to reuse");
        }
        logo = await copyAssociationLogo(params.associationLogoSourceTournamentId, params.tournamentId);
    }
    const update = {
        name: normalizedName,
        shortName,
        primaryColor,
        eventStartDate: params.eventStartDate,
        eventEndDate: params.eventEndDate,
        hostCountry: params.hostCountry.trim().toUpperCase(),
        hostCity: params.hostCity.trim(),
        roundSchedules,
        agendaItems,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    };
    if (logo !== undefined) {
        update.associationLogoPath = logo?.path ?? null;
        update.associationLogoUrl = logo?.url ?? null;
    }
    await ref.update(update);
    await Promise.all([
        (0, dataVersionsService_1.bumpGlobalDataVersion)("tournaments"),
        (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "rounds"),
    ]);
    if (oldLogoPath.length > 0 && (logo === null || (logo != null && logo.path !== oldLogoPath))) {
        await firebase_1.storage.file(oldLogoPath).delete({ ignoreNotFound: true }).catch(() => undefined);
    }
    return mapTournamentDoc(await ref.get());
}
function isDocRef(value) {
    return value != null;
}
async function listTournamentsForUser(uid) {
    const memberSnaps = await firebase_1.db.collectionGroup("members").where("uid", "==", uid).get();
    const tournamentRefs = memberSnaps.docs
        .map((m) => m.ref.parent.parent)
        .filter(isDocRef);
    if (tournamentRefs.length === 0)
        return [];
    const tournamentSnaps = await firebase_1.db.getAll(...tournamentRefs);
    return Promise.all(tournamentSnaps
        .filter((t) => t.exists)
        .map((d) => mapTournamentDoc(d)));
}
async function deleteTournamentResources(params) {
    const logoPath = params.logoPath.trim();
    if (logoPath.length > 0) {
        await params.deleteLogo(logoPath);
    }
    await params.deleteTournamentTree();
}
async function deleteTournament(tournamentId) {
    const ref = firebase_1.db.collection("tournaments").doc(tournamentId);
    const snap = await ref.get();
    if (!snap.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    await deleteTournamentResources({
        logoPath: String(snap.get("associationLogoPath") ?? ""),
        deleteLogo: async (logoPath) => {
            await firebase_1.storage.file(logoPath).delete({ ignoreNotFound: true });
        },
        deleteTournamentTree: async () => {
            await firebase_1.db.recursiveDelete(ref);
        },
    });
    await (0, dataVersionsService_1.bumpGlobalDataVersion)("tournaments");
}
//# sourceMappingURL=tournamentsService.js.map