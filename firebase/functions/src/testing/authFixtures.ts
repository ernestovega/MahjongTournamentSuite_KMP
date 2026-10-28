import { randomUUID } from "node:crypto";
import type { AddressInfo } from "node:net";
import http from "node:http";

import { auth, db } from "../firebase";
import { buildApp } from "../api/app";
import { createUserProfile, setGlobalUserRole } from "../services/usersService";
import { upsertTournamentMember } from "../services/membersService";
import { listTournamentPlayers } from "../services/tournamentContentService";

/** Role tests need the Auth emulator next to the Firestore emulator. `npm run test:integration` starts both. */
export function requireAuthEmulator(): void {
  if (!process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Run these tests with the Auth and Firestore emulators: npm run test:integration");
  }
}

const PASSWORD = "Passw0rd!-test";

export type TestAccount = { uid: string; email: string; token: string };

/** A random EMA id. The emulator keeps its data between test files, so a counter would repeat in each file. */
const randomEmaId = () => String(10_000_000 + Math.floor(Math.random() * 89_000_000));
let nextTestSlotId = 1000;

/** Makes the account the owner of an EMA id, as the server does: the profile and the index document. */
export async function giveEmaIdToAccount(uid: string, emaId: string): Promise<void> {
  await db.doc(`users/${uid}`).set({ emaId }, { merge: true });
  await db.doc(`emaIdIndex/${emaId}`).set({ uid });
}

/** Puts the EMA id in a slot of the tournament, with its assignment document. */
export async function seatEmaId(tournamentId: string, playerId: number, emaId: string): Promise<void> {
  await db.doc(`tournaments/${tournamentId}/players/${playerId}`).set({ id: playerId, team: 0, assignedEmaId: emaId }, { merge: true });
  await db.doc(`tournaments/${tournamentId}/emaPlayerAssignments/${emaId}`).set({ playerId });
}

/** The account that the tournament list shows for a slot. It is the account that owns the EMA id of the slot. */
export async function linkedAccountUid(tournamentId: string, playerId: number): Promise<string | null> {
  const players = await listTournamentPlayers(tournamentId);
  return players.find((player) => player.id === playerId)?.accountUid ?? null;
}

/** Creates an account with the real profile, claim and membership documents, and signs it in. */
export async function createAccount(params: {
  role: "ADMIN" | "EDITOR" | "PLAYER";
  memberOf?: string[];
  /** Tournaments where a slot holds the EMA id of the player account, so the account plays there. */
  playerOf?: string[];
  disabled?: boolean;
}): Promise<TestAccount> {
  const email = `${randomUUID()}@example.test`;
  const user = await auth.createUser({ email, password: PASSWORD, emailVerified: true });
  await createUserProfile(user.uid, email, "Test");
  if (params.role === "PLAYER") await auth.setCustomUserClaims(user.uid, { player: true });
  else await setGlobalUserRole(user.uid, params.role);
  for (const tournamentId of params.memberOf ?? []) await upsertTournamentMember({ tournamentId, uid: user.uid });
  if ((params.playerOf ?? []).length > 0) {
    const emaId = randomEmaId();
    await giveEmaIdToAccount(user.uid, emaId);
    for (const tournamentId of params.playerOf ?? []) await seatEmaId(tournamentId, nextTestSlotId++, emaId);
  }
  // The token carries the claims, so sign in after they are set.
  const token = await signInTestAccount(email);
  if (params.disabled) await auth.updateUser(user.uid, { disabled: true });
  return { uid: user.uid, email, token };
}

/** Signs in a test account again, for example after its tokens were revoked. */
export async function signInTestAccount(email: string): Promise<string> {
  const response = await fetch(
    `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=test`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    },
  );
  const body = await response.json() as { idToken?: string };
  if (!body.idToken) throw new Error(`Sign-in failed: ${JSON.stringify(body)}`);
  return body.idToken;
}

export type TestServer = {
  baseUrl: string;
  close(): Promise<void>;
  request(method: string, path: string, options?: { token?: string | null; body?: unknown; headers?: Record<string, string> }): Promise<{ status: number; body: unknown }>;
};

/** Runs the real Express app on a free port. */
export async function startServer(): Promise<TestServer> {
  const server = http.createServer(buildApp());
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  return {
    baseUrl,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
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
      let body: unknown = text;
      try {
        body = JSON.parse(text);
      } catch {
        // Not JSON, for example an Excel file.
      }
      return { status: response.status, body };
    },
  };
}
