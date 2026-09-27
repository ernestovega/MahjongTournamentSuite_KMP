"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const globalRole_1 = require("./globalRole");
(0, node_test_1.default)("treats accounts without the admin claim as editors", () => {
    strict_1.default.equal((0, globalRole_1.getGlobalUserRole)({ customClaims: undefined }), "EDITOR");
    strict_1.default.equal((0, globalRole_1.getGlobalUserRole)({ customClaims: { admin: false } }), "EDITOR");
    strict_1.default.equal((0, globalRole_1.hasAdminClaim)(undefined), false);
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