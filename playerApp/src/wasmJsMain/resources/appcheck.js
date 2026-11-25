// App Check for the web app. It sends a token with every API request, so the server can tell the app from scripts.
// Fill in the values of the Firebase web app and the reCAPTCHA Enterprise (Fraud Defense) site key to turn it on. See docs/backend.md.
// These values are public. Without them this file does nothing and the app sends no token.
const CONFIG = {
  siteKey: "6LdRfh4uAAAAACR1gxK_wnRs0_3RplOT6n3HdGuB",
  apiKey: "AIzaSyDf4pvgE_iwfbo2Yo4nEmMN0Ch0qvb1T_c",
  appId: "1:827074322474:web:cb06b89f34d68a72efa353",
  projectId: "mahjong-tournament-suite",
};
const SDK = "https://www.gstatic.com/firebasejs/11.0.2/";

// Off on the web since 25-nov-2026 (Ernesto). The server is in App Check monitor mode, so the token blocked nobody, and
// getting it (Firebase SDK, reCAPTCHA Enterprise, token exchange) held the first requests of a page about 2.8 s on a
// phone whenever the browser had no saved token. Android keeps App Check. Set to true to send the token again.
const ENABLED = false;

async function start() {
  if (!ENABLED || !CONFIG.siteKey || !CONFIG.apiKey || !CONFIG.appId) return null;
  const { initializeApp } = await import(SDK + "firebase-app.js");
  const { initializeAppCheck, ReCaptchaEnterpriseProvider, getToken } = await import(SDK + "firebase-app-check.js");
  // On localhost the SDK prints a debug token in the console. Register it in the Firebase console.
  if (location.hostname === "localhost") self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
  const app = initializeApp({ apiKey: CONFIG.apiKey, appId: CONFIG.appId, projectId: CONFIG.projectId });
  const appCheck = initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(CONFIG.siteKey),
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
