"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireEditor = requireEditor;
const httpError_1 = require("../httpError");
const globalRole_1 = require("../../models/globalRole");
/** Lets only accounts with the editor or admin claim use management routes. Run it after requireAuth. */
function requireEditor(_req, res, next) {
    const decoded = res.locals.auth;
    if ((0, globalRole_1.hasEditorAccess)(decoded)) {
        next();
        return;
    }
    next((0, httpError_1.forbidden)("Editor required"));
}
//# sourceMappingURL=requireEditor.js.map