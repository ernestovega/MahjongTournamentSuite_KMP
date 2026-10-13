import { randomBytes } from "node:crypto";

import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth, type UserRecord } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

type Options = {
  projectId: string;
  adminEmail: string;
  apiBaseUrl: string;
  apiKey: string;
};

type ApiResponse = {
  status: number;
  body: unknown;
};

function argumentValue(name: string): string | null {
  const inlinePrefix = `--${name}=`;
  const inline = process.argv.find((argument) => argument.startsWith(inlinePrefix));
  if (inline) return inline.slice(inlinePrefix.length).trim();
  const index = process.argv.indexOf(`--${name}`);
  const next = index >= 0 ? process.argv[index + 1] : undefined;
  return next && !next.startsWith("--") ? next.trim() : null;
}

function requiredArgument(name: string): string {
  const value = argumentValue(name);
  if (!value) throw new Error(`Missing required argument --${name}`);
  return value;
}

function parseOptions(): Options {
  const apiKey = process.env.MTS_ID_TOOLKIT_API_KEY?.trim();
  if (!apiKey) throw new Error("Missing MTS_ID_TOOLKIT_API_KEY");
  return {
    projectId: requiredArgument("project"),
    adminEmail: requiredArgument("admin-email").toLowerCase(),
    apiBaseUrl: requiredArgument("api-base-url").replace(/\/$/, ""),
    apiKey,
  };
}

function assertCondition(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function idTokenFor(email: string, password: string, options: Options): Promise<string> {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${encodeURIComponent(options.apiKey)}`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  const body = await response.json() as { idToken?: string; error?: { message?: string } };
  if (!response.ok || !body.idToken) {
    throw new Error(`Test sign-in failed: ${body.error?.message ?? response.status}`);
  }
  return body.idToken;
}

async function apiRequest(
  options: Options,
  token: string,
  path: string,
  method = "GET",
  body?: unknown,
): Promise<ApiResponse> {
  const response = await fetch(`${options.apiBaseUrl}${path}`, {
    method,
    headers: {
      authorization: `Bearer ${token}`,
      ...(body === undefined ? {} : { "content-type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let parsed: unknown = text;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    // Keep non-JSON text for failure diagnostics.
  }
  return { status: response.status, body: parsed };
}

function expectStatus(response: ApiResponse, expected: number, label: string): void {
  if (response.status !== expected) {
    throw new Error(`${label}: expected HTTP ${expected}, received ${response.status}: ${JSON.stringify(response.body)}`);
  }
  console.log(`PASS ${label}`);
}

async function main(): Promise<void> {
  const options = parseOptions();
  const app = initializeApp(
    { credential: applicationDefault(), projectId: options.projectId },
    `verify-roles-${Date.now()}`,
  );
  const auth = getAuth(app);
  const db = getFirestore(app);
  const admin = await auth.getUserByEmail(options.adminEmail);
  assertCondition(admin.customClaims?.admin === true, "Selected account does not have the admin claim");

  const timestamp = Date.now();
  const temporaryUsers: UserRecord[] = [];
  let testAdmin: UserRecord;
  let editorOne: UserRecord;
  let editorTwo: UserRecord;
  let playerOne: UserRecord;
  let tournamentId: string | null = null;

  try {
    const testAdminPassword = randomBytes(24).toString("base64url");
    const editorOnePassword = randomBytes(24).toString("base64url");
    const editorTwoPassword = randomBytes(24).toString("base64url");
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
    await auth.setCustomUserClaims(editorOne.uid, { editor: true });
    temporaryUsers.push(editorOne);
    editorTwo = await auth.createUser({
      email: `role-test-editor-two-${timestamp}@example.invalid`,
      password: editorTwoPassword,
      displayName: "Role Test Editor Two",
      emailVerified: true,
    });
    await auth.setCustomUserClaims(editorTwo.uid, { editor: true });
    temporaryUsers.push(editorTwo);
    const playerOnePassword = randomBytes(24).toString("base64url");
    playerOne = await auth.createUser({
      email: `role-test-player-one-${timestamp}@example.invalid`,
      password: playerOnePassword,
      displayName: "Role Test Player One",
      emailVerified: true,
    });
    await auth.setCustomUserClaims(playerOne.uid, { player: true });
    temporaryUsers.push(playerOne);

    for (const user of temporaryUsers) {
      await db.doc(`users/${user.uid}`).set({
        uid: user.uid,
        email: user.email,
        alias: user.displayName,
        contactEmail: user.email,
        disabled: false,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    const [adminToken, editorOneToken, playerToken] = await Promise.all([
      idTokenFor(testAdmin.email as string, testAdminPassword, options),
      idTokenFor(editorOne.email as string, editorOnePassword, options),
      idTokenFor(playerOne.email as string, playerOnePassword, options),
    ]);

    const adminWhoAmI = await apiRequest(options, adminToken, "/admin/whoami");
    expectStatus(adminWhoAmI, 200, "ADMIN whoami");
    assertCondition(
      (adminWhoAmI.body as { role?: string }).role === "ADMIN",
      "ADMIN whoami returned the wrong role",
    );

    const editorWhoAmI = await apiRequest(options, editorOneToken, "/admin/whoami");
    expectStatus(editorWhoAmI, 200, "EDITOR whoami");
    assertCondition(
      (editorWhoAmI.body as { role?: string }).role === "EDITOR",
      "EDITOR whoami returned the wrong role",
    );

    for (const path of ["/admin/users", "/admin/users/lookup?identifier=a@b.c"]) {
      expectStatus(await apiRequest(options, playerToken, path), 403, `PLAYER is refused on GET ${path}`);
    }
    // Global revisions and countries hold no private data. Any signed-in account may read them.
    for (const path of ["/sync/manifest", "/countries"]) {
      expectStatus(await apiRequest(options, playerToken, path), 200, `PLAYER may read GET ${path}`);
    }
    // These lists are scoped to the tournaments of the player. A new player has none.
    const playerTournaments = await apiRequest(options, playerToken, "/tournaments");
    expectStatus(playerTournaments, 200, "PLAYER lists own tournaments");
    assertCondition(
      ((playerTournaments.body as { tournaments?: unknown[] }).tournaments ?? []).length === 0,
      "PLAYER without registrations must see no tournaments",
    );
    const playerRegistry = await apiRequest(options, playerToken, "/ema-player-registry");
    expectStatus(playerRegistry, 200, "PLAYER lists own registry scope");
    assertCondition(
      ((playerRegistry.body as { players?: unknown[] }).players ?? []).length === 0,
      "PLAYER without registrations must see no registry players",
    );
    expectStatus(
      await apiRequest(options, playerToken, "/admin/users", "POST", {
        email: `forbidden-editor-${timestamp}@example.invalid`,
        alias: "Forbidden Editor",
        role: "EDITOR",
        tournamentAssignments: [],
      }),
      403,
      "PLAYER cannot create an EDITOR",
    );

    const adminTournaments = await apiRequest(options, adminToken, "/tournaments");
    expectStatus(adminTournaments, 200, "ADMIN lists tournaments");
    const tournaments = (adminTournaments.body as { tournaments?: Array<{ id?: string }> }).tournaments ?? [];
    tournamentId = tournaments.find((tournament) => tournament.id)?.id ?? null;
    assertCondition(tournamentId, "At least one tournament is required for assignment checks");

    expectStatus(
      await apiRequest(options, editorOneToken, "/tournaments"),
      200,
      "unassigned EDITOR lists no inaccessible content",
    );
    expectStatus(
      await apiRequest(options, adminToken, `/tournaments/${tournamentId}/members/${editorOne.uid}`, "PUT", { assigned: true }),
      200,
      "ADMIN assigns an EDITOR",
    );

    expectStatus(
      await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/players`),
      200,
      "assigned EDITOR reads tournament content",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/members/${editorTwo.uid}`, "PUT", { assigned: true }),
      200,
      "assigned EDITOR assigns another EDITOR",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, "/admin/users"),
      200,
      "EDITOR lists management accounts",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, "/tournaments", "POST", {}),
      403,
      "EDITOR cannot create a tournament",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}`, "PUT", { name: "Forbidden rename" }),
      403,
      "EDITOR cannot configure a tournament",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, `/admin/users/${admin.uid}/disabled`, "PUT", { disabled: true }),
      403,
      "EDITOR cannot disable accounts",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, "/admin/users", "POST", {
        email: `forbidden-admin-${timestamp}@example.invalid`,
        alias: "Forbidden Admin",
        role: "ADMIN",
        tournamentAssignments: [],
      }),
      403,
      "EDITOR cannot create an ADMIN",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/members/${admin.uid}`, "DELETE"),
      400,
      "EDITOR cannot remove an implicit ADMIN",
    );
    expectStatus(
      await apiRequest(options, adminToken, `/admin/users/${testAdmin.uid}/disabled`, "PUT", { disabled: true }),
      400,
      "ADMIN cannot disable their own account",
    );
    expectStatus(
      await apiRequest(options, editorOneToken, `/tournaments/${tournamentId}/members/${editorTwo.uid}`, "DELETE"),
      200,
      "assigned EDITOR removes another EDITOR",
    );

    console.log("Role permission verification passed.");
  } finally {
    if (tournamentId) {
      await Promise.allSettled(temporaryUsers.map((user) => (
        db.doc(`tournaments/${tournamentId}/members/${user.uid}`).delete()
      )));
    }
    await Promise.allSettled(temporaryUsers.map((user) => db.doc(`users/${user.uid}`).delete()));
    await Promise.allSettled(temporaryUsers.map((user) => auth.deleteUser(user.uid)));
  }
}

main().catch((error: unknown) => {
  console.error("Role permission verification failed.", error);
  process.exitCode = 1;
});
