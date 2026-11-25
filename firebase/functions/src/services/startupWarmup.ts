import { db } from "../firebase";

/**
 * The first Firestore call of an instance loads the client and opens its connection: about 0.5 to 1.7 s, while the
 * next calls take a few milliseconds. A new instance of the API makes that call as it starts, so the first request
 * of a player (often after the uptime check, which reads no data, started the instance) does not wait for it.
 * It costs one read per new instance. Only the deployed API does it, never the tests, the emulator or the scripts.
 */
export function shouldWarmUp(env: NodeJS.ProcessEnv): boolean {
  return env.FUNCTION_TARGET === "api" && env.FUNCTIONS_EMULATOR !== "true" && env.FIRESTORE_EMULATOR_HOST == null;
}

export function warmUpFirestore(read: () => Promise<unknown> = () => db.doc("config/warmup").get()): Promise<void> {
  return read().then(
    () => undefined,
    (error: unknown) => console.warn("Firestore warm-up failed", error),
  );
}
