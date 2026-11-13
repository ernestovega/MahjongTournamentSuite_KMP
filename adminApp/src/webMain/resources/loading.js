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
