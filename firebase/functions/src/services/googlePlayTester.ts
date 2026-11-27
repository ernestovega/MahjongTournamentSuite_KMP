import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { auth, db } from "../firebase";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import { setAccountEmaId } from "./playerAccountsService";
import { getGlobalUserRole } from "../models/globalRole";
import { createUserProfile, setGlobalUserRole, setUserPhoto } from "./usersService";

/** An account of the reviewers of a store. The paths of the photos work from lib/services and from src/services. */
export type StoreTester = { email: string; alias: string; role: "SUPER_ADMIN" | "PLAYER"; photoPath: string };

/**
 * The account of the Google Play reviewers (docs/google-play-tester.md). Google needs an account with access to every
 * feature of the app. An admin reaches everything, so the account has no EMA player. The password is not here: it
 * lives in Play Console (App content > App access).
 */
export const GOOGLE_PLAY_TESTER: StoreTester = {
  email: "mtsgoogleplaytester@mahjongmadrid.com",
  alias: "PLAY STORE REVIEWER ACCOUNT",
  role: "SUPER_ADMIN",
  photoPath: resolve(__dirname, "../../assets/google-play-tester.png"),
};

/**
 * The account that the Google Play reviewers may delete to try the account deletion, so that GOOGLE_PLAY_TESTER stays.
 * A player with no EMA number. Publish to Google Play creates it again, only when it no longer exists, with the
 * password of the secret GOOGLE_PLAY_DISPOSABLE_TESTER_PASSWORD, the same one that is in Play Console (App access).
 */
export const GOOGLE_PLAY_DISPOSABLE_TESTER: StoreTester = {
  email: "mtsgoogleplaydisposable@mahjongmadrid.com",
  alias: "PLAY STORE REVIEWER DISPOSABLE ACCOUNT",
  role: "PLAYER",
  photoPath: resolve(__dirname, "../../assets/google-play-tester.png"),
};

/**
 * The account of the App Review of Apple (docs/google-play-tester.md), like the one of Google Play. Its password is
 * only in App Store Connect (App Review Information): the workflow Publish · App Store listing makes a new one each
 * run and puts it there.
 */
export const APP_STORE_TESTER: StoreTester = {
  email: "mtsappstoretester@mahjongmadrid.com",
  alias: "APP STORE REVIEWER ACCOUNT",
  role: "SUPER_ADMIN",
  photoPath: resolve(__dirname, "../../assets/app-store-tester.png"),
};

/**
 * The account that App Review may delete to try the account deletion (Guideline 5.1.1(v)), so that the account of
 * APP_STORE_TESTER stays. A player with no EMA number. The workflow Publish · App Store listing creates it again, only
 * when it no longer exists (always with the same email), and puts its email and password in the review notes.
 */
export const APP_STORE_DISPOSABLE_TESTER: StoreTester = {
  email: "mtsappstoredisposable@mahjongmadrid.com",
  alias: "APP STORE REVIEWER DISPOSABLE ACCOUNT",
  role: "PLAYER",
  photoPath: resolve(__dirname, "../../assets/app-store-tester.png"),
};

const PHOTO_CONTENT_TYPE = "image/png";

function hasPhoto(document: FirebaseFirestore.DocumentSnapshot): boolean {
  const url = document.get("photoUrl");
  return typeof url === "string" && url !== "";
}

export type GooglePlayTesterReport = {
  account: "CREATE" | "MISSING_PASSWORD" | "UPDATE" | "OK";
  uid: string | null;
  /** True when [setPassword] changed the password of an existing account. */
  passwordSet?: boolean;
};

/**
 * Makes the account of the reviewers of a store exist as [tester] says. It changes only what differs, and only with
 * [apply]. It creates a missing account only with a [password]. An existing account keeps its password, unless
 * [setPassword] asks to put [password] on it. A photo that someone chose stays.
 */
export async function ensureStoreTester(
  tester: StoreTester,
  params: { apply: boolean; password?: string; setPassword?: boolean },
): Promise<GooglePlayTesterReport> {
  const existing = await auth.getUserByEmail(tester.email).catch(() => null);
  if (existing == null) {
    if (!params.password) return { account: "MISSING_PASSWORD", uid: null };
    if (!params.apply) return { account: "CREATE", uid: null };
    const created = await auth.createUser({ email: tester.email, password: params.password, emailVerified: true });
    await configureAccount(tester, created.uid);
    return { account: "CREATE", uid: created.uid };
  }

  const profile = await db.doc(`users/${existing.uid}`).get();
  const accountOk = profile.get("alias") === tester.alias &&
    getGlobalUserRole(existing) === tester.role &&
    !(typeof profile.get("emaId") === "string" && profile.get("emaId") !== "") &&
    hasPhoto(profile);
  if (!accountOk && params.apply) await configureAccount(tester, existing.uid);
  const passwordSet = params.apply && params.setPassword === true && !!params.password;
  if (passwordSet) await auth.updateUser(existing.uid, { password: params.password, emailVerified: true });
  return { account: accountOk ? "OK" : "UPDATE", uid: existing.uid, passwordSet };
}

async function configureAccount(tester: StoreTester, uid: string): Promise<void> {
  await createUserProfile(uid, tester.email, tester.alias);
  await setGlobalUserRole(uid, tester.role);
  await setAccountEmaId({ uid, emaId: null });
  if (!hasPhoto(await db.doc(`users/${uid}`).get())) {
    await setUserPhoto(uid, PHOTO_CONTENT_TYPE, readFileSync(tester.photoPath).toString("base64"));
  }
  await bumpGlobalDataVersion("users");
}
