// The HTTP client of the rehearsal bots, with the measures that the report needs. Node 22, no dependencies.

export const STAGING_API = "https://europe-southwest1-mts---staging.cloudfunctions.net/api";

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
export const randomBetween = (min, max) => min + Math.random() * (max - min);

/** The time of each request and its answer, by a short label such as "GET manifest". */
export class Metrics {
  constructor() {
    this.byLabel = new Map();
    this.refusals = new Map();
    this.events = [];
  }

  record(label, status, ms, message) {
    let entry = this.byLabel.get(label);
    if (entry == null) {
      entry = { count: 0, statuses: {}, ms: [] };
      this.byLabel.set(label, entry);
    }
    entry.count += 1;
    entry.statuses[status] = (entry.statuses[status] ?? 0) + 1;
    entry.ms.push(ms);
    if (status >= 400 && message) {
      const key = `${status} ${label}: ${message}`;
      this.refusals.set(key, (this.refusals.get(key) ?? 0) + 1);
    }
  }

  event(kind, data) {
    this.events.push({ at: new Date().toISOString(), kind, ...data });
  }

  summary() {
    const labels = {};
    for (const [label, entry] of this.byLabel) {
      labels[label] = {
        count: entry.count,
        statuses: entry.statuses,
        p50: percentile(entry.ms, 50),
        p95: percentile(entry.ms, 95),
        // A spread of a long list overflows the stack: the medium scenario makes hundreds of thousands of calls.
        max: entry.ms.reduce((max, ms) => Math.max(max, ms), 0),
      };
    }
    return { labels, refusals: Object.fromEntries(this.refusals), events: this.events };
  }
}

export function percentile(values, p) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return Math.round(sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)]);
}

export class Api {
  constructor(base, metrics) {
    this.base = base.replace(/\/$/, "");
    this.metrics = metrics;
  }

  /**
   * Returns { status, json }. A network failure counts as status 0. It never throws. A read that fails on the network
   * is tried once more after a second, as the app does on its next poll; the report counts the retry as an event.
   */
  async call(method, path, options = {}) {
    const reply = await this.once(method, path, options, method === "GET");
    if (reply.status !== 0 || method !== "GET") return reply;
    this.metrics.event("network-retry", { label: options.label ?? `${method} ${path}`, message: reply.json?.message });
    await sleep(1_000);
    return this.once(method, path, options, false);
  }

  async once(method, path, { token, body, label } = {}, mayRetry = false) {
    const started = performance.now();
    let status = 0;
    let json = null;
    try {
      const response = await fetch(this.base + path, {
        method,
        headers: {
          ...(body === undefined ? {} : { "content-type": "application/json" }),
          ...(token ? { authorization: `Bearer ${token}` } : {}),
          "user-agent": "MTS rehearsal bot",
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(30_000),
      });
      status = response.status;
      const text = await response.text();
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        json = { message: text.slice(0, 120) };
      }
    } catch (error) {
      json = { message: String(error?.cause?.code ?? error?.name ?? error).slice(0, 120) };
    }
    const ms = performance.now() - started;
    if (status === 0 && mayRetry) return { status, json, ms };
    this.metrics.record(label ?? `${method} ${path}`, status, ms, status >= 400 || status === 0 ? json?.message : null);
    return { status, json, ms };
  }
}

/** A signed-in account. It renews the token before it expires, as the app does. */
export class Session {
  constructor(api, email, password) {
    this.api = api;
    this.email = email;
    this.password = password;
    this.token = null;
    this.refreshToken = null;
    this.renewAt = 0;
  }

  async signIn() {
    for (let attempt = 1; attempt <= 6; attempt += 1) {
      const reply = await this.api.call("POST", "/auth/signIn", {
        body: { email: this.email, password: this.password },
        label: "POST signIn",
      });
      if (reply.status === 200) {
        this.token = reply.json.idToken;
        this.refreshToken = reply.json.refreshToken;
        this.renewAt = Date.now() + 50 * 60_000;
        return true;
      }
      if (reply.status === 400 || reply.status === 401 || reply.status === 403) return false;
      await sleep(attempt * 5_000);
    }
    return false;
  }

  async bearer() {
    if (Date.now() >= this.renewAt && this.refreshToken) {
      const reply = await this.api.call("POST", "/auth/refresh", {
        body: { refreshToken: this.refreshToken },
        label: "POST refresh",
      });
      if (reply.status === 200) {
        this.token = reply.json.idToken;
        this.refreshToken = reply.json.refreshToken;
        this.renewAt = Date.now() + 50 * 60_000;
      } else {
        await this.signIn();
      }
    }
    return this.token;
  }
}

/** The email of a rehearsal account: the local part of the base email plus "+mts-<suffix>". */
export function rehearsalEmail(baseEmail, suffix) {
  const [local, domain] = baseEmail.trim().toLowerCase().split("@");
  if (!local || !domain) throw new Error("The base email is not an email");
  return `${local.split("+")[0]}+mts-${suffix}@${domain}`;
}

/** "10-48" or "2,3,5-7" to a list of numbers. */
export function parseRange(text) {
  return String(text ?? "")
    .split(",")
    .filter((part) => part.trim() !== "")
    .flatMap((part) => {
      const [from, to] = part.split("-").map((value) => Number(value.trim()));
      return to == null ? [from] : Array.from({ length: to - from + 1 }, (_, index) => from + index);
    });
}

export function parseArgs(argv) {
  const options = {};
  for (const arg of argv) {
    const match = /^--([^=]+)(?:=(.*))?$/.exec(arg);
    if (match) options[match[1]] = match[2] ?? true;
  }
  return options;
}
