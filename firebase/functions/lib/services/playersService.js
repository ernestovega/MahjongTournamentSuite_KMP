"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateEmaId = validateEmaId;
exports.listPlayers = listPlayers;
exports.createPlayer = createPlayer;
exports.updatePlayer = updatePlayer;
exports.playerExists = playerExists;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
function timestampToIso(value) {
    return value instanceof firestore_1.Timestamp ? value.toDate().toISOString() : null;
}
function toPlayer(emaId, data) {
    return {
        emaId,
        name: String(data.name ?? ""),
        country: String(data.country ?? ""),
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
    const snapshot = await firebase_1.db.collection("players").get();
    return snapshot.docs
        .map((document) => toPlayer(document.id, document.data()))
        .sort((left, right) => left.name.localeCompare(right.name) || left.emaId.localeCompare(right.emaId));
}
async function createPlayer(params) {
    const ref = firebase_1.db.collection("players").doc(params.emaId);
    const existing = await ref.get();
    if (existing.exists) {
        throw (0, httpError_1.badRequest)("A player with this EMA number already exists");
    }
    await ref.create({
        name: params.name,
        country: params.country,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    const created = await ref.get();
    return toPlayer(params.emaId, created.data() ?? {});
}
async function updatePlayer(params) {
    const ref = firebase_1.db.collection("players").doc(params.emaId);
    if (!(await ref.get()).exists) {
        throw (0, httpError_1.notFound)("Player not found");
    }
    await ref.update({ name: params.name, country: params.country, updatedAt: firestore_1.FieldValue.serverTimestamp() });
}
async function playerExists(emaId) {
    return (await firebase_1.db.collection("players").doc(emaId).get()).exists;
}
//# sourceMappingURL=playersService.js.map