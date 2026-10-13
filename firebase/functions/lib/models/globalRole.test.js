"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const globalRole_1 = require("./globalRole");
(0, node_test_1.default)("gives editor access only to admins and editors", () => {
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)(undefined), false);
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)({}), false);
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)({ admin: true }), true);
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)({ editor: true }), true);
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)({ player: true }), false);
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)({ player: true, editor: true }), true);
    strict_1.default.equal((0, globalRole_1.hasEditorAccess)({ player: true, admin: true }), true);
    strict_1.default.equal((0, globalRole_1.hasAdminClaim)(undefined), false);
});
(0, node_test_1.default)("lists every capability of an account", () => {
    strict_1.default.deepEqual((0, globalRole_1.getGlobalUserRoles)({ customClaims: undefined }), []);
    strict_1.default.deepEqual((0, globalRole_1.getGlobalUserRoles)({ customClaims: { admin: true } }), ["ADMIN"]);
    strict_1.default.deepEqual((0, globalRole_1.getGlobalUserRoles)({ customClaims: { player: true } }), ["PLAYER"]);
    strict_1.default.deepEqual((0, globalRole_1.getGlobalUserRoles)({ customClaims: { editor: true, player: true } }), ["EDITOR", "PLAYER"]);
    strict_1.default.deepEqual((0, globalRole_1.getGlobalUserRoles)({ customClaims: { admin: true, player: true } }), ["ADMIN", "PLAYER"]);
    strict_1.default.equal((0, globalRole_1.getGlobalUserRole)({ customClaims: { player: true } }), "PLAYER");
    strict_1.default.equal((0, globalRole_1.getGlobalUserRole)({ customClaims: { editor: true, player: true } }), "EDITOR");
});
(0, node_test_1.default)("recognizes only the new admin claim and role names", () => {
    strict_1.default.equal((0, globalRole_1.getGlobalUserRole)({ customClaims: { admin: true } }), "ADMIN");
    strict_1.default.equal((0, globalRole_1.hasAdminClaim)({ admin: true }), true);
    strict_1.default.equal((0, globalRole_1.parseGlobalUserRole)("EDITOR"), "EDITOR");
    strict_1.default.equal((0, globalRole_1.parseGlobalUserRole)("ADMIN"), "ADMIN");
    strict_1.default.equal((0, globalRole_1.parseGlobalUserRole)("SUPERADMIN"), null);
    strict_1.default.equal((0, globalRole_1.parseGlobalUserRole)("READER"), null);
});
//# sourceMappingURL=globalRole.test.js.map