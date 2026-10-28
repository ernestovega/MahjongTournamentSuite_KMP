import { spawnSync } from "node:child_process";
import http from "node:http";

// The secret value is read from the environment when the code runs outside Firebase.
process.env.MTS_ID_TOOLKIT_API_KEY ??= "e2e-key";
process.env.GCLOUD_PROJECT ??= "demo-mts-e2e";
process.env.EMA_FIREBASE_PROJECT ??= "demo-mts-e2e";

/**
 * Starts the real API on the local Auth and Firestore emulators for the instrumented UI tests.
 * It fills the emulators with an EMA registry, test accounts and the test-* tournaments.
 *
 * Run it with `npm run e2e:backend`. The Android emulator reaches it at http://10.0.2.2:5055/api.
 */
const PORT = Number(process.env.E2E_PORT ?? 5055);
const PASSWORD = "Passw0rd!-e2e";

/** The uid of the account that the seed script already uses as the editor of the live tournament. */
const SEED_EDITOR_UID = "RH9PioX5AvNDulLDVa2JX45SP7H3";
const SEED_ADMIN_UID = "h0Hgasv0Y1VBOXDVnAoSq1X8RMQ2";

export const E2E_ACCOUNTS = {
  admin: { uid: SEED_ADMIN_UID, email: "admin@e2e.test" },
  editor: { uid: SEED_EDITOR_UID, email: "editor@e2e.test" },
  outsider: { uid: "e2e-editor-outsider", email: "outsider@e2e.test" },
  disabled: { uid: "e2e-disabled", email: "disabled@e2e.test" },
  /** Sits at a table of the live tournament that has 11 hands. */
  playerActive: { uid: "e2e-player-active", email: "player@e2e.test" },
  /** Sits at a table of the live tournament that has seats and no hands. */
  playerSeated: { uid: "e2e-player-seated", email: "seated@e2e.test" },
  /** Has no seat yet in the running round of the live tournament. */
  playerWaiting: { uid: "e2e-player-waiting", email: "waiting@e2e.test" },
  /** Sits at a table of the live tournament that is finished. */
  playerDone: { uid: "e2e-player-done", email: "done@e2e.test" },
  /** Plays in no tournament. */
  playerStranger: { uid: "e2e-player-stranger", email: "stranger@e2e.test" },
  /** Sits at table 1 of test-match-extra-time. The match time is over and the extra time runs. The table has 7 hands. */
  playerExtraTime: { uid: "e2e-player-extra", email: "extra@e2e.test" },
  /** Sits at table 1 of test-match-over. The match time is over and the tables stopped. The table has 7 hands. */
  playerTimeOver: { uid: "e2e-player-over", email: "over@e2e.test" },
} as const;

/** The editor account works in these tournaments, next to the live one. */
const EDITOR_TOURNAMENTS = ["test-live-ongoing", "test-min-16", "test-incomplete-states", "test-last-table"];

const FIRST_NAMES = ["Ana", "Luis", "Marta", "Jorge", "Elena", "Pablo", "Lucia", "Diego", "Irene", "Sergio", "Carmen", "Raul"];
const LAST_NAMES = ["GARCIA", "LOPEZ", "MARTIN", "SANCHEZ", "PEREZ", "GOMEZ", "RUIZ", "DIAZ", "MORENO", "ALVAREZ"];
const COUNTRIES = ["ESP", "ITA", "FRA", "GER", "POR", "NED", "SUI", "AUT"];

async function main(): Promise<void> {
  if (!process.env.FIREBASE_AUTH_EMULATOR_HOST || !process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Run this script with the emulators: npm run e2e:backend");
  }
  const { auth, db } = await import("../firebase");
  const { createUserProfile, setGlobalUserRole } = await import("../services/usersService");
  const { upsertTournamentMember } = await import("../services/membersService");
  const { buildApp } = await import("../api/app");
  const { setAccountEmaId } = await import("../services/playerAccountsService");

  console.log("Writing the EMA registry.");
  const writer = db.bulkWriter();
  for (let index = 0; index < 160; index += 1) {
    const emaId = String(10000001 + index);
    void writer.set(db.doc(`emaPlayerRegistry/${emaId}`), {
      firstName: FIRST_NAMES[index % FIRST_NAMES.length],
      lastName: `${LAST_NAMES[Math.floor(index / FIRST_NAMES.length) % LAST_NAMES.length]}${index >= 120 ? ` ${index}` : ""}`,
      country: COUNTRIES[index % COUNTRIES.length],
    });
  }
  await writer.close();

  function seedTournaments(only?: string[]): void {
    const args = ["lib/scripts/seedTestTournaments.js", "--apply", ...(only ? [`--only=${only.join(",")}`] : [])];
    const seed = spawnSync(process.execPath, args, { stdio: "inherit", env: process.env });
    if (seed.status !== 0) throw new Error("The seed script failed.");
    // The statistics are written when a table is saved, so seeded tournaments need the rebuild.
    const stats = spawnSync(process.execPath, ["lib/scripts/backfillStatistics.js", "--apply"], { stdio: "inherit", env: process.env });
    if (stats.status !== 0) throw new Error("The statistics rebuild failed.");
  }

  async function restoreMemberships(tournamentIds: string[]): Promise<void> {
    for (const tournamentId of EDITOR_TOURNAMENTS.filter((id) => tournamentIds.includes(id))) {
      await upsertTournamentMember({ tournamentId, uid: E2E_ACCOUNTS.editor.uid });
    }
  }

  console.log("Writing the test tournaments.");
  seedTournaments();

  console.log("Creating the accounts.");
  async function account(entry: { uid: string; email: string }, role: "ADMIN" | "EDITOR" | "PLAYER", disabled = false): Promise<void> {
    await auth.createUser({ uid: entry.uid, email: entry.email, password: PASSWORD, emailVerified: true });
    await createUserProfile(entry.uid, entry.email, entry.email.split("@")[0]);
    if (role === "PLAYER") await auth.setCustomUserClaims(entry.uid, { player: true });
    else await setGlobalUserRole(entry.uid, role);
    if (disabled) await auth.updateUser(entry.uid, { disabled: true });
  }
  await account(E2E_ACCOUNTS.admin, "ADMIN");
  await account(E2E_ACCOUNTS.editor, "EDITOR");
  await account(E2E_ACCOUNTS.outsider, "EDITOR");
  await account(E2E_ACCOUNTS.disabled, "EDITOR", true);
  for (const key of ["playerActive", "playerSeated", "playerWaiting", "playerDone", "playerStranger", "playerExtraTime", "playerTimeOver"] as const) {
    await account(E2E_ACCOUNTS[key], "PLAYER");
  }
  await restoreMemberships(EDITOR_TOURNAMENTS);

  /** The first EMA player who sits at a table of a tournament. The live tournament is the default. */
  async function emaPlayerAt(table: string, tournamentId = "test-live-ongoing"): Promise<string> {
    const live = db.doc(`tournaments/${tournamentId}`);
    const data = (await live.collection("tables").doc(table).get()).data() ?? {};
    const seats = [data.playerEastId, data.playerSouthId, data.playerWestId, data.playerNorthId, ...(data.playerIds ?? [])]
      .filter((seat) => seat != null && String(seat) !== "").map(String);
    for (const seat of seats) {
      const emaId = (await live.collection("players").doc(seat).get()).get("assignedEmaId");
      if (typeof emaId === "string" && emaId) return emaId;
    }
    throw new Error(`No EMA player at table ${table}.`);
  }

  const emaOfAccount = new Map<string, string>();

  /** Gives the account its EMA id. The service links the account to every slot that holds this id. */
  async function linkPlayer(entry: { uid: string }, emaId: string): Promise<void> {
    await setAccountEmaId({ uid: entry.uid, emaId: null });
    await setAccountEmaId({ uid: entry.uid, emaId });
    emaOfAccount.set(entry.uid, emaId);
    console.log(`Account ${entry.uid} plays as EMA ${emaId}.`);
  }

  await linkPlayer(E2E_ACCOUNTS.playerActive, await emaPlayerAt("4_5"));
  await linkPlayer(E2E_ACCOUNTS.playerSeated, await emaPlayerAt("4_8"));
  await linkPlayer(E2E_ACCOUNTS.playerWaiting, await emaPlayerAt("4_9"));
  await linkPlayer(E2E_ACCOUNTS.playerDone, await emaPlayerAt("4_1"));
  await linkPlayer(E2E_ACCOUNTS.playerExtraTime, await emaPlayerAt("1_1", "test-match-extra-time"));
  await linkPlayer(E2E_ACCOUNTS.playerTimeOver, await emaPlayerAt("1_1", "test-match-over"));

  /** A reset writes the slots again, so the links to the player accounts are made again. */
  async function relinkPlayers(): Promise<void> {
    for (const [uid, emaId] of emaOfAccount) {
      await setAccountEmaId({ uid, emaId: null });
      await setAccountEmaId({ uid, emaId });
    }
  }

  // No mail server runs here. The backend keeps the emails in memory, so tests can read them.
  // The email to choose a password goes through the Google password API, which the emulator cannot replace.
  // The test mailbox takes it, so tests can read it. An address that starts with "fail" makes the send fail.
  const sentEmails: { to: string; subject: string; text: string; html: string }[] = [];
  const { setPasswordResetSenderForTests } = await import("../services/firebaseAuthRest");
  setPasswordResetSenderForTests(async (email) => {
    if (email.startsWith("fail")) throw new Error("The test mailbox refuses this address.");
    sentEmails.push({ to: email, subject: "Choose your password", text: "Open the link to choose your password.", html: "" });
  });

  const app = buildApp();
  app.get("/__e2e/mail", (_req, res) => {
    res.status(200).json({ emails: sentEmails });
  });
  app.delete("/__e2e/mail", (_req, res) => {
    sentEmails.length = 0;
    res.status(200).json({ ok: true });
  });
  // Tests call this to get back the original data of some tournaments, for example after they changed a table.
  app.post("/__e2e/reset", async (req, res) => {
    try {
      const only = String(req.query.only ?? "").split(",").filter(Boolean);
      if (only.length === 0 || only.some((id) => !id.startsWith("test-"))) {
        res.status(400).json({ error: "Pass ?only=test-a,test-b" });
        return;
      }
      seedTournaments(only);
      await restoreMemberships(only);
      await relinkPlayers();
      res.status(200).json({ ok: true });
    } catch (error) {
      console.error(error);
      res.status(500).json({ error: String(error) });
    }
  });
  const server = http.createServer(app);
  await new Promise<void>((resolve) => server.listen(PORT, "0.0.0.0", resolve));
  console.log(`E2E_BACKEND_READY http://0.0.0.0:${PORT}/api (password for every account: ${PASSWORD})`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
