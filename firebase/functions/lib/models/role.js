"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ROLE_ORDER = void 0;
exports.roleMeetsMinimum = roleMeetsMinimum;
exports.parseRole = parseRole;
exports.ROLE_ORDER = {
    READER: 1,
    EDITOR: 2,
    ADMIN: 3,
};
function roleMeetsMinimum(role, minimum) {
    return exports.ROLE_ORDER[role] >= exports.ROLE_ORDER[minimum];
}
function parseRole(value) {
    if (value === "READER" || value === "EDITOR" || value === "ADMIN")
        return value;
    return null;
}
//# sourceMappingURL=role.js.map