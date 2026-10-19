"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.hasEditorAccess = hasEditorAccess;
exports.hasPlayerClaim = hasPlayerClaim;
exports.hasAdminClaim = hasAdminClaim;
exports.getGlobalUserRoles = getGlobalUserRoles;
exports.getGlobalUserRole = getGlobalUserRole;
exports.parseGlobalUserRole = parseGlobalUserRole;
/** An account may use management routes only when it is an admin or an editor. */
function hasEditorAccess(claims) {
    return claims?.admin === true || claims?.editor === true;
}
function hasPlayerClaim(claims) {
    return claims?.player === true;
}
function hasAdminClaim(claims) {
    return claims?.admin === true;
}
/** All capabilities of an account, in rank order: ADMIN, EDITOR, PLAYER. */
function getGlobalUserRoles(user) {
    const claims = user.customClaims;
    const roles = [];
    if (hasAdminClaim(claims))
        roles.push("ADMIN");
    if (claims?.editor === true)
        roles.push("EDITOR");
    if (hasPlayerClaim(claims))
        roles.push("PLAYER");
    return roles;
}
/** The highest capability of an account. An account with no claim has no capability and reads as a player. */
function getGlobalUserRole(user) {
    return getGlobalUserRoles(user)[0] ?? "PLAYER";
}
/** Roles that any management route may set. Only an admin creates a player account, and that route checks it. */
function parseGlobalUserRole(value) {
    return value === "EDITOR" || value === "ADMIN" ? value : null;
}
//# sourceMappingURL=globalRole.js.map