"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createTournament = createTournament;
exports.listAllTournaments = listAllTournaments;
exports.renameTournament = renameTournament;
exports.updateTournamentSettings = updateTournamentSettings;
exports.listTournamentsForUser = listTournamentsForUser;
exports.deleteTournament = deleteTournament;
const firestore_1 = require("firebase-admin/firestore");
const node_crypto_1 = require("node:crypto");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const usersService_1 = require("./usersService");
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
    return {
        id: d.id,
        name: d.get("name") ?? "",
        shortName: String(d.get("shortName") ?? d.get("name") ?? "").trim().slice(0, 10),
        primaryColor: /^#[0-9A-F]{6}$/i.test(String(d.get("primaryColor") ?? ""))
            ? String(d.get("primaryColor")).toUpperCase()
            : "#02B16B",
        associationLogoUrl: typeof d.get("associationLogoUrl") === "string" ? d.get("associationLogoUrl") : null,
        isTeams: d.get("isTeams") ?? false,
        numPlayers: d.get("numPlayers") ?? 0,
        numRounds: d.get("numRounds") ?? 0,
        eventStartDate: d.get("eventStartDate") ?? d.get("eventDate") ?? null,
        eventEndDate: d.get("eventEndDate") ?? d.get("eventDate") ?? null,
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
    const ref = firebase_1.db.collection("tournaments").doc();
    const logo = params.associationLogoContentType && params.associationLogoDataBase64
        ? await saveAssociationLogo(ref.id, params.associationLogoContentType, params.associationLogoDataBase64)
        : null;
    const tournamentDoc = {
        name: params.name,
        shortName: normalizeShortName(params.shortName),
        primaryColor: normalizePrimaryColor(params.primaryColor),
        associationLogoUrl: logo?.url ?? null,
        associationLogoPath: logo?.path ?? null,
        eventStartDate: params.eventStartDate,
        eventEndDate: params.eventEndDate,
        isTeams: params.isTeams,
        numPlayers: params.numPlayers,
        numRounds: params.numRounds,
        numTries: params.numTries,
        isCompleted: false,
        createdByUid: params.createdByUid,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
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
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    await flushBatch();
    await Promise.all(batchCommits);
    const snap = await ref.get();
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
async function updateTournamentSettings(params) {
    const normalizedName = params.name.trim();
    if (normalizedName.length === 0)
        throw (0, httpError_1.badRequest)("Tournament name is required");
    const shortName = normalizeShortName(params.shortName);
    const primaryColor = normalizePrimaryColor(params.primaryColor);
    const ref = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const before = await ref.get();
    if (!before.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const oldLogoPath = String(before.get("associationLogoPath") ?? "").trim();
    let logo;
    if (params.removeAssociationLogo === true) {
        logo = null;
    }
    else if (params.associationLogoContentType && params.associationLogoDataBase64) {
        logo = await saveAssociationLogo(params.tournamentId, params.associationLogoContentType, params.associationLogoDataBase64);
    }
    const update = {
        name: normalizedName,
        shortName,
        primaryColor,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    };
    if (logo !== undefined) {
        update.associationLogoPath = logo?.path ?? null;
        update.associationLogoUrl = logo?.url ?? null;
    }
    await ref.update(update);
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
async function deleteTournament(tournamentId) {
    const ref = firebase_1.db.collection("tournaments").doc(tournamentId);
    const snap = await ref.get();
    if (!snap.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const recursiveDelete = firebase_1.db
        .recursiveDelete;
    if (typeof recursiveDelete === "function") {
        await recursiveDelete(ref);
        return;
    }
    // Fallback: delete the parent document (subcollections will remain).
    // This should be extremely rare; most firebase-admin builds expose recursiveDelete.
    await ref.delete();
}
//# sourceMappingURL=tournamentsService.js.map