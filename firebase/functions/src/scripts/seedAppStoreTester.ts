import { APP_STORE_TESTER, ensureStoreTester } from "../services/googlePlayTester";
import { runScript } from "./scriptRun";

/**
 * Makes the account of the App Review of Apple exist (docs/google-play-tester.md). It previews by default. Add
 * --apply to write. APP_STORE_TESTER_PASSWORD creates a missing account; with --set-password an existing account
 * gets that password too (the workflow Publish · App Store listing puts the same one in App Store Connect).
 */
const applyChanges = process.argv.includes("--apply");
const setPassword = process.argv.includes("--set-password");

runScript("seedAppStoreTester", async () => {
  const report = await ensureStoreTester(APP_STORE_TESTER, {
    apply: applyChanges,
    password: process.env.APP_STORE_TESTER_PASSWORD,
    setPassword,
  });
  const mode = applyChanges ? "" : " (preview)";
  const password = report.passwordSet ? ", new password" : "";
  console.log(`Account ${APP_STORE_TESTER.email}: ${report.account}${password}${mode}${report.uid ? ` (${report.uid})` : ""}`);
  if (report.account === "MISSING_PASSWORD") {
    console.log("The account does not exist. Run again with APP_STORE_TESTER_PASSWORD set.");
  }
  return { account: report.account, passwordSet: report.passwordSet ?? false };
}, applyChanges);
