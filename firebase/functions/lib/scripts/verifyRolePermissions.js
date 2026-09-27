"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const node_crypto_1 = require("node:crypto");
const app_1 = require("firebase-admin/app");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
function argumentValue(name) {
    const inlinePrefix = `--${name}=`;
    const inline = process.argv.find((argument) => argument.startsWith(inlinePrefix));
    if (inline)
        return inline.slice(inlinePrefix.length).trim();
    const index = process.argv.indexOf(`--${name}`);
    const next = index >= 0 ? process.argv[index + 1] : undefined;
    return next && !next.startsWith("--") ? next.trim() : null;
}
function requiredArgument(name) {
    const value = argumentValue(name);
    if (!value)
        throw new Error(`Missing required argument --${name}`);
    return value;
}
function parseOptions() {
    const apiKey = process.env.MTS_ID_TOOLKIT_API_KEY?.trim();
    if (!apiKey)
        throw new Error("Missing MTS_ID_TOOLKIT_API_KEY");
    return {
        projectId: requiredArgument("project"),
        adminEmail: requiredArgument("admin-email").toLowerCase(),
        apiBaseUrl: requiredArgument("api-base-url").replace(/\/$/, ""),
        apiKey,
    };
}
function assertCondition(condition, message) {
    if (!condition)
        throw new Error(message);
}
async function idTokenFor(email, password, options) {
    const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${encodeURIComponent(options.apiKey)}`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email, password, returnSecureToken: true }),
    });
    const body = await response.json();
    if (!response.ok || !body.idToken) {
        throw new Error(`Test sign-in failed: ${body.error?.message ?? response.status}`);
    }
    return body.idToken;
}
async function apiRequest(options, token, path, method = "GET", body) {
    const response = await fetch(`${options.apiBaseUrl}${path}`, {
        method,
        headers: {
            authorization: `Bearer ${token}`,
            ...(body === undefined ? {} : { "content-type": "application/json" }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let parsed = text;
    try {
        parsed = text ? JSON.parse(text) : null;
    }
    catch {
        // Keep non-JSON text for failure diagnostics.
    }
    return { status: response.status, body: parsed };
}
function expectStatus(response, expected, label) {
    if (response.status !== expected) {
        throw new Error(`${label}: expected HTTP ${expected}, received ${response.status}: ${JSON.stringify(response.body)}`);
    }
    console.log(`PASS ${label}`);
}
async function main() {
    const options = parseOptions();
    const app = (0, app_1.initializeApp)({ credential: (0, app_1.applicationDefault)(), projectId: options.projectId }, `verify-roles-${Date.now()}`);
    const auth = (0, auth_1.getAuth)(app);
    const db = (0, firestore_1.getFirestore)(app);
    const admin = await auth.getUserByEmail(options.adminEmail);
    assertCondition(admin.customClaims?.admin === true, "Selected account does not have the admin claim");
    const timestamp = Date.now();
    const temporaryUsers = [];
    let testAdmin;
    let editorOne;
    let editorTwo;
    let tournamentId = null;
    try {
        const testAdminPassword = (0, node_crypto_1.randomBytes)(24).toString("base64url");
        const editorOnePassword = (0, node_crypto_1.randomBytes)(24).toString("base64url");
        const editorTwoPassword = (0, node_crypto_1.randomBytes)(24).toString("base64url");
        testAdmin = await auth.createUser({
            email: `role-test-admin-${timestamp}@example.invalid`,
            password: testAdminPassword,
            displayName: "Role Test Admin",
            emailVerified: true,
        });
        await auth.setCustomUserClaims(testAdmin.uid, { admin: true });
        temporaryUsers.push(testAdmin);
        editorOne = await auth.createUser({
            email: `role-test-editor-one-${timestamp}@example.invalid`,
            password: editorOnePassword,
            displayName: "Role Test Editor One",
            emailVerified: true,
        });
        temporaryUsers.push(editorOne);
        editorTwo = await auth.createUser({
            email: `role-test-editor-two-${timestamp}@example.invalid`,
            password: editorTwoPassword,
            displayName: "Role Test Editor Two",
            emailVerified: true,
        });
        temporaryUsers.push(editorTwo);
        for (const user of temporaryUsers) {
            await db.doc(`users/${user.uid}`).set({
                uid: user.uid,
                email: user.email,
                alias: user.displayName,
                contactEmail: user.email,
                disabled: false,
                createdAt: firestore_1.FieldValue.serverTimestamp(),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
        const [adminToken, editorOneToken] = await Promise.all([
            idTokenFor(testAdmin.email, testAdminPassword, options),
            idTokenFor(editorOne.email, editorOnePassword, options),
        ]);
        const adminWhoAmI = await apiRequest(options, adminToken, "/admin/whoami");
        expectStatus(adminWhoAmI, 200, "ADMIN whoami");
        assertCondition(adminWhoAmI.body.role === "ADMIN", "ADMIN whoami returned the wrong role");
        const editorWhoAmI = await apiRequest(options, editorOneToken, "/admin/whoami");
        expectStatus(editorWhoAmI, 200, "EDITOR whoami");
        assertCondition(editorWhoAmI.body.role === "EDITOR", "EDITOR whoami returned the wrong role");
        const adminTournaments = await apiRequest(options, adminToken, "/tournaments");
        expectStatus(adminTournaments, 200, "ADMIN lists tournaments");
        const tournaments = adminTournaments.body.tournaments ?? [];
        tournamentId = tournaments.find((tournament) => tournament.id)?.id ?? null;
        assertCondition(tournamentId, "At least one tournament is required for assignment checks");
        expectStatus(await apiRequest(options, editorOneToken, "/tournaments"), 200, "unassigned EDITOR lists no inaccessible content");
        expectStatus(await apiRequest(options, adminToken, `/tournaments/${tournamentId}/members/${editorOne.uid}`, "PUT", { assigned: true }), 200, "ADMIN assigns an EDITOR");
        expectStatus(await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/players`), 200, "assigned EDITOR reads tournament content");
        expectStatus(await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/members/${editorTwo.uid}`, "PUT", { assigned: true }), 200, "assigned EDITOR assigns another EDITOR");
        expectStatus(await apiRequest(options, editorOneToken, "/admin/users"), 200, "EDITOR lists management accounts");
        expectStatus(await apiRequest(options, editorOneToken, "/tournaments", "POST", {}), 403, "EDITOR cannot create a tournament");
        expectStatus(await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}`, "PUT", { name: "Forbidden rename" }), 403, "EDITOR cannot configure a tournament");
        expectStatus(await apiRequest(options, editorOneToken, `/admin/users/${admin.uid}/disabled`, "PUT", { disabled: true }), 403, "EDITOR cannot disable accounts");
        expectStatus(await apiRequest(options, editorOneToken, "/admin/users", "POST", {
            email: `forbidden-admin-${timestamp}@example.invalid`,
            alias: "Forbidden Admin",
            role: "ADMIN",
            tournamentAssignments: [],
        }), 403, "EDITOR cannot create an ADMIN");
        expectStatus(await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/members/${admin.uid}`, "DELETE"), 400, "EDITOR cannot remove an implicit ADMIN");
        expectStatus(await apiRequest(options, adminToken, `/admin/users/${testAdmin.uid}/disabled`, "PUT", { disabled: true }), 400, "ADMIN cannot disable their own account");
        expectStatus(await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/members/${editorTwo.uid}`, "DELETE"), 200, "assigned EDITOR removes another EDITOR");
        console.log("Role permission verification passed.");
    }
    finally {
        if (tournamentId) {
            await Promise.allSettled(temporaryUsers.map((user) => (db.doc(`tournaments/${tournamentId}/members/${user.uid}`).delete())));
        }
        await Promise.allSettled(temporaryUsers.map((user) => db.doc(`users/${user.uid}`).delete()));
        await Promise.allSettled(temporaryUsers.map((user) => auth.deleteUser(user.uid)));
    }
}
main().catch((error) => {
    console.error("Role permission verification failed.", error);
    process.exitCode = 1;
});
//# sourceMappingURL=verifyRolePermissions.js.map