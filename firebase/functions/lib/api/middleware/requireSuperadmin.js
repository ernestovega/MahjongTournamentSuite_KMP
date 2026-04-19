"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireSuperadmin = requireSuperadmin;
const httpError_1 = require("../httpError");
function requireSuperadmin(_req, res, next) {
    const decoded = res.locals.auth;
    if (decoded?.superadmin === true) {
        next();
        return;
    }
    next((0, httpError_1.forbidden)("Superadmin required"));
}
//# sourceMappingURL=requireSuperadmin.js.map