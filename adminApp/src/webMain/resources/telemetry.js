// Analytics for the web app. It counts screens and actions so the team can see what the users use.
// Fill in the values of the Firebase web app, including the measurement id, to turn it on. See docs/telemetry.md.
// These values are public. Without them this file does nothing and the app sends no data.
(function () {
  const CONFIG = {
    apiKey: "",
    appId: "",
    measurementId: "",
    projectId: "mahjong-tournament-suite",
  };
  const SDK = "https://www.gstatic.com/firebasejs/11.0.2/";
  const MAX_CRASHES = 5;

  async function start() {
    if (!CONFIG.apiKey || !CONFIG.appId || !CONFIG.measurementId) return null;
    const { initializeApp } = await import(SDK + "firebase-app.js");
    const { getAnalytics, logEvent, setUserProperties, isSupported } = await import(SDK + "firebase-analytics.js");
    if (!(await isSupported())) return null;
    // A named app, so it does not clash with the app of App Check.
    const app = initializeApp(
      { apiKey: CONFIG.apiKey, appId: CONFIG.appId, projectId: CONFIG.projectId, measurementId: CONFIG.measurementId },
      "mts-telemetry",
    );
    const analytics = getAnalytics(app);
    setUserProperties(analytics, {
      platform: "web",
      env: location.hostname === "localhost" ? "dev" : "prod",
      language: (navigator.language || "").slice(0, 2),
    });
    return {
      event: (name, params) => logEvent(analytics, name, params),
      screen: (name) => logEvent(analytics, "screen_view", { firebase_screen: name, firebase_screen_class: name }),
      property: (name, value) => setUserProperties(analytics, { [name]: value }),
    };
  }

  const ready = start().catch(() => null);

  // kind is "event", "screen" or "property". The app calls this function. It never throws.
  window.mtsTelemetry = (kind, name, json) => {
    ready.then((api) => {
      if (!api) return;
      const data = JSON.parse(json || "{}");
      if (kind === "event") api.event(name, data);
      else if (kind === "screen") api.screen(name);
      else if (kind === "property") api.property(name, data.value);
    }).catch(() => {});
  };

  // The web has no Crashlytics. An error that nobody handled goes to Analytics as a crash event.
  let crashes = 0;
  function reportCrash(kind, message) {
    if (crashes++ >= MAX_CRASHES) return;
    window.mtsTelemetry("event", "app_crash", JSON.stringify({ kind: kind, message: String(message || "").slice(0, 100) }));
  }
  window.addEventListener("error", (event) => reportCrash("error", event.message));
  window.addEventListener("unhandledrejection", (event) => reportCrash("promise", event.reason && event.reason.message));
})();
