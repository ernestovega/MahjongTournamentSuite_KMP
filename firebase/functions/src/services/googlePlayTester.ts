import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { auth, db } from "../firebase";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import { setAccountEmaId } from "./playerAccountsService";
import { createUserProfile, setGlobalUserRole, setUserPhoto } from "./usersService";

/**
 * The account of the Google Play reviewers (docs/google-play-tester.md). Google needs an account with access to every
 * feature of the app. An admin reaches everything, so the account has no EMA player. The password is not here: it
 * lives in Play Console (App content > App access).
 */
export const GOOGLE_PLAY_TESTER = {
  email: "mtsgoogleplaytester@mahjongmadrid.com",
  alias: "Google Play Testers",
  role: "SUPER_ADMIN" as const,
};

/** The photo of the account (the Play Console icon). The path works from lib/services and from src/services. */
const PHOTO_PATH = resolve(__dirname, "../../assets/google-play-tester.png");
const PHOTO_CONTENT_TYPE = "image/png";

function hasPhoto(document: FirebaseFirestore.DocumentSnapshot): boolean {
  const url = document.get("photoUrl");
  return typeof url === "string" && url !== "";
}

export type GooglePlayTesterReport = {
  account: "CREATE" | "MISSING_PASSWORD" | "UPDATE" | "OK";
  uid: string | null;
};

/**
 * Makes the account of the Google Play reviewers exist as GOOGLE_PLAY_TESTER says. It changes only what differs, and
 * only with [apply]. It creates a missing account only with a [password]. A photo that someone chose stays.
 */
export async function ensureGooglePlayTester(params: { apply: boolean; password?: string }): Promise<GooglePlayTesterReport> {
  const existing = await auth.getUserByEmail(GOOGLE_PLAY_TESTER.email).catch(() => null);
  if (existing == null) {
    if (!params.password) return { account: "MISSING_PASSWORD", uid: null };
    if (!params.apply) return { account: "CREATE", uid: null };
    const created = await auth.createUser({ email: GOOGLE_PLAY_TESTER.email, password: params.password, emailVerified: true });
    await configureAccount(created.uid);
    return { account: "CREATE", uid: created.uid };
  }

  const profile = await db.doc(`users/${existing.uid}`).get();
  const accountOk = profile.get("alias") === GOOGLE_PLAY_TESTER.alias &&
    (existing.customClaims ?? {}).superAdmin === true &&
    !(typeof profile.get("emaId") === "string" && profile.get("emaId") !== "") &&
    hasPhoto(profile);
  if (!accountOk && params.apply) await configureAccount(existing.uid);
  return { account: accountOk ? "OK" : "UPDATE", uid: existing.uid };
}

async function configureAccount(uid: string): Promise<void> {
  await createUserProfile(uid, GOOGLE_PLAY_TESTER.email, GOOGLE_PLAY_TESTER.alias);
  await setGlobalUserRole(uid, GOOGLE_PLAY_TESTER.role);
  await setAccountEmaId({ uid, emaId: null });
  if (!hasPhoto(await db.doc(`users/${uid}`).get())) {
    await setUserPhoto(uid, PHOTO_CONTENT_TYPE, readFileSync(PHOTO_PATH).toString("base64"));
  }
  await bumpGlobalDataVersion("users");
}
