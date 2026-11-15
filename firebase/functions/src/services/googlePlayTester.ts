import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import { setAccountEmaId } from "./playerAccountsService";
import { createUserProfile, setGlobalUserRole } from "./usersService";

/**
 * The account of the Google Play reviewers (docs/google-play-tester.md). Google needs an account with access to every
 * feature of the app. The password is not here: it lives in Play Console (App content > App access).
 */
export const GOOGLE_PLAY_TESTER = {
  email: "mtsgoogleplaytester@mahjongmadrid.com",
  alias: "Google Play Testers",
  role: "ADMIN" as const,
  emaPlayer: {
    emaId: "00000000",
    firstName: "Google Play",
    lastName: "Testers",
    country: "USA",
    hiddenFromPublic: true,
  },
};

export type GooglePlayTesterReport = {
  emaPlayer: "CREATE" | "UPDATE" | "OK";
  account: "CREATE" | "MISSING_PASSWORD" | "UPDATE" | "OK";
  uid: string | null;
};

/**
 * Makes the EMA player and the account of the Google Play reviewers exist as GOOGLE_PLAY_TESTER says. It changes only
 * what differs, and only with [apply]. It creates a missing account only with a [password].
 */
export async function ensureGooglePlayTester(params: { apply: boolean; password?: string }): Promise<GooglePlayTesterReport> {
  const { emaPlayer } = GOOGLE_PLAY_TESTER;
  const playerRef = db.doc(`emaPlayerRegistry/${emaPlayer.emaId}`);
  const player = await playerRef.get();
  const playerFields = {
    firstName: emaPlayer.firstName,
    lastName: emaPlayer.lastName,
    country: emaPlayer.country,
    hiddenFromPublic: emaPlayer.hiddenFromPublic,
  };
  const playerAction = !player.exists
    ? "CREATE"
    : Object.entries(playerFields).every(([key, value]) => player.get(key) === value) ? "OK" : "UPDATE";
  if (params.apply && playerAction !== "OK") {
    await playerRef.set({ ...playerFields, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    await bumpGlobalDataVersion("emaPlayers");
  }

  const existing = await auth.getUserByEmail(GOOGLE_PLAY_TESTER.email).catch(() => null);
  if (existing == null) {
    if (!params.password) return { emaPlayer: playerAction, account: "MISSING_PASSWORD", uid: null };
    if (!params.apply) return { emaPlayer: playerAction, account: "CREATE", uid: null };
    const created = await auth.createUser({ email: GOOGLE_PLAY_TESTER.email, password: params.password, emailVerified: true });
    await configureAccount(created.uid);
    return { emaPlayer: playerAction, account: "CREATE", uid: created.uid };
  }

  const profile = await db.doc(`users/${existing.uid}`).get();
  const claims = existing.customClaims ?? {};
  const accountOk = profile.get("alias") === GOOGLE_PLAY_TESTER.alias &&
    profile.get("emaId") === emaPlayer.emaId &&
    claims.admin === true;
  if (!accountOk && params.apply) await configureAccount(existing.uid);
  return { emaPlayer: playerAction, account: accountOk ? "OK" : "UPDATE", uid: existing.uid };
}

async function configureAccount(uid: string): Promise<void> {
  await createUserProfile(uid, GOOGLE_PLAY_TESTER.email, GOOGLE_PLAY_TESTER.alias);
  await setGlobalUserRole(uid, GOOGLE_PLAY_TESTER.role);
  await setAccountEmaId({ uid, emaId: GOOGLE_PLAY_TESTER.emaPlayer.emaId });
  await bumpGlobalDataVersion("users");
}
