import { userInfo } from "node:os";

import { FieldValue, type Firestore } from "firebase-admin/firestore";

/**
 * The maintenance scripts change data outside the API, so each run that writes leaves one record in the activity log:
 * which script, who ran it on which machine user, with which arguments, and how it ended. The admin app shows it in
 * the Activity screen. A run that only previews writes nothing, so it leaves no record.
 *
 * This module does not import "../firebase", so a script with its own Firebase app (resetAccess) can use it too.
 */

/** The activity log collection. It is the same as ACTIVITY_COLLECTION in services/activityLog. */
const ACTIVITY_COLLECTION = "activity";
const MAX_TEXT = 200;

/** The short summary values of a run, for example `{ additions: 3 }`. */
export type ScriptSummary = Record<string, string | number | boolean | null>;

function machineUser(): string {
  try {
    return userInfo().username || "unknown";
  } catch {
    return "unknown";
  }
}

function short(text: string): string {
  return text.length > MAX_TEXT ? `${text.slice(0, MAX_TEXT)}…` : text;
}

/** The arguments without values that look like e-mail addresses. */
function argumentsText(): string {
  return short(process.argv.slice(2).map((arg) => (arg.includes("@") ? arg.replace(/=.*/, "=…") : arg)).join(" "));
}

export function buildScriptRunEntry(params: {
  script: string;
  projectId: string;
  outcome: "ok" | "failed";
  summary?: ScriptSummary;
  error?: unknown;
  user?: string;
}) {
  const user = params.user ?? machineUser();
  const details: Record<string, unknown> = {
    ...(params.summary ?? {}),
    outcome: params.outcome,
    project: params.projectId,
    args: argumentsText(),
  };
  if (params.error !== undefined) {
    details.error = short(params.error instanceof Error ? params.error.message : String(params.error));
  }
  return {
    action: "script.run",
    uid: "script",
    name: `${user} (script)`,
    tournamentId: null,
    target: params.script,
    details,
  };
}

/** Writes the record of one run. A failure is reported and never hides the result of the script. */
export async function recordScriptRun(firestore: Firestore, params: Parameters<typeof buildScriptRunEntry>[0]): Promise<void> {
  try {
    await firestore.collection(ACTIVITY_COLLECTION).add({ ...buildScriptRunEntry(params), at: FieldValue.serverTimestamp() });
  } catch (error) {
    console.error("Writing the script run to the activity log failed", error);
  }
}

/**
 * Runs the main function of a script. When [writes] is true, the run leaves its record in the default project of
 * "../firebase", with the summary that [main] returns. An error sets the exit code to 1.
 */
export function runScript(script: string, main: () => Promise<ScriptSummary | void>, writes: boolean): void {
  const finish = async (outcome: "ok" | "failed", summary?: ScriptSummary, error?: unknown) => {
    if (!writes) return;
    const { db, firebaseProjectId } = await import("../firebase");
    await recordScriptRun(db, { script, projectId: firebaseProjectId, outcome, summary, error });
  };
  main()
    .then((summary) => finish("ok", summary ?? undefined))
    .catch(async (error: unknown) => {
      console.error(`${script} failed.`, error);
      process.exitCode = 1;
      await finish("failed", undefined, error);
    });
}
