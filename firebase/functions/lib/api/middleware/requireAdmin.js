"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAdmin = requireAdmin;
const httpError_1 = require("../httpError");
/** Allows global admins and superadmins to manage the shared player base. */
function requireAdmin(_req, res, next) {
    const decoded = res.locals.auth;
    if (decoded?.admin === true || decoded?.superadmin === true) {
        next();
        return;
    }
    next((0, httpError_1.forbidden)("Admin required"));
}
//# sourceMappingURL=requireAdmin.js.map