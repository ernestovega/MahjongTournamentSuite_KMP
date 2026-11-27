/**
 * The project that a maintenance script writes to, named on the command line: --target=prod, --target=staging or
 * --target=emulator. A script imports "./useScriptTarget" before "../firebase": it sets the project variables that the
 * Firebase app reads, so the target is never the production default by accident. The script refuses to run without
 * --target, or when a project variable that is already set names another project.
 */
export const SCRIPT_TARGETS = {
  prod: "mahjong-tournament-suite",
  staging: "mts---staging",
} as const;

export type ScriptTarget = keyof typeof SCRIPT_TARGETS | "emulator";

export function parseScriptTarget(argv: string[], env: NodeJS.ProcessEnv): { target: ScriptTarget; projectId: string } {
  const value = argv.find((arg) => arg.startsWith("--target="))?.slice("--target=".length).trim();
  if (value !== "prod" && value !== "staging" && value !== "emulator") {
    throw new Error("Pass --target=prod, --target=staging or --target=emulator.");
  }
  const named = [env.EMA_FIREBASE_PROJECT, env.GCLOUD_PROJECT, env.GOOGLE_CLOUD_PROJECT]
    .map((project) => project?.trim())
    .filter((project): project is string => !!project);
  if (value === "emulator") {
    if (!env.FIRESTORE_EMULATOR_HOST?.trim()) throw new Error("--target=emulator needs FIRESTORE_EMULATOR_HOST (run it inside firebase emulators:exec).");
    const projectId = named[0];
    if (!projectId?.startsWith("demo-")) throw new Error("--target=emulator needs a demo- project in GCLOUD_PROJECT.");
    return { target: value, projectId };
  }
  const projectId = SCRIPT_TARGETS[value];
  const other = named.find((project) => project !== projectId);
  if (other) throw new Error(`--target=${value} is ${projectId}, but a project variable names ${other}.`);
  if (env.FIRESTORE_EMULATOR_HOST?.trim()) throw new Error(`--target=${value} cannot run with FIRESTORE_EMULATOR_HOST set.`);
  return { target: value, projectId };
}
