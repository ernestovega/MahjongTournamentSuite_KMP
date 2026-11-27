import { APP_STORE_DISPOSABLE_TESTER, APP_STORE_TESTER, ensureStoreTester } from "../services/googlePlayTester";
import { runScript, type ScriptSummary } from "./scriptRun";

/**
 * Makes the accounts of the App Review of Apple exist (docs/google-play-tester.md): the demo account and the account
 * that the review may delete. It previews by default. Add --apply to write. APP_STORE_TESTER_PASSWORD and
 * APP_STORE_DISPOSABLE_TESTER_PASSWORD create a missing account; with --set-password an existing account gets that
 * password too (the workflow Publish · App Store listing puts the same ones in App Store Connect).
 */
const applyChanges = process.argv.includes("--apply");
const setPassword = process.argv.includes("--set-password");

const ACCOUNTS = [
  { tester: APP_STORE_TESTER, passwordVariable: "APP_STORE_TESTER_PASSWORD" },
  { tester: APP_STORE_DISPOSABLE_TESTER, passwordVariable: "APP_STORE_DISPOSABLE_TESTER_PASSWORD" },
];

runScript("seedAppStoreTester", async () => {
  const summary: ScriptSummary = {};
  for (const { tester, passwordVariable } of ACCOUNTS) {
    const report = await ensureStoreTester(tester, {
      apply: applyChanges,
      password: process.env[passwordVariable],
      setPassword,
    });
    const mode = applyChanges ? "" : " (preview)";
    const password = report.passwordSet ? ", new password" : "";
    console.log(`Account ${tester.email}: ${report.account}${password}${mode}${report.uid ? ` (${report.uid})` : ""}`);
    if (report.account === "MISSING_PASSWORD") {
      console.log(`The account does not exist. Run again with ${passwordVariable} set.`);
    }
    summary[`${tester.email} account`] = report.account;
    summary[`${tester.email} passwordSet`] = report.passwordSet ?? false;
  }
  return summary;
}, applyChanges);
