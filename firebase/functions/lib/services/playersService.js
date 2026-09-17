"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EMA_PLAYER_REGISTRY_COLLECTION = void 0;
exports.validateEmaId = validateEmaId;
exports.listPlayers = listPlayers;
exports.createPlayer = createPlayer;
exports.updatePlayer = updatePlayer;
exports.updatePlayerPhoto = updatePlayerPhoto;
exports.playerExists = playerExists;
const firestore_1 = require("firebase-admin/firestore");
const node_crypto_1 = require("node:crypto");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const playerName_1 = require("./playerName");
/** Firestore collection for EMA registry records. Never use this for tournament player slots. */
exports.EMA_PLAYER_REGISTRY_COLLECTION = "emaPlayerRegistry";
function timestampToIso(value) {
    return value instanceof firestore_1.Timestamp ? value.toDate().toISOString() : null;
}
function toPlayer(emaId, data) {
    return {
        emaId,
        name: (0, playerName_1.normalizePlayerName)(String(data.name ?? "")),
        country: String(data.country ?? ""),
        photoUrl: typeof data.photoUrl === "string" ? data.photoUrl : null,
        createdAt: timestampToIso(data.createdAt),
        updatedAt: timestampToIso(data.updatedAt),
    };
}
function validateEmaId(value) {
    const emaId = String(value ?? "").trim();
    if (!/^\d+$/.test(emaId)) {
        throw (0, httpError_1.badRequest)("EMA number must contain only digits");
    }
    return emaId;
}
async function listPlayers() {
    const snapshot = await firebase_1.db.collection(exports.EMA_PLAYER_REGISTRY_COLLECTION).get();
    return snapshot.docs
        .map((document) => toPlayer(document.id, document.data()))
        .sort((left, right) => left.name.localeCompare(right.name) || left.emaId.localeCompare(right.emaId));
}
async function createPlayer(params) {
    const ref = firebase_1.db.collection(exports.EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId);
    const existing = await ref.get();
    if (existing.exists) {
        throw (0, httpError_1.badRequest)("A player with this EMA number already exists");
    }
    await ref.create({
        name: (0, playerName_1.normalizePlayerName)(params.name),
        country: params.country,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    const created = await ref.get();
    return toPlayer(params.emaId, created.data() ?? {});
}
async function updatePlayer(params) {
    const previousRef = firebase_1.db.collection(exports.EMA_PLAYER_REGISTRY_COLLECTION).doc(params.previousEmaId);
    const previousSnapshot = await previousRef.get();
    if (!previousSnapshot.exists) {
        throw (0, httpError_1.notFound)("Player not found");
    }
    const targetRef = firebase_1.db.collection(exports.EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId);
    if (params.previousEmaId !== params.emaId && (await targetRef.get()).exists) {
        throw (0, httpError_1.badRequest)("A player with this EMA number already exists");
    }
    const update = {
        name: (0, playerName_1.normalizePlayerName)(params.name),
        country: params.country,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    };
    if (params.previousEmaId === params.emaId) {
        await previousRef.update(update);
        return;
    }
    const references = await firebase_1.db.collectionGroup("players")
        .where("assignedEmaId", "==", params.previousEmaId)
        .get();
    const batch = firebase_1.db.batch();
    batch.create(targetRef, { ...previousSnapshot.data(), ...update });
    batch.delete(previousRef);
    references.docs.forEach((reference) => batch.update(reference.ref, { assignedEmaId: params.emaId, updatedAt: firestore_1.FieldValue.serverTimestamp() }));
    await batch.commit();
}
const photoExtensions = {
    "image/gif": "gif",
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
};
function hasValidImageSignature(bytes, contentType) {
    if (contentType === "image/jpeg") {
        return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    }
    if (contentType === "image/png") {
        return bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    }
    if (contentType === "image/gif") {
        const signature = bytes.subarray(0, 6).toString("ascii");
        return signature === "GIF87a" || signature === "GIF89a";
    }
    if (contentType === "image/webp") {
        return bytes.length >= 12
            && bytes.subarray(0, 4).toString("ascii") === "RIFF"
            && bytes.subarray(8, 12).toString("ascii") === "WEBP";
    }
    return false;
}
async function updatePlayerPhoto(params) {
    const extension = photoExtensions[params.contentType];
    if (!extension)
        throw (0, httpError_1.badRequest)("Photo must be a JPEG, PNG, WebP, or GIF image");
    const ref = firebase_1.db.collection(exports.EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId);
    if (!(await ref.get()).exists)
        throw (0, httpError_1.notFound)("Player not found");
    const bytes = Buffer.from(params.dataBase64, "base64");
    if (bytes.length === 0)
        throw (0, httpError_1.badRequest)("Photo is empty");
    if (bytes.length > 5 * 1024 * 1024)
        throw (0, httpError_1.badRequest)("Photo must be 5 MB or smaller");
    if (!hasValidImageSignature(bytes, params.contentType))
        throw (0, httpError_1.badRequest)("Photo data does not match its image type");
    const file = firebase_1.storage.file(`playerPhotos/${params.emaId}.${extension}`);
    const token = (0, node_crypto_1.randomUUID)();
    await file.save(bytes, {
        contentType: params.contentType,
        metadata: {
            cacheControl: "public, max-age=604800",
            metadata: { firebaseStorageDownloadTokens: token },
        },
    });
    const objectName = encodeURIComponent(file.name);
    const photoUrl = `https://firebasestorage.googleapis.com/v0/b/${firebase_1.storage.name}/o/${objectName}?alt=media&token=${token}`;
    await ref.update({ photoUrl, updatedAt: firestore_1.FieldValue.serverTimestamp() });
    const updated = await ref.get();
    return toPlayer(params.emaId, updated.data() ?? {});
}
async function playerExists(emaId) {
    return (await firebase_1.db.collection(exports.EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()).exists;
}
//# sourceMappingURL=playersService.js.map