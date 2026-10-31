// Analytics for the web app. It counts screens and actions so the team can see what the users use.
// It also sends the crashes of the page to the error log of the API, with the same report id as the Analytics event.
// Fill in the values of the Firebase web app, including the measurement id, to turn it on. See docs/telemetry.md.
// These values are public. Without them this file does nothing and the app sends no data.
(function () {
  const APP = "player";
  const CONFIG = {
    apiKey: "",
    appId: "",
    measurementId: "",
    projectId: "mahjong-tournament-suite",
  };
  const SDK = "https://www.gstatic.com/firebasejs/11.0.2/";
  const CONSENT_KEY = "mtsAnalyticsConsent";
  const MAX_CRASHES = 5;
  const configured = !!(CONFIG.apiKey && CONFIG.appId && CONFIG.measurementId);
  // The tests of the repository replace this loader, so they need no network. The page never sets it.
  const loadSdk = window.mtsSdkLoader || ((file) => import(SDK + file));

  window.mtsApp = APP;
  window.mtsTelemetryConfigured = () => configured;

  function readConsent() {
    try {
      return configured && localStorage.getItem(CONSENT_KEY) === "1";
    } catch (_) {
      return false;
    }
  }

  let consent = readConsent();
  window.mtsTelemetryHasConsent = () => consent;

  // Facts for the crash report. Kotlin fills in the version and the role.
  const context = {
    sessionId: Math.random().toString(36).slice(2) + Date.now().toString(36),
    language: (navigator.language || "").slice(0, 2),
    env: location.hostname === "localhost" ? "dev" : "prod",
  };

  async function start() {
    const { initializeApp } = await loadSdk("firebase-app.js");
    const { getAnalytics, logEvent, setUserProperties, setAnalyticsCollectionEnabled, isSupported } =
      await loadSdk("firebase-analytics.js");
    if (!(await isSupported())) return null;
    // A named app, so it does not clash with the app of App Check.
    const app = initializeApp(
      { apiKey: CONFIG.apiKey, appId: CONFIG.appId, projectId: CONFIG.projectId, measurementId: CONFIG.measurementId },
      "mts-telemetry",
    );
    const analytics = getAnalytics(app);
    setAnalyticsCollectionEnabled(analytics, true);
    setUserProperties(analytics, { app: APP, platform: "web", env: context.env, language: context.language });
    return {
      event: (name, params) => logEvent(analytics, name, params),
      screen: (name) => logEvent(analytics, "screen_view", { firebase_screen: name, firebase_screen_class: name }),
      property: (name, value) => setUserProperties(analytics, { [name]: value }),
      disable: () => setAnalyticsCollectionEnabled(analytics, false),
    };
  }

  let ready = consent ? start().catch(() => null) : Promise.resolve(null);

  function setConsent(granted) {
    consent = configured && granted;
    try {
      localStorage.setItem(CONSENT_KEY, consent ? "1" : "0");
    } catch (_) {}
    if (consent) {
      ready = ready.then((api) => api || start().catch(() => null));
    } else {
      ready.then((api) => api && api.disable()).catch(() => {});
      ready = Promise.resolve(null);
    }
  }

  // kind is "event", "screen", "property", "context" or "consent". The app calls this function. It never throws.
  window.mtsTelemetry = (kind, name, json) => {
    try {
      const data = JSON.parse(json || "{}");
      if (kind === "consent") return setConsent(!!data.value);
      if (kind === "context") return Object.assign(context, data);
      if (kind === "property" && name === "role") context.role = data.value || undefined;
      if (!consent) return;
      ready.then((api) => {
        if (!api) return;
        if (kind === "event") api.event(name, data);
        else if (kind === "screen") api.screen(name);
        else if (kind === "property") api.property(name, data.value);
      }).catch(() => {});
    } catch (_) {}
  };

  // A page that dies cannot wait for the app. This code counts the crash and sends it to the error log at once.
  let crashes = 0;
  async function reportCrash(kind, error, message) {
    if (!consent || crashes++ >= MAX_CRASHES) return;
    const reportId = Math.random().toString(36).slice(2) + Date.now().toString(36);
    const errorType = (error && error.name) || (kind === "promise" ? "UnhandledRejection" : "Error");
    window.mtsTelemetry("event", "app_crash", JSON.stringify({ report_id: reportId, kind: errorType }));
    try {
      const token = typeof window.mtsAppCheckToken === "function" ? await window.mtsAppCheckToken() : null;
      const headers = { "Content-Type": "application/json" };
      if (token) headers["X-Firebase-AppCheck"] = token;
      await fetch("/api/telemetry/errors", {
        method: "POST",
        keepalive: true,
        headers,
        body: JSON.stringify({
          app: APP,
          platform: "web",
          kind: "crash",
          reportId,
          errorType,
          message: String(message || (error && error.message) || "").slice(0, 500),
          stackTrace: String((error && error.stack) || "").slice(0, 8000),
          ...context,
        }),
      });
    } catch (_) {}
  }
  window.addEventListener("error", (event) => reportCrash("error", event.error, event.message));
  window.addEventListener("unhandledrejection", (event) => reportCrash("promise", event.reason, event.reason && event.reason.message));
})();
