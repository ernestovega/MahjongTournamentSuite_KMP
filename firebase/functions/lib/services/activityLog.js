"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ACTIVITY_COLLECTION = void 0;
exports.sanitizeDetails = sanitizeDetails;
exports.buildActivityEntry = buildActivityEntry;
exports.recordActivity = recordActivity;
exports.tournamentNameOf = tournamentNameOf;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
/**
 * The activity log keeps one record for each change that is not a table save: tournament settings, players, teams,
 * editors, accounts and the player registry. It is append-only. Table saves have their own log in "changes".
 * Records outlive a deleted tournament. They hold no e-mail addresses and no image data.
 */
exports.ACTIVITY_COLLECTION = "activity";
const MAX_TEXT = 200;
const SECRET_KEY = /base64|email|password|token/i;
/** Keeps the short values of a request. Long text, image data and e-mail addresses stay out of the log. */
function sanitizeDetails(details) {
    return sanitizeObject(details, 0);
}
function sanitizeObject(details, depth) {
    const clean = {};
    for (const [key, value] of Object.entries(details ?? {})) {
        if (SECRET_KEY.test(key) || value === undefined)
            continue;
        clean[key] = sanitizeValue(value, depth);
    }
    return clean;
}
function sanitizeValue(value, depth) {
    if (value == null || typeof value === "number" || typeof value === "boolean")
        return value ?? null;
    if (typeof value === "string")
        return value.length > MAX_TEXT ? `${value.slice(0, MAX_TEXT)}…` : value;
    if (depth >= 3)
        return null;
    if (Array.isArray(value))
        return value.slice(0, 50).map((item) => sanitizeValue(item, depth + 1));
    if (typeof value === "object")
        return sanitizeObject(value, depth + 1);
    return null;
}
function buildActivityEntry(params) {
    return {
        action: params.action,
        uid: params.actor?.uid ?? null,
        name: params.actor?.name ?? null,
        tournamentId: params.tournamentId ?? null,
        target: params.target ?? null,
        details: sanitizeDetails(params.details),
    };
}
/** Writes one record. A failed write is reported and never fails the change that was already made. */
async function recordActivity(params) {
    try {
        await firebase_1.db.collection(exports.ACTIVITY_COLLECTION).add({ ...buildActivityEntry(params), at: firestore_1.FieldValue.serverTimestamp() });
    }
    catch (error) {
        console.error("Writing the activity log failed", params.action, error);
    }
}
/** The name of a tournament, read before it is deleted. */
async function tournamentNameOf(tournamentId) {
    try {
        const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId).get();
        return snapshot.exists ? String(snapshot.get("name") ?? "") : null;
    }
    catch {
        return null;
    }
}
//# sourceMappingURL=activityLog.js.map