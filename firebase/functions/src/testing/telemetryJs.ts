import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

/** The two copies of the script. They differ only in the name of the app. */
export const TELEMETRY_FILES = {
  admin: "../../adminApp/src/webMain/resources/telemetry.js",
  player: "../../playerApp/src/wasmJsMain/resources/telemetry.js",
} as const;

export type TelemetryApp = keyof typeof TELEMETRY_FILES;

export function telemetrySource(app: TelemetryApp): string {
  // `npm test` runs in firebase/functions, so the paths start there.
  return fs.readFileSync(path.resolve(process.cwd(), TELEMETRY_FILES[app]), "utf8");
}

export type PageOptions = {
  /** False keeps the empty settings of the file. The default fills them in, like a real release. */
  configured?: boolean;
  /** The value that the page already holds in localStorage for the consent. */
  storedConsent?: "1" | "0" | null;
  hostname?: string;
  language?: string;
  /** The token of App Check. Null means that the page has none. */
  appCheckToken?: string | null;
  /** The answer of `isSupported()` of the Analytics SDK. */
  supported?: boolean;
  /** True makes every access to localStorage throw, like a private window. */
  storageThrows?: boolean;
  /** True makes the request to the error log fail. */
  fetchFails?: boolean;
};

/** Everything that the script did with the SDK and with the network. Values are plain host objects. */
export type PageCalls = {
  loaded: string[];
  initialized: Array<{ config: Record<string, string>; name: string }>;
  events: Array<{ name: string; params: Record<string, unknown> }>;
  properties: Array<Record<string, unknown>>;
  collection: boolean[];
  fetches: Array<{ url: string; init: { method: string; keepalive: boolean; headers: Record<string, string>; body: string } }>;
};

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value ?? null));

/** Lets the promises of the script finish. The SDK loads in the background. */
export async function settle(): Promise<void> {
  for (let turn = 0; turn < 10; turn += 1) await new Promise((resolve) => setImmediate(resolve));
}

/** Runs the script in an empty page with fake browser objects and a fake Analytics SDK. */
export function loadPage(app: TelemetryApp, options: PageOptions = {}) {
  let source = telemetrySource(app);
  if (options.configured !== false) {
    source = source
      .replace('apiKey: ""', 'apiKey: "test-key"')
      .replace('appId: ""', 'appId: "test-app"')
      .replace('measurementId: ""', 'measurementId: "G-TEST"');
  }
  const calls: PageCalls = { loaded: [], initialized: [], events: [], properties: [], collection: [], fetches: [] };
  const storage = new Map<string, string>();
  if (options.storedConsent) storage.set("mtsAnalyticsConsent", options.storedConsent);
  const listeners: Record<string, (event: unknown) => unknown> = {};
  const supported = options.supported ?? true;

  const modules: Record<string, unknown> = {
    "firebase-app.js": {
      initializeApp: (config: Record<string, string>, name: string) => {
        calls.initialized.push({ config: clone(config), name });
        return { name };
      },
    },
    "firebase-analytics.js": {
      getAnalytics: (firebaseApp: unknown) => ({ firebaseApp }),
      logEvent: (_analytics: unknown, name: string, params: Record<string, unknown>) => calls.events.push({ name, params: clone(params) }),
      setUserProperties: (_analytics: unknown, properties: Record<string, unknown>) => calls.properties.push(clone(properties)),
      setAnalyticsCollectionEnabled: (_analytics: unknown, enabled: boolean) => calls.collection.push(enabled),
      isSupported: async () => supported,
    },
  };

  const sandbox: Record<string, unknown> = {
    mtsSdkLoader: async (file: string) => {
      calls.loaded.push(file);
      return modules[file];
    },
    navigator: { language: options.language ?? "en-US" },
    location: { hostname: options.hostname ?? "app.example.test" },
    localStorage: {
      getItem: (key: string) => {
        if (options.storageThrows) throw new Error("blocked");
        return storage.get(key) ?? null;
      },
      setItem: (key: string, value: string) => {
        if (options.storageThrows) throw new Error("blocked");
        storage.set(key, value);
      },
    },
    fetch: async (url: string, init: PageCalls["fetches"][number]["init"]) => {
      calls.fetches.push({ url, init: clone(init) });
      if (options.fetchFails) throw new Error("offline");
      return {};
    },
    addEventListener: (name: string, listener: (event: unknown) => unknown) => {
      listeners[name] = listener;
    },
  };
  if (options.appCheckToken !== undefined && options.appCheckToken !== null) {
    sandbox.mtsAppCheckToken = async () => options.appCheckToken;
  }
  sandbox.window = sandbox;
  vm.runInContext(source, vm.createContext(sandbox));

  const window = sandbox as Record<string, (...args: unknown[]) => unknown> & { mtsApp: string };
  return {
    calls,
    storage,
    window,
    /** The call that the Kotlin code makes. */
    send: (kind: string, name: string, data: unknown = {}) => window.mtsTelemetry(kind, name, JSON.stringify(data)),
    /** The raw call, for a body that is not valid JSON. */
    sendRaw: (kind: string, name: string, json: string) => window.mtsTelemetry(kind, name, json),
    fire: (type: "error" | "unhandledrejection", event: unknown) => listeners[type](event),
    hasListener: (type: string) => typeof listeners[type] === "function",
  };
}
