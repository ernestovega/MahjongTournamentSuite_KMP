// Start diagnostics. A blank page gives the user no clue. This file shows why the app did not start.
// It shows nothing when the app starts well. The user can copy or share the text, or send a screenshot.
(function () {
  const WAIT_MS = 20 * 1000;
  const MAX_LINES = 40;
  const problems = [];
  const startedAt = Date.now();
  let panel = null;
  let report = null;

  // The app draws on a canvas. When a canvas exists, the app has started.
  const started = () => document.querySelector("canvas") !== null;

  const seconds = () => Math.round((Date.now() - startedAt) / 100) / 10;

  // A console message alone does not show the panel. The app can write warnings and still start well.
  const add = (text, show = true) => {
    if (problems.length < MAX_LINES) problems.push("[" + seconds() + "s] " + text);
    if (show) render();
  };

  // Check a feature with a tiny WebAssembly module. A wrong answer here only helps the organizer to read the text.
  const supports = (bytes) => {
    try {
      return typeof WebAssembly === "object" && WebAssembly.validate(new Uint8Array(bytes));
    } catch (_) {
      return false;
    }
  };
  const yesNo = (value) => (value ? "yes" : "NO");

  const safeStorage = (storage) => {
    try {
      storage.setItem("mts-check", "1");
      storage.removeItem("mts-check");
      return "ok";
    } catch (e) {
      return String(e);
    }
  };

  const resources = () => {
    try {
      return performance.getEntriesByType("resource").map((entry) => {
        const name = entry.name.replace(location.origin, "");
        const size = entry.transferSize || entry.encodedBodySize || 0;
        return "  " + name.slice(-40) + " " + Math.round(size / 1024) + " KB, " + Math.round(entry.duration) + " ms";
      });
    } catch (_) {
      return ["  not available"];
    }
  };

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
    const safari = /Version\/([\d.]+).*Safari/.exec(navigator.userAgent);
    const ios = /OS ([\d_]+) like Mac OS X/.exec(navigator.userAgent);
    return [
      "Safari: " + (safari ? safari[1] : "no") + ", iOS: " + (ios ? ios[1].replace(/_/g, ".") : "no"),
      "WebAssembly: " + yesNo(typeof WebAssembly === "object"),
      "Wasm GC: " + yesNo(supports([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0])),
      "Wasm exceptions (old): " + yesNo(supports([0, 97, 115, 109, 1, 0, 0, 0, 1, 4, 1, 96, 0, 0, 3, 2, 1, 0, 10, 8, 1, 6, 0, 6, 64, 25, 11, 11])),
      "Wasm exceptions (new): " + yesNo(supports([0, 97, 115, 109, 1, 0, 0, 0, 1, 4, 1, 96, 0, 0, 3, 2, 1, 0, 10, 8, 1, 6, 0, 31, 64, 0, 11, 11])),
      "Storage: " + safeStorage(window.localStorage || {}) + " / " + safeStorage(window.sessionStorage || {}),
      "Language: " + navigator.language + " (" + (navigator.languages || []).join(", ") + ")",
      "Locale: " + locale + ", calendar: " + calendar,
      "Screen: " + window.innerWidth + "x" + window.innerHeight + " at " + window.devicePixelRatio + "x",
      "Memory: " + (navigator.deviceMemory ? navigator.deviceMemory + " GB" : "unknown"),
      "Online: " + yesNo(navigator.onLine),
      "Browser: " + navigator.userAgent,
    ];
  };

  const buildReport = () => {
    const lines = ["MahjongTournamentSuite Player did not start.", location.href, ""];
    lines.push(problems.length ? problems.join("\n") : "No error was reported.", "");
    lines.push(info().join("\n"), "", "Files loaded:", resources().join("\n"));
    return lines.join("\n");
  };

  const button = (label, onClick) => {
    const element = document.createElement("button");
    element.textContent = label;
    element.style.cssText = "font:600 14px sans-serif;padding:8px 14px;margin:0 8px 8px 0;border:1px solid #b00020;" +
      "border-radius:6px;background:#fff;color:#b00020";
    element.addEventListener("click", onClick);
    return element;
  };

  const copy = async (element) => {
    const text = buildReport();
    try {
      await navigator.clipboard.writeText(text);
    } catch (_) {
      // Older browsers: select the text and copy it with the old command.
      const range = document.createRange();
      range.selectNodeContents(report);
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      try {
        document.execCommand("copy");
      } catch (__) {
        // The text stays selected. The user can copy it by hand.
      }
    }
    element.textContent = "Copied";
    setTimeout(() => (element.textContent = "Copy text"), 2000);
  };

  const create = () => {
    // A fixed panel that scrolls by itself. The page cannot scroll, so the panel must.
    panel = document.createElement("div");
    panel.style.cssText = "position:fixed;top:0;left:0;right:0;bottom:0;z-index:2147483646;overflow-y:auto;" +
      "-webkit-overflow-scrolling:touch;touch-action:pan-y;background:#fff;color:#b00020;text-align:left;" +
      "padding:12px;box-sizing:border-box";
    const title = document.createElement("div");
    title.textContent = "The app did not start. Send the text below to the organizer.";
    title.style.cssText = "font:600 15px sans-serif;margin-bottom:10px";
    const buttons = document.createElement("div");
    buttons.appendChild(button("Copy text", (event) => copy(event.currentTarget)));
    if (navigator.share) {
      buttons.appendChild(button("Share", () => navigator.share({ text: buildReport() }).catch(() => {})));
    }
    buttons.appendChild(button("Reload", () => window.location.reload()));
    report = document.createElement("pre");
    report.style.cssText = "margin:0;white-space:pre-wrap;word-break:break-word;font:11px/1.3 monospace;" +
      "user-select:text;-webkit-user-select:text";
    panel.append(title, buttons, report);
    (document.documentElement || document.body).appendChild(panel);
  };

  const render = () => {
    if (started()) {
      if (panel) {
        panel.remove();
        panel = null;
      }
      return;
    }
    if (!panel) create();
    report.textContent = buildReport();
  };

  // The capture phase also gets the errors of files that fail to load. These errors do not bubble.
  window.addEventListener("error", (event) => {
    const target = event.target;
    if (target && target !== window && (target.src || target.href)) {
      add("File did not load: " + (target.src || target.href));
      return;
    }
    const where = event.filename ? " (" + event.filename + ":" + event.lineno + ")" : "";
    add("Error: " + (event.message || "unknown") + where);
  }, true);
  window.addEventListener("unhandledrejection", (event) => {
    const reason = event.reason;
    add("Rejected: " + (reason && reason.stack ? reason.stack : String(reason)));
  });

  // Keep the messages that the app writes to the console. Kotlin writes its own errors there.
  ["error", "warn"].forEach((level) => {
    const original = console[level];
    console[level] = function () {
      try {
        add("Console " + level + ": " + Array.prototype.map.call(arguments, String).join(" ").slice(0, 300), false);
      } catch (_) {
        // Never break the console.
      }
      return original.apply(console, arguments);
    };
  });

  if (typeof WebAssembly !== "object") add("This browser does not support WebAssembly.");

  setTimeout(() => {
    if (!started()) add("No screen after " + WAIT_MS / 1000 + " seconds.");
  }, WAIT_MS);

  // Remove the panel when the app starts late.
  setInterval(() => {
    if (panel) render();
  }, 1000);
})();
