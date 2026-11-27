// Analytics for the web app. It counts screens and actions so the team can see what the users use.
// It also sends the crashes of the page to the error log of the API, with the same report id as the Analytics event.
// Fill in the values of the Firebase web app, including the measurement id, to turn it on. See docs/telemetry.md.
// These values are public. Without them this file does nothing and the app sends no data.
(function () {
  const APP = "player";
  const CONFIG = {
    apiKey: "AIzaSyDf4pvgE_iwfbo2Yo4nEmMN0Ch0qvb1T_c",
    appId: "1:827074322474:web:cb06b89f34d68a72efa353",
    measurementId: "G-M6K6X05YS8",
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

  // True when the user answered, yes or no. The app asks only until then. Declining is fine: the app works the same.
  function readAnswered() {
    try {
      const stored = localStorage.getItem(CONSENT_KEY);
      return stored === "1" || stored === "0";
    } catch (_) {
      return false;
    }
  }

  let consent = readConsent();
  let answered = readAnswered();
  window.mtsTelemetryHasConsent = () => consent;
  window.mtsTelemetryHasAnswered = () => answered;

  // Google Analytics keeps its ids in the cookies "_ga" and "_ga_<stream>", on this host or on a parent domain.
  // They are removed when the user says no, so nothing of the old visits stays in the browser.
  function removeAnalyticsCookies() {
    try {
      const names = document.cookie.split(";").map((part) => part.split("=")[0].trim()).filter((name) => /^_ga(_|$)/.test(name));
      const parts = location.hostname.split(".");
      const domains = [""];
      for (let i = 0; i < parts.length - 1; i++) domains.push("; domain=." + parts.slice(i).join("."));
      for (const name of names) {
        for (const domain of domains) document.cookie = name + "=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/" + domain;
      }
    } catch (_) {}
  }

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
    answered = true;
    try {
      localStorage.setItem(CONSENT_KEY, consent ? "1" : "0");
    } catch (_) {}
    if (consent) {
      ready = ready.then((api) => api || start().catch(() => null));
    } else {
      ready.then((api) => api && api.disable()).catch(() => {}).then(removeAnalyticsCookies);
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
