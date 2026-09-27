"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAdmin = requireAdmin;
const httpError_1 = require("../httpError");
const globalRole_1 = require("../../models/globalRole");
function requireAdmin(_req, res, next) {
    const decoded = res.locals.auth;
    if ((0, globalRole_1.hasAdminClaim)(decoded)) {
        next();
        return;
    }
    next((0, httpError_1.forbidden)("Admin required"));
}
//# sourceMappingURL=requireAdmin.js.map