import { FieldValue } from "firebase-admin/firestore";

import { auth, db, firebaseProjectId } from "../firebase";
import { bumpGlobalDataVersion } from "../services/dataVersionsService";
import { createPlayerAccount, setAccountEmaId } from "../services/playerAccountsService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";
import { createUserProfile, deleteManagedUser, setGlobalUserRole } from "../services/usersService";
import { runScript } from "./scriptRun";

/**
 * The accounts of the bots and emulators of a rehearsal in staging (docs/rehearsal.md). Only for staging.
 *
 * --passwords: gives the rehearsal password to the accounts of the slots --from..--to (default 2..60). Ernesto
 *   creates them with the invitations of the tournament. They have no password until then, and the bots need one.
 * --calibration-setup: the accounts and EMA players of the calibration tournament (16 players, EMA 99980001-16) and
 *   the admin account of the bots, all with the rehearsal password. No email is sent.
 * --calibration-cleanup: removes the 16 calibration accounts and EMA players. Delete the calibration tournament first.
 *
 * Emails: the local part of STAGING_ADMIN_EMAIL plus "+mts-jNN", "+mts-calNN" or "+mts-botadmin". The password comes
 * from REHEARSAL_PASSWORD. It previews by default; add --apply to write. It needs --project=<id> and never runs on
 * production.
 */
const PRODUCTION_PROJECT = "mahjong-tournament-suite";
const CALIBRATION_PLAYERS = 16;
const CALIBRATION_FIRST_EMA = 99980001;

const args = process.argv.slice(2);
const applyChanges = args.includes("--apply");
const option = (name: string) => args.find((arg) => arg.startsWith(`--${name}=`))?.slice(name.length + 3) ?? "";

function rehearsalEmail(baseEmail: string, suffix: string): string {
  const [local, domain] = baseEmail.trim().toLowerCase().split("@");
  if (!local || !domain) throw new Error("STAGING_ADMIN_EMAIL is not an email");
  return `${local.split("+")[0]}+mts-${suffix}@${domain}`;
}

function slotSuffix(slot: number): string {
  return `j${String(slot).padStart(2, "0")}`;
}

function calibrationSuffix(index: number): string {
  return `cal${String(index).padStart(2, "0")}`;
}

async function userByEmail(email: string) {
  return auth.getUserByEmail(email).catch(() => null);
}

async function setPasswords(baseEmail: string, password: string, from: number, to: number) {
  let updated = 0;
  const missing: string[] = [];
  for (let slot = from; slot <= to; slot += 1) {
    const email = rehearsalEmail(baseEmail, slotSuffix(slot));
    const user = await userByEmail(email);
    if (user == null) {
      missing.push(slotSuffix(slot));
      continue;
    }
    if (applyChanges) await auth.updateUser(user.uid, { password, emailVerified: true });
    updated += 1;
  }
  console.log(`Passwords: ${updated} accounts${applyChanges ? "" : " (preview)"}. Without an account: ${missing.join(", ") || "none"}.`);
  return { updated, missing: missing.length };
}

async function setupCalibration(baseEmail: string, password: string) {
  let players = 0;
  let accounts = 0;
  for (let index = 1; index <= CALIBRATION_PLAYERS; index += 1) {
    const emaId = String(CALIBRATION_FIRST_EMA + index - 1);
    const ref = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId);
    if (!(await ref.get()).exists) {
      players += 1;
      if (applyChanges) {
        await ref.create({
          firstName: "Bot",
          lastName: `Calibración ${String(index).padStart(2, "0")}`,
          country: "ESP",
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    }
    const email = rehearsalEmail(baseEmail, calibrationSuffix(index));
    let user = await userByEmail(email);
    if (user == null) {
      accounts += 1;
      if (!applyChanges) continue;
      user = await auth.getUser(await createPlayerAccount({ email, emaId }));
    }
    if (applyChanges) await auth.updateUser(user.uid, { password, emailVerified: true });
  }
  if (applyChanges && players > 0) await bumpGlobalDataVersion("emaPlayers");

  const adminEmail = rehearsalEmail(baseEmail, "botadmin");
  let admin = await userByEmail(adminEmail);
  const adminState = admin == null ? "CREATE" : "OK";
  if (applyChanges) {
    if (admin == null) {
      admin = await auth.createUser({ email: adminEmail, emailVerified: true });
      await createUserProfile(admin.uid, adminEmail);
    }
    if ((admin.customClaims ?? {}).admin !== true) await setGlobalUserRole(admin.uid, "ADMIN");
    await auth.updateUser(admin.uid, { password, emailVerified: true });
    await bumpGlobalDataVersion("users");
  }
  console.log(`Calibration${applyChanges ? "" : " (preview)"}: ${players} new EMA players, ${accounts} new accounts, bot admin ${adminState}.`);
  return { players, accounts, admin: adminState };
}

async function cleanupCalibration(baseEmail: string) {
  let accounts = 0;
  let players = 0;
  for (let index = 1; index <= CALIBRATION_PLAYERS; index += 1) {
    const user = await userByEmail(rehearsalEmail(baseEmail, calibrationSuffix(index)));
    if (user != null) {
      accounts += 1;
      if (applyChanges) {
        await setAccountEmaId({ uid: user.uid, emaId: null });
        await deleteManagedUser(user.uid);
      }
    }
    const ref = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(String(CALIBRATION_FIRST_EMA + index - 1));
    if ((await ref.get()).exists) {
      players += 1;
      if (applyChanges) await ref.delete();
    }
  }
  if (applyChanges && players > 0) await bumpGlobalDataVersion("emaPlayers");
  console.log(`Calibration cleanup${applyChanges ? "" : " (preview)"}: ${accounts} accounts and ${players} EMA players.`);
  return { accounts, players };
}

runScript("rehearsalAccounts", async () => {
  const expectedProject = option("project");
  if (firebaseProjectId === PRODUCTION_PROJECT || expectedProject === PRODUCTION_PROJECT) {
    throw new Error("rehearsalAccounts never runs on the production project.");
  }
  if (expectedProject === "" || expectedProject !== firebaseProjectId) {
    throw new Error(`Pass --project=${firebaseProjectId} to confirm the project.`);
  }
  const baseEmail = process.env.STAGING_ADMIN_EMAIL ?? "";
  const password = process.env.REHEARSAL_PASSWORD ?? "";
  if (!baseEmail) throw new Error("Set STAGING_ADMIN_EMAIL.");
  if (args.includes("--calibration-cleanup")) return cleanupCalibration(baseEmail);
  if (password.length < 12) throw new Error("Set REHEARSAL_PASSWORD (12 characters or more).");
  if (args.includes("--calibration-setup")) return setupCalibration(baseEmail, password);
  if (args.includes("--passwords")) {
    return setPasswords(baseEmail, password, Number(option("from") || 2), Number(option("to") || 60));
  }
  throw new Error("Pass --passwords, --calibration-setup or --calibration-cleanup.");
}, applyChanges);
