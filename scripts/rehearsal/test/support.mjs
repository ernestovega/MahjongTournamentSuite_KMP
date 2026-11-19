// Helpers of the tests of the rehearsal scripts.

/** A registry of [count] EMA players with real-looking names, numbers from 10000001. */
export function registry(count, extra = []) {
  return [
    ...Array.from({ length: count }, (_, index) => ({
      emaId: String(10000001 + index), firstName: `Name${index + 1}`, lastName: `Surname${index + 1}`, disabled: false,
    })),
    ...extra,
  ];
}

/**
 * Replaces the global fetch with [handler] (method, path, body) => [status, json] while [run] runs. The scenario and
 * the API client call fetch, so the tests answer as staging would.
 */
export async function withFetch(handler, run) {
  const original = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    const { pathname, search } = new URL(url);
    const path = pathname.replace(/^\/api/, "") + search;
    const [status, json] = await handler(init.method ?? "GET", path, init.body ? JSON.parse(init.body) : undefined);
    return new Response(json == null ? "" : JSON.stringify(json), { status });
  };
  try {
    return await run();
  } finally {
    globalThis.fetch = original;
  }
}

/** Runs [run] with the environment variables of [values], and puts the old ones back. */
export async function withEnv(values, run) {
  const before = Object.fromEntries(Object.keys(values).map((key) => [key, process.env[key]]));
  Object.assign(process.env, values);
  try {
    return await run();
  } finally {
    for (const [key, value] of Object.entries(before)) {
      if (value == null) delete process.env[key];
      else process.env[key] = value;
    }
  }
}

export const ENV = {
  REHEARSAL_IMAP_USER: "mailbox@example.test",
  REHEARSAL_PASSWORD: "secret",
  STAGING_ADMIN_EMAIL: "admin@example.test",
};

/** A staging that answers the plan of a scenario: the bot admin signs in, and the registry has [players]. */
export function planStaging(players, users = []) {
  return (method, path) => {
    if (method === "POST" && path === "/auth/signIn") return [200, { idToken: "token", refreshToken: "refresh" }];
    if (path === "/ema-player-registry") return [200, { players }];
    if (path === "/admin/users") return [200, { users }];
    return [404, { message: "no route" }];
  };
}
