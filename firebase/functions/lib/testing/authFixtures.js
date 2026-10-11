"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAuthEmulator = requireAuthEmulator;
exports.createAccount = createAccount;
exports.startServer = startServer;
const node_crypto_1 = require("node:crypto");
const node_http_1 = __importDefault(require("node:http"));
const firebase_1 = require("../firebase");
const app_1 = require("../api/app");
const usersService_1 = require("../services/usersService");
const membersService_1 = require("../services/membersService");
/** Role tests need the Auth emulator next to the Firestore emulator. `npm run test:integration` starts both. */
function requireAuthEmulator() {
    if (!process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIRESTORE_EMULATOR_HOST) {
        throw new Error("Run these tests with the Auth and Firestore emulators: npm run test:integration");
    }
}
const PASSWORD = "Passw0rd!-test";
/** Creates an account with the real profile, claim and membership documents, and signs it in. */
async function createAccount(params) {
    const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
    const user = await firebase_1.auth.createUser({ email, password: PASSWORD, emailVerified: true });
    await (0, usersService_1.createUserProfile)(user.uid, email, "Test");
    await (0, usersService_1.setGlobalUserRole)(user.uid, params.role);
    for (const tournamentId of params.memberOf ?? [])
        await (0, membersService_1.upsertTournamentMember)({ tournamentId, uid: user.uid });
    // The token carries the claims, so sign in after they are set.
    const response = await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=test`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    });
    const body = await response.json();
    if (!body.idToken)
        throw new Error(`Sign-in failed: ${JSON.stringify(body)}`);
    if (params.disabled)
        await firebase_1.auth.updateUser(user.uid, { disabled: true });
    return { uid: user.uid, email, token: body.idToken };
}
/** Runs the real Express app on a free port. */
async function startServer() {
    const server = node_http_1.default.createServer((0, app_1.buildApp)());
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    const baseUrl = `http://127.0.0.1:${server.address().port}`;
    return {
        baseUrl,
        close: () => new Promise((resolve) => server.close(() => resolve())),
        async request(method, path, options = {}) {
            const response = await fetch(baseUrl + path, {
                method,
                headers: {
                    "content-type": "application/json",
                    ...(options.token ? { authorization: `Bearer ${options.token}` } : {}),
                    ...options.headers,
                },
                body: method === "GET" || method === "DELETE" ? undefined : JSON.stringify(options.body ?? {}),
            });
            const text = await response.text();
            let body = text;
            try {
                body = JSON.parse(text);
            }
            catch {
                // Not JSON, for example an Excel file.
            }
            return { status: response.status, body };
        },
    };
}
//# sourceMappingURL=authFixtures.js.map