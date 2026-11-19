import { FieldValue } from "firebase-admin/firestore";

import { auth, db, firebaseProjectId } from "../firebase";
import { bumpGlobalDataVersion } from "../services/dataVersionsService";
import { sendPasswordResetEmail } from "../services/firebaseAuthRest";
import { createUserProfile, setGlobalUserRole } from "../services/usersService";
import { normalizePlayerNamePart } from "../services/playerName";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";
import { runScript } from "./scriptRun";
import { STAGING_PLAYERS } from "./stagingPlayers";

/**
 * Prepares the staging project for a rehearsal (docs/staging.md):
 * - the admin account of STAGING_ADMIN_EMAIL. A new account gets no password, only the password reset email.
 * - the EMA players of stagingPlayers.ts that are not created by hand.
 *
 * It previews by default. Add --apply to write. It refuses to run on any project but `--project=<id>`, and never on
 * the production project.
 */
const PRODUCTION_PROJECT = "mahjong-tournament-suite";
const applyChanges = process.argv.includes("--apply");
const expectedProject = process.argv.find((arg) => arg.startsWith("--project="))?.slice("--project=".length) ?? "";

runScript("seedStaging", async () => {
  if (firebaseProjectId === PRODUCTION_PROJECT || expectedProject === PRODUCTION_PROJECT) {
    throw new Error("seedStaging never runs on the production project.");
  }
  if (expectedProject === "" || expectedProject !== firebaseProjectId) {
    throw new Error(`Pass --project=${firebaseProjectId} to confirm the project.`);
  }

  const adminEmail = (process.env.STAGING_ADMIN_EMAIL ?? "").trim().toLowerCase();
  if (!adminEmail) throw new Error("Set STAGING_ADMIN_EMAIL to the email of the admin.");
  const mode = applyChanges ? "" : " (preview)";

  let adminAccount = "OK";
  const existing = await auth.getUserByEmail(adminEmail).catch(() => null);
  if (existing == null) {
    adminAccount = "CREATE";
    if (applyChanges) {
      const created = await auth.createUser({ email: adminEmail, emailVerified: true });
      await createUserProfile(created.uid, adminEmail);
      await setGlobalUserRole(created.uid, "SUPER_ADMIN");
      await sendPasswordResetEmail(adminEmail);
      await bumpGlobalDataVersion("users");
    }
  } else if ((existing.customClaims ?? {}).superAdmin !== true) {
    adminAccount = "MAKE_ADMIN";
    if (applyChanges) {
      await setGlobalUserRole(existing.uid, "SUPER_ADMIN");
      await bumpGlobalDataVersion("users");
    }
  }
  console.log(`Admin account: ${adminAccount}${mode}`);

  const seeded = STAGING_PLAYERS.filter((player) => !player.byHand);
  let added = 0;
  for (const player of seeded) {
    const ref = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(player.emaId);
    if ((await ref.get()).exists) continue;
    added += 1;
    if (applyChanges) {
      await ref.create({
        firstName: normalizePlayerNamePart(player.firstName),
        lastName: normalizePlayerNamePart(player.lastName),
        country: player.country,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  }
  if (applyChanges && added > 0) await bumpGlobalDataVersion("emaPlayers");
  console.log(`EMA players to add: ${added} of ${seeded.length}${mode}. By hand in Player base: ${
    STAGING_PLAYERS.filter((player) => player.byHand).map((player) => player.emaId).join(", ")}`);

  return { project: firebaseProjectId, adminAccount, playersAdded: added };
}, applyChanges);
