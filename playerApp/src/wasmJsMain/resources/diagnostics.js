// Start diagnostics. A blank page gives the user no clue. This file shows why the app did not start.
// It shows nothing when the app starts well. The text can be copied or photographed and sent to the organizer.
(function () {
  const WAIT_MS = 20 * 1000;
  const problems = [];
  let box = null;

  // The app draws on a canvas. When a canvas exists, the app has started.
  const started = () => document.querySelector("canvas") !== null;

  const info = () => {
    let locale = "";
    let calendar = "";
    try {
      const options = new Intl.DateTimeFormat().resolvedOptions();
      locale = options.locale;
      calendar = options.calendar;
    } catch (_) {
      locale = "Intl error";
    }
    let storage = "ok";
    try {
      window.localStorage.setItem("mts-check", "1");
      window.localStorage.removeItem("mts-check");
      window.sessionStorage.setItem("mts-check", "1");
      window.sessionStorage.removeItem("mts-check");
    } catch (e) {
      storage = String(e);
    }
    return [
      "Browser: " + navigator.userAgent,
      "Language: " + navigator.language + " (" + (navigator.languages || []).join(", ") + ")",
      "Locale: " + locale + ", calendar: " + calendar,
      "WebAssembly: " + (typeof WebAssembly === "object" ? "yes" : "NO"),
      "Storage: " + storage,
    ];
  };

  const render = () => {
    if (started()) return;
    if (!box) {
      box = document.createElement("pre");
      box.style.cssText = "text-align:left;white-space:pre-wrap;word-break:break-word;margin:16px;padding:12px;" +
        "font:13px monospace;color:#b00020;background:#fff;border:1px solid #b00020;border-radius:6px";
      document.body.appendChild(box);
    }
    box.textContent = "The app did not start.\nSend this text to the organizer.\n\n" +
      problems.join("\n") + "\n\n" + info().join("\n");
  };

  const report = (text) => {
    problems.push(text);
    render();
  };

  window.addEventListener("error", (event) => {
    const where = event.filename ? " (" + event.filename + ":" + event.lineno + ")" : "";
    report("Error: " + (event.message || "unknown") + where);
  });
  window.addEventListener("unhandledrejection", (event) => {
    const reason = event.reason;
    report("Rejected: " + (reason && reason.stack ? reason.stack : String(reason)));
  });

  if (typeof WebAssembly !== "object") {
    report("This browser does not support WebAssembly.");
  }

  setTimeout(() => {
    if (!started()) report("No screen after " + WAIT_MS / 1000 + " seconds.");
  }, WAIT_MS);
})();
