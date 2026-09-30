"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.storage = exports.db = exports.auth = exports.firebaseProjectId = void 0;
const app_1 = require("firebase-admin/app");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
const storage_1 = require("firebase-admin/storage");
const storageBucketName = process.env.EMA_FIREBASE_STORAGE_BUCKET?.trim()
    || "mahjong-tournament-suite.firebasestorage.app";
exports.firebaseProjectId = process.env.EMA_FIREBASE_PROJECT?.trim()
    || process.env.GCLOUD_PROJECT?.trim()
    || process.env.GOOGLE_CLOUD_PROJECT?.trim()
    || "mahjong-tournament-suite";
const adminApp = (0, app_1.initializeApp)({
    projectId: exports.firebaseProjectId,
    storageBucket: storageBucketName,
});
exports.auth = (0, auth_1.getAuth)(adminApp);
exports.db = (0, firestore_1.getFirestore)(adminApp);
exports.storage = (0, storage_1.getStorage)(adminApp).bucket(storageBucketName);
exports.db.settings({
    ignoreUndefinedProperties: true,
});
//# sourceMappingURL=firebase.js.map