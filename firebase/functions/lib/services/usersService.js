"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getUserProfile = getUserProfile;
exports.getEmaIdMapping = getEmaIdMapping;
exports.assertEmaIdAvailable = assertEmaIdAvailable;
exports.getEmailForEmaId = getEmailForEmaId;
exports.linkEmaIdToUser = linkEmaIdToUser;
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
async function getEmaIdMapping(emaId) {
    const snap = await firebase_1.db.doc(`emaIdUsers/${emaId}`).get();
    if (!snap.exists) {
        throw (0, httpError_1.notFound)("Unknown emaId");
    }
    return {
        uid: snap.get("uid"),
        email: snap.get("email"),
        emaId: snap.get("emaId"),
    };
}
async function assertEmaIdAvailable(emaId) {
    const snap = await firebase_1.db.doc(`emaIdUsers/${emaId}`).get();
    if (snap.exists) {
        throw (0, httpError_1.conflict)("emaId already in use");
    }
}
async function getEmailForEmaId(emaId) {
    const mapping = await getEmaIdMapping(emaId);
    return mapping.email;
}
async function linkEmaIdToUser(uid, email, emaId) {
    const mappingRef = firebase_1.db.doc(`emaIdUsers/${emaId}`);
    await firebase_1.db.runTransaction(async (tx) => {
        const current = await tx.get(mappingRef);
        if (current.exists) {
            throw (0, httpError_1.conflict)("emaId already in use");
        }
        tx.set(mappingRef, {
            uid,
            email,
            emaId,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
        });
        tx.set(firebase_1.db.doc(`users/${uid}`), {
            uid,
            email,
            emaId,
            contactEmail: email,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
    });
}
//# sourceMappingURL=usersService.js.map