import { ensureGooglePlayTester, GOOGLE_PLAY_TESTER } from "../services/googlePlayTester";
import { runScript } from "./scriptRun";

/**
 * Makes the account of the Google Play reviewers exist (docs/google-play-tester.md).
 * It previews by default. Add --apply to write. A missing account needs GOOGLE_PLAY_TESTER_PASSWORD (the password of
 * Play Console); an existing account keeps its password.
 */
const applyChanges = process.argv.includes("--apply");

runScript("seedGooglePlayTester", async () => {
  const report = await ensureGooglePlayTester({ apply: applyChanges, password: process.env.GOOGLE_PLAY_TESTER_PASSWORD });
  const mode = applyChanges ? "" : " (preview)";
  console.log(`Account ${GOOGLE_PLAY_TESTER.email}: ${report.account}${mode}${report.uid ? ` (${report.uid})` : ""}`);
  if (report.account === "MISSING_PASSWORD") {
    console.log("The account does not exist. Run again with GOOGLE_PLAY_TESTER_PASSWORD set to the password of Play Console.");
  }
  return { account: report.account };
}, applyChanges);
