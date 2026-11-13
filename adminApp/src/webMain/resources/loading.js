// Sets the colors of the loading screen before the app starts, so a dark theme does not flash white.
// The app keeps the theme in localStorage under "themePreference": System, Light or Dark.
(function () {
  try {
    const saved = window.localStorage.getItem("themePreference");
    const dark = saved === "Dark" || (saved !== "Light" && window.matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
  } catch (_) {
    // Without storage the light colors stay.
  }
})();

// Marks the page with the language that the user chose in the app, before the app starts. The browser and screen
// readers read it, and Chrome does not offer to translate a page that is already in the language of the user.
// The app keeps the choice in localStorage under "languagePreference" and updates the mark when it changes.
(function () {
  try {
    const saved = window.localStorage.getItem("languagePreference");
    if (saved) document.documentElement.lang = saved;
  } catch (_) {
    // Without storage the page stays in English, the default language of the app.
  }
})();
