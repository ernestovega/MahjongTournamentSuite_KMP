"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAdmin = requireAdmin;
const httpError_1 = require("../httpError");
/** Kept for compatibility. Global administration now requires a superadmin. */
function requireAdmin(_req, res, next) {
    const decoded = res.locals.auth;
    if (decoded?.superadmin === true) {
        next();
        return;
    }
    next((0, httpError_1.forbidden)("Superadmin required"));
}
//# sourceMappingURL=requireAdmin.js.map