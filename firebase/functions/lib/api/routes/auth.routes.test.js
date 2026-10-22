"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const auth_routes_1 = require("./auth.routes");
(0, node_test_1.describe)("signInError", () => {
    (0, node_test_1.it)("answers 401 for a wrong password or an unknown email", () => {
        for (const code of ["INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND"]) {
            const error = (0, auth_routes_1.signInError)(new Error(code));
            strict_1.default.equal(error.status, 401);
            strict_1.default.equal(error.message, "INVALID_LOGIN_CREDENTIALS");
        }
    });
    (0, node_test_1.it)("answers 400 for an email that is not valid", () => {
        const error = (0, auth_routes_1.signInError)(new Error("INVALID_EMAIL"));
        strict_1.default.equal(error.status, 400);
    });
    (0, node_test_1.it)("answers 403 for a disabled account, with the words that the apps look for", () => {
        const error = (0, auth_routes_1.signInError)(new Error("USER_DISABLED"));
        strict_1.default.equal(error.status, 403);
        strict_1.default.match(error.message, /account is disabled/);
    });
    (0, node_test_1.it)("answers 429 when Firebase blocks too many attempts", () => {
        const error = (0, auth_routes_1.signInError)(new Error("TOO_MANY_ATTEMPTS_TRY_LATER : Access to this account has been temporarily disabled"));
        strict_1.default.equal(error.status, 429);
    });
    (0, node_test_1.it)("keeps other errors unchanged", () => {
        const original = new Error("socket hang up");
        strict_1.default.equal((0, auth_routes_1.signInError)(original), original);
    });
});
//# sourceMappingURL=auth.routes.test.js.map