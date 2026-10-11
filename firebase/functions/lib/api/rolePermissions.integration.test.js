"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const requireAdmin_1 = require("./middleware/requireAdmin");
const requireAuth_1 = require("./middleware/requireAuth");
const requireTournamentEditor_1 = require("./middleware/requireTournamentEditor");
const app_1 = require("./app");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const authFixtures_1 = require("../testing/authFixtures");
const MATRIX = [
    ["GET", "/health", "PUBLIC"],
    ["GET", "/version", "PUBLIC"],
    ["POST", "/auth/signIn", "PUBLIC"],
    ["POST", "/auth/passwordReset", "PUBLIC"],
    ["POST", "/auth/refresh", "PUBLIC"],
    ["POST", "/admin/bootstrapAdmin", "PUBLIC"],
    ["GET", "/auth/me", "SIGNED_IN"],
    ["GET", "/countries/", "SIGNED_IN"],
    ["GET", "/ema-player-registry/", "SIGNED_IN"],
    ["GET", "/admin/whoami", "SIGNED_IN"],
    ["GET", "/admin/users", "SIGNED_IN"],
    ["POST", "/admin/users", "SIGNED_IN"],
    ["PUT", "/admin/users/:uid", "SIGNED_IN"],
    ["GET", "/admin/users/lookup", "SIGNED_IN"],
    ["GET", "/sync/manifest", "SIGNED_IN"],
    ["GET", "/tournaments/", "SIGNED_IN"],
    ["POST", "/tournaments/id-card-proof", "SIGNED_IN"],
    ["POST", "/tournaments/id-card-preview", "SIGNED_IN"],
    ["POST", "/ema-player-registry/", "ADMIN"],
    ["PUT", "/ema-player-registry/:emaId", "ADMIN"],
    ["PUT", "/ema-player-registry/:emaId/photo", "ADMIN"],
    ["PUT", "/admin/users/:uid/disabled", "ADMIN"],
    ["POST", "/tournaments/", "ADMIN"],
    ["PUT", "/tournaments/:tournamentId/settings", "ADMIN"],
    ["DELETE", "/tournaments/:tournamentId", "ADMIN"],
    ["PUT", "/tournaments/:tournamentId", "ADMIN"],
    ["POST", "/tournaments/:tournamentId/tables/:roundId/:tableId/reset", "ADMIN"],
    ["GET", "/tournaments/:tournamentId/sync/manifest", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/id-cards", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/id-list", "MEMBER"],
    ["POST", "/tournaments/:tournamentId/ema-report", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/users/lookup", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/members", "MEMBER"],
    ["PUT", "/tournaments/:tournamentId/members/:uid", "MEMBER"],
    ["DELETE", "/tournaments/:tournamentId/members/:uid", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/players", "MEMBER"],
    ["PUT", "/tournaments/:tournamentId/players/:playerId", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/teams", "MEMBER"],
    ["PUT", "/tournaments/:tournamentId/teams/:teamId", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/rounds", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/tables", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/tables-with-hands", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", "MEMBER"],
    ["GET", "/tournaments/:tournamentId/tables/:roundId/:tableId", "MEMBER"],
    ["PUT", "/tournaments/:tournamentId/tables/:roundId/:tableId/state", "MEMBER"],
    ["GET", "/tournaments/:tournamentId", "MEMBER"],
];
function actualGuards() {
    const app = (0, app_1.buildApp)();
    const result = new Map();
    const walk = (layer, prefix) => {
        if (layer.route) {
            const handlers = layer.route.stack.map((item) => item.handle);
            const access = handlers.includes(requireAdmin_1.requireAdmin) ? "ADMIN"
                : handlers.includes(requireTournamentEditor_1.requireTournamentEditor) ? "MEMBER"
                    : handlers.includes(requireAuth_1.requireAuth) ? "SIGNED_IN"
                        : "PUBLIC";
            for (const method of Object.keys(layer.route.methods))
                result.set(`${method.toUpperCase()} ${prefix}${layer.route.path}`, access);
        }
        else if (layer.name === "router") {
            const match = layer.regexp.source.match(/^\^\\\/(\w[\w-]*)/);
            layer.handle.stack.forEach((inner) => walk(inner, prefix + (match ? `/${match[1]}` : "")));
        }
    };
    app._router.stack.forEach((layer) => walk(layer, ""));
    return result;
}
const denied = (status) => status === 401 || status === 403;
(0, node_test_1.describe)("who may call which route", () => {
    let server;
    let t1 = "";
    let t2 = "";
    let admin;
    let member;
    let outsider;
    const concrete = (path, tournamentId) => path
        .replace(":tournamentId", tournamentId)
        .replace(":roundId", "1").replace(":tableId", "1").replace(":playerId", "1").replace(":teamId", "1")
        .replace(":uid", "unknown-user").replace(":emaId", "unknown-ema");
    (0, node_test_1.before)(async () => {
        (0, authFixtures_1.requireAuthEmulator)();
        server = await (0, authFixtures_1.startServer)();
        t1 = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        t2 = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        admin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
        member = await (0, authFixtures_1.createAccount)({ role: "EDITOR", memberOf: [t1] });
        outsider = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
    });
    (0, node_test_1.after)(async () => { await server.close(); });
    (0, node_test_1.it)("lists every route of the app with its intended access", () => {
        const actual = actualGuards();
        const expected = new Map(MATRIX.map(([method, path, access]) => [`${method} ${path}`, access]));
        strict_1.default.deepEqual([...actual.entries()].sort(), [...expected.entries()].sort());
    });
    for (const [method, path, access] of MATRIX.filter(([, , a]) => a !== "PUBLIC")) {
        (0, node_test_1.describe)(`${method} ${path} (${access})`, () => {
            (0, node_test_1.it)("refuses a request without a token", async () => {
                strict_1.default.equal((await server.request(method, concrete(path, t1))).status, 401);
            });
            (0, node_test_1.it)("refuses a token that is not valid", async () => {
                strict_1.default.equal((await server.request(method, concrete(path, t1), { token: "not-a-token" })).status, 401);
            });
            if (access === "SIGNED_IN") {
                (0, node_test_1.it)("lets any signed-in editor and admin through", async () => {
                    for (const account of [outsider, member, admin]) {
                        const { status } = await server.request(method, concrete(path, t1), { token: account.token });
                        strict_1.default.ok(!denied(status), `${status} for ${account.email}`);
                    }
                });
            }
            if (access === "MEMBER") {
                (0, node_test_1.it)("refuses an editor who is not assigned to the tournament", async () => {
                    strict_1.default.equal((await server.request(method, concrete(path, t1), { token: outsider.token })).status, 403);
                });
                (0, node_test_1.it)("lets an assigned editor into the assigned tournament", async () => {
                    const { status } = await server.request(method, concrete(path, t1), { token: member.token });
                    strict_1.default.ok(!denied(status), `status ${status}`);
                });
                (0, node_test_1.it)("refuses an assigned editor in another tournament", async () => {
                    strict_1.default.equal((await server.request(method, concrete(path, t2), { token: member.token })).status, 403);
                });
                (0, node_test_1.it)("lets an admin into any tournament without an assignment", async () => {
                    const { status } = await server.request(method, concrete(path, "probe-tournament"), { token: admin.token });
                    strict_1.default.ok(!denied(status), `status ${status}`);
                });
            }
            if (access === "ADMIN") {
                (0, node_test_1.it)("refuses an editor, also an assigned one", async () => {
                    for (const account of [outsider, member]) {
                        strict_1.default.equal((await server.request(method, concrete(path, t1), { token: account.token })).status, 403);
                    }
                });
                (0, node_test_1.it)("lets an admin through the guard", async () => {
                    const { status } = await server.request(method, concrete(path, "probe-tournament"), { token: admin.token });
                    strict_1.default.ok(!denied(status), `status ${status}`);
                });
            }
        });
    }
});
(0, node_test_1.describe)("account rules", () => {
    let server;
    let admin;
    let editor;
    let otherEditor;
    (0, node_test_1.before)(async () => {
        (0, authFixtures_1.requireAuthEmulator)();
        server = await (0, authFixtures_1.startServer)();
        admin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
        editor = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        otherEditor = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
    });
    (0, node_test_1.after)(async () => { await server.close(); });
    const update = (token, uid, email, role) => server.request("PUT", `/admin/users/${uid}`, { token, body: { email, alias: "x", role, tournamentAssignments: [] } });
    (0, node_test_1.it)("whoami tells the role from the token claim", async () => {
        strict_1.default.deepEqual((await server.request("GET", "/admin/whoami", { token: admin.token })).body, { uid: admin.uid, role: "ADMIN" });
        strict_1.default.deepEqual((await server.request("GET", "/admin/whoami", { token: editor.token })).body, { uid: editor.uid, role: "EDITOR" });
    });
    (0, node_test_1.it)("an editor cannot create an admin account", async () => {
        const { status } = await server.request("POST", "/admin/users", {
            token: editor.token, body: { email: "new-admin@example.test", role: "ADMIN", alias: "x" },
        });
        strict_1.default.equal(status, 403);
    });
    (0, node_test_1.it)("an editor cannot change an admin account", async () => {
        strict_1.default.equal((await update(editor.token, admin.uid, admin.email, "ADMIN")).status, 403);
    });
    (0, node_test_1.it)("an editor cannot promote an editor to admin", async () => {
        strict_1.default.equal((await update(editor.token, otherEditor.uid, otherEditor.email, "ADMIN")).status, 403);
    });
    (0, node_test_1.it)("an editor can edit another editor account", async () => {
        strict_1.default.equal((await update(editor.token, otherEditor.uid, otherEditor.email, "EDITOR")).status, 200);
    });
    (0, node_test_1.it)("an admin can promote an editor to admin", async () => {
        const promoted = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        strict_1.default.equal((await update(admin.token, promoted.uid, promoted.email, "ADMIN")).status, 200);
        strict_1.default.deepEqual((await firebase_1.auth.getUser(promoted.uid)).customClaims, { admin: true });
    });
    (0, node_test_1.it)("nobody can change their own role", async () => {
        const extraAdmin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
        strict_1.default.equal((await update(extraAdmin.token, extraAdmin.uid, extraAdmin.email, "EDITOR")).status, 400);
    });
    (0, node_test_1.it)("an editor cannot disable an account", async () => {
        strict_1.default.equal((await server.request("PUT", `/admin/users/${otherEditor.uid}/disabled`, { token: editor.token, body: { disabled: true } })).status, 403);
    });
    (0, node_test_1.it)("an admin must send a boolean to disable an account", async () => {
        strict_1.default.equal((await server.request("PUT", `/admin/users/${otherEditor.uid}/disabled`, { token: admin.token, body: { disabled: "yes" } })).status, 400);
    });
    (0, node_test_1.it)("an admin cannot disable their own account", async () => {
        const extraAdmin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
        strict_1.default.equal((await server.request("PUT", `/admin/users/${extraAdmin.uid}/disabled`, { token: extraAdmin.token, body: { disabled: true } })).status, 400);
    });
    (0, node_test_1.it)("an admin can disable an editor, and that editor can no longer sign in to the API", async () => {
        const victim = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        const { status } = await server.request("PUT", `/admin/users/${victim.uid}/disabled`, { token: admin.token, body: { disabled: true } });
        strict_1.default.equal(status, 200);
        strict_1.default.equal((await server.request("GET", "/auth/me", { token: victim.token })).status, 401);
    });
    (0, node_test_1.it)("a disabled account is refused on its first call", async () => {
        const disabled = await (0, authFixtures_1.createAccount)({ role: "EDITOR", disabled: true });
        strict_1.default.equal((await server.request("GET", "/admin/whoami", { token: disabled.token })).status, 401);
    });
    (0, node_test_1.it)("the last enabled admin cannot be demoted or disabled", async () => {
        const others = (await firebase_1.auth.listUsers(1000)).users.filter((user) => user.customClaims?.admin === true && !user.disabled && user.uid !== admin.uid);
        try {
            for (const user of others)
                await firebase_1.auth.updateUser(user.uid, { disabled: true });
            const demote = await update(admin.token, admin.uid, admin.email, "ADMIN");
            strict_1.default.equal(demote.status, 200, "keeping the role is fine");
            // A second admin tries to disable the only other enabled admin. Only the first remains after the others are off.
            const helper = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
            const disableHelper = await server.request("PUT", `/admin/users/${admin.uid}/disabled`, { token: helper.token, body: { disabled: true } });
            strict_1.default.equal(disableHelper.status, 200, "two enabled admins: one may be disabled");
            await firebase_1.auth.updateUser(admin.uid, { disabled: false });
            await firebase_1.auth.updateUser(helper.uid, { disabled: true });
            const last = await server.request("PUT", `/admin/users/${admin.uid}/disabled`, { token: admin.token, body: { disabled: true } });
            strict_1.default.equal(last.status, 400);
        }
        finally {
            for (const user of others)
                await firebase_1.auth.updateUser(user.uid, { disabled: false });
        }
    });
    (0, node_test_1.it)("the bootstrap route needs the right key and works only without an enabled admin", async () => {
        process.env.MTS_BOOTSTRAP_KEY = "secret-key";
        strict_1.default.equal((await server.request("POST", "/admin/bootstrapAdmin", { body: { uid: editor.uid } })).status, 403);
        strict_1.default.equal((await server.request("POST", "/admin/bootstrapAdmin", { body: { uid: editor.uid }, headers: { "x-bootstrap-key": "wrong" } })).status, 403);
        // The right key is still refused, because an enabled admin exists.
        strict_1.default.equal((await server.request("POST", "/admin/bootstrapAdmin", { body: { uid: editor.uid }, headers: { "x-bootstrap-key": "secret-key" } })).status, 403);
    });
});
(0, node_test_1.describe)("tournament rules for assigned editors", () => {
    let server;
    let t1 = "";
    let member;
    let admin;
    (0, node_test_1.before)(async () => {
        (0, authFixtures_1.requireAuthEmulator)();
        server = await (0, authFixtures_1.startServer)();
        t1 = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        member = await (0, authFixtures_1.createAccount)({ role: "EDITOR", memberOf: [t1] });
        admin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
    });
    (0, node_test_1.after)(async () => { await server.close(); });
    (0, node_test_1.it)("an assigned editor can add another editor to the tournament", async () => {
        const colleague = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        const { status } = await server.request("PUT", `/tournaments/${t1}/members/${colleague.uid}`, { token: member.token });
        strict_1.default.equal(status, 200);
        strict_1.default.equal((await server.request("GET", `/tournaments/${t1}/players`, { token: colleague.token })).status, 200);
    });
    (0, node_test_1.it)("an admin account cannot be added as a member, because admins already see everything", async () => {
        const { status } = await server.request("PUT", `/tournaments/${t1}/members/${admin.uid}`, { token: member.token });
        strict_1.default.equal(status, 400);
    });
    (0, node_test_1.it)("an assigned editor can save a table, an outsider cannot", async () => {
        const outsider = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        const path = `/tournaments/${t1}/tables/1/1/state`;
        const body = { expectedVersion: 0, tablePatch: { playerEastId: "1" }, handPatches: [] };
        strict_1.default.equal((await server.request("PUT", path, { token: outsider.token, body })).status, 403);
        strict_1.default.equal((await server.request("PUT", path, { token: member.token, body })).status, 200);
    });
    (0, node_test_1.it)("only an admin can reset the table that an editor saved", async () => {
        const path = `/tournaments/${t1}/tables/1/1/reset`;
        strict_1.default.equal((await server.request("POST", path, { token: member.token })).status, 403);
        strict_1.default.equal((await server.request("POST", path, { token: admin.token })).status, 200);
    });
    (0, node_test_1.it)("the change log names the editor who saved", async () => {
        const t = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const editor = await (0, authFixtures_1.createAccount)({ role: "EDITOR", memberOf: [t] });
        await server.request("PUT", `/tournaments/${t}/tables/1/1/state`, {
            token: editor.token, body: { expectedVersion: 0, tablePatch: { playerEastId: "1" }, handPatches: [] },
        });
        const { db } = await Promise.resolve().then(() => __importStar(require("../firebase")));
        const log = await db.collection("tournaments").doc(t).collection("changes").get();
        strict_1.default.equal(log.docs[0].get("uid"), editor.uid);
    });
    (0, node_test_1.it)("a request body without a version is refused before anything is written", async () => {
        const t = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const editor = await (0, authFixtures_1.createAccount)({ role: "EDITOR", memberOf: [t] });
        const { status } = await server.request("PUT", `/tournaments/${t}/tables/1/1/state`, {
            token: editor.token, body: { expectedVersion: null, tablePatch: { playerEastId: "1" } },
        });
        strict_1.default.equal(status, 400);
    });
});
//# sourceMappingURL=rolePermissions.integration.test.js.map