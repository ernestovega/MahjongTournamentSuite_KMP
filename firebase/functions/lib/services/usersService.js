"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getUserProfile = getUserProfile;
exports.getUserProfileByEmaId = getUserProfileByEmaId;
exports.assertEmaIdAvailable = assertEmaIdAvailable;
exports.createUserProfile = createUserProfile;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
async function getUserProfile(uid) {
    const snap = await firebase_1.db.doc(`users/${uid}`).get();
    if (!snap.exists) {
        throw (0, httpError_1.notFound)("User profile not found");
    }
    const email = snap.get("email");
    const emaId = snap.get("emaId");
    const contactEmail = snap.get("contactEmail") ?? email;
    return { uid, email, emaId, contactEmail };
}
async function getUserProfileByEmaId(emaId) {
    const matches = await firebase_1.db.collection("users").where("emaId", "==", emaId).limit(1).get();
    if (matches.empty) {
        throw (0, httpError_1.notFound)("Unknown emaId");
    }
    return getUserProfile(matches.docs[0].id);
}
async function assertEmaIdAvailable(emaId) {
    const matches = await firebase_1.db.collection("users").where("emaId", "==", emaId).limit(1).get();
    if (!matches.empty) {
        throw (0, httpError_1.conflict)("emaId already in use");
    }
}
async function createUserProfile(uid, email, emaId) {
    await firebase_1.db.doc(`users/${uid}`).set({
        uid,
        email,
        emaId,
        contactEmail: email,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
}
//# sourceMappingURL=usersService.js.map