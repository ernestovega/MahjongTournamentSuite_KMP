// App Check for the web app. It sends a token with every API request, so the server can tell the app from scripts.
// Fill in the values of the Firebase web app and the reCAPTCHA v3 site key to turn it on. See docs/backend.md.
// These values are public. Without them this file does nothing and the app sends no token.
const CONFIG = {
  siteKey: "",
  apiKey: "",
  appId: "",
  projectId: "mahjong-tournament-suite",
};
const SDK = "https://www.gstatic.com/firebasejs/11.0.2/";

async function start() {
  if (!CONFIG.siteKey || !CONFIG.apiKey || !CONFIG.appId) return null;
  const { initializeApp } = await import(SDK + "firebase-app.js");
  const { initializeAppCheck, ReCaptchaV3Provider, getToken } = await import(SDK + "firebase-app-check.js");
  // On localhost the SDK prints a debug token in the console. Register it in the Firebase console.
  if (location.hostname === "localhost") self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
  const app = initializeApp({ apiKey: CONFIG.apiKey, appId: CONFIG.appId, projectId: CONFIG.projectId });
  const appCheck = initializeAppCheck(app, {
    provider: new ReCaptchaV3Provider(CONFIG.siteKey),
    isTokenAutoRefreshEnabled: true,
  });
  return async () => (await getToken(appCheck, false)).token;
}

let ready = null;

// A failure gives null. The server decides what a missing token means.
window.mtsAppCheckToken = async () => {
  try {
    ready = ready ?? start();
    const read = await ready;
    return read ? await read() : null;
  } catch (_) {
    return null;
  }
};
