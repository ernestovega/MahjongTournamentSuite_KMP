import { ensureStoreTester, GOOGLE_PLAY_DISPOSABLE_TESTER, GOOGLE_PLAY_TESTER } from "../services/googlePlayTester";
import { runScript, type ScriptSummary } from "./scriptRun";

/**
 * Makes the accounts of the Google Play reviewers exist (docs/google-play-tester.md): the demo account and the account
 * that the review may delete. It previews by default. Add --apply to write. A missing account needs its password
 * (GOOGLE_PLAY_TESTER_PASSWORD, GOOGLE_PLAY_DISPOSABLE_TESTER_PASSWORD: the passwords of Play Console); an existing
 * account keeps its password. The same email always, so it never makes a second account.
 */
const applyChanges = process.argv.includes("--apply");

const ACCOUNTS = [
  { tester: GOOGLE_PLAY_TESTER, passwordVariable: "GOOGLE_PLAY_TESTER_PASSWORD" },
  { tester: GOOGLE_PLAY_DISPOSABLE_TESTER, passwordVariable: "GOOGLE_PLAY_DISPOSABLE_TESTER_PASSWORD" },
];

runScript("seedGooglePlayTester", async () => {
  const summary: ScriptSummary = {};
  for (const { tester, passwordVariable } of ACCOUNTS) {
    const report = await ensureStoreTester(tester, { apply: applyChanges, password: process.env[passwordVariable] });
    const mode = applyChanges ? "" : " (preview)";
    console.log(`Account ${tester.email}: ${report.account}${mode}${report.uid ? ` (${report.uid})` : ""}`);
    if (report.account === "MISSING_PASSWORD") {
      console.log(`The account does not exist. Run again with ${passwordVariable} set to the password of Play Console.`);
    }
    summary[`${tester.email} account`] = report.account;
  }
  return summary;
}, applyChanges);
