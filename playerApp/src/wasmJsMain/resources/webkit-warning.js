// Warning for WebKit 27.2. Its WebAssembly compiler gives wrong results after some use of the app.
// The screen then stays blank or draws the items in the wrong place. Turning the compiler off removes the problem.
// The page cannot fix this, so it tells the user. Safari 27.2 shows "Version/27.2". Other iPhone browsers show "OS 27_2".
(function () {
  const agent = navigator.userAgent || "";
  const affected = /Version\/27\.2(\.|\s|$)/.test(agent) || /(iPhone|iPad).* OS 27_2([_\s]|$)/.test(agent);
  if (!affected) return;

  const KEY = "mts-webkit-warning-closed";
  try {
    if (sessionStorage.getItem(KEY)) return;
  } catch (_) {
    // Without session storage the warning shows on every load.
  }

  const bar = document.createElement("div");
  bar.setAttribute("role", "alert");
  bar.style.cssText = "position:fixed;left:0;right:0;bottom:0;z-index:2147483647;display:flex;gap:12px;" +
    "align-items:center;padding:10px 16px;background:#f9a825;color:#000;font:14px/1.4 sans-serif;text-align:left";
  const text = document.createElement("div");
  text.style.flex = "1";
  const problem = document.createElement("div");
  problem.textContent = "This version of Safari (27.2) has a bug that can break this app. " +
    "The screen can stay blank or show items in the wrong place. " +
    "Use another browser on a computer, or wait for the next Apple update.";
  // Without another browser, the user can turn off the WebAssembly compiler of Safari. The app then loads slowly.
  const workaround = document.createElement("div");
  workaround.style.cssText = "margin-top:6px;font-size:13px";
  const onPhone = /iPhone|iPad/.test(agent);
  workaround.textContent = "No other browser? " + (onPhone
    ? "Open Settings > Apps > Safari > Advanced > Feature Flags. "
    : "Open Safari > Settings > Advanced and turn on \"Show features for web developers\". Then open Develop > WebKit Feature Flags. ") +
    "Turn on \"Experimental WebAssembly Debugger (Disables JIT)\". " +
    (onPhone ? "Close Safari and open it again. " : "Quit Safari with Cmd+Q and open it again. ") +
    "The app then loads slowly, but the screen draws well.";
  text.append(problem, workaround);
  const close = document.createElement("button");
  close.textContent = "Close";
  close.style.cssText = "padding:6px 12px;border:1px solid #000;background:transparent;color:#000;font:inherit;cursor:pointer";
  close.addEventListener("click", () => {
    bar.remove();
    try {
      sessionStorage.setItem(KEY, "1");
    } catch (_) {
      // The warning comes back on the next load.
    }
  });
  bar.append(text, close);
  // Add the bar to the html element. The app can replace the content of the body.
  (document.documentElement || document.body).appendChild(bar);
})();
