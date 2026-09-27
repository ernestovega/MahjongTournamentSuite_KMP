"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getGlobalUserRole = getGlobalUserRole;
exports.parseGlobalUserRole = parseGlobalUserRole;
exports.hasAdminClaim = hasAdminClaim;
function getGlobalUserRole(user) {
    return user.customClaims?.admin === true ? "ADMIN" : "EDITOR";
}
function parseGlobalUserRole(value) {
    return value === "EDITOR" || value === "ADMIN" ? value : null;
}
function hasAdminClaim(claims) {
    return claims?.admin === true;
}
//# sourceMappingURL=globalRole.js.map