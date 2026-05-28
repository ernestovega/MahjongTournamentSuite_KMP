"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.db = exports.auth = void 0;
const app_1 = require("firebase-admin/app");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
const adminApp = (0, app_1.initializeApp)();
exports.auth = (0, auth_1.getAuth)(adminApp);
exports.db = (0, firestore_1.getFirestore)(adminApp);
exports.db.settings({
    ignoreUndefinedProperties: true,
});
//# sourceMappingURL=firebase.js.map