// Recovery for a tab that stays in the background on a phone.
// The browser can free the graphics memory of a hidden tab. The page then stays blank.
// The app keeps its route in the session storage, so a reload is safe.
(function () {
  const MAX_HIDDEN_MS = 30 * 60 * 1000;
  let hiddenAt = null;

  const reload = () => window.location.reload();

  // Reload when the tab returns after a long time in the background.
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      hiddenAt = Date.now();
    } else if (hiddenAt !== null && Date.now() - hiddenAt > MAX_HIDDEN_MS) {
      reload();
    }
  });

  // Reload when the browser restores the page from its back/forward cache.
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) reload();
  });

  // Reload when the browser takes the graphics context away. This event does not bubble, so use the capture phase.
  document.addEventListener("webglcontextlost", (event) => {
    event.preventDefault();
    reload();
  }, true);

  // Pull to refresh. The canvas of the app takes all touch events, so the browser cannot do it.
  // The gesture starts in the top part of the screen and moves down.
  const START_ZONE_PX = 96;
  const TRIGGER_PX = 140;
  let startY = null;
  let pulled = 0;
  const SIZE = 48;
  const indicator = document.createElement("div");
  indicator.style.cssText = "position:fixed;top:0;left:50%;width:" + SIZE + "px;height:" + SIZE + "px;" +
    "margin-left:-" + SIZE / 2 + "px;border-radius:50%;background:#fff;box-shadow:0 3px 10px rgba(0,0,0,.5);" +
    "z-index:2147483647;display:none;pointer-events:none;align-items:center;justify-content:center;" +
    "font:28px sans-serif;color:#333;transition:background-color .1s,color .1s";
  // An SVG icon is centered in its own box. The text arrow is not, so it wobbles when it turns.
  indicator.innerHTML = '<svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor" style="display:block">' +
    '<path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z"/></svg>';
  const icon = indicator.firstChild;
  // Add the indicator to the html element. The app can replace the content of the body.
  (document.documentElement || document.body).appendChild(indicator);

  const hide = () => {
    indicator.style.display = "none";
    startY = null;
    pulled = 0;
  };

  const show = () => {
    const shown = Math.min(pulled, TRIGGER_PX);
    const ready = pulled >= TRIGGER_PX;
    indicator.style.display = "flex";
    indicator.style.transform = "translateY(" + (shown - SIZE + 16) + "px)";
    icon.style.transform = "rotate(" + shown * 3 + "deg)";
    indicator.style.opacity = String(Math.max(0.3, shown / TRIGGER_PX));
    indicator.style.background = ready ? "#2e7d32" : "#fff";
    indicator.style.color = ready ? "#fff" : "#333";
  };

  window.addEventListener("touchstart", (event) => {
    const touch = event.touches[0];
    startY = event.touches.length === 1 && touch.clientY < START_ZONE_PX ? touch.clientY : null;
    pulled = 0;
  }, { capture: true, passive: true });

  window.addEventListener("touchmove", (event) => {
    if (startY === null) return;
    pulled = event.touches[0].clientY - startY;
    if (pulled > 10) {
      show();
    } else {
      indicator.style.display = "none";
    }
  }, { capture: true, passive: true });

  window.addEventListener("touchend", () => {
    if (pulled >= TRIGGER_PX) {
      // Keep the indicator visible while the page reloads.
      indicator.style.transform = "translateY(16px)";
      reload();
      return;
    }
    hide();
  }, { capture: true, passive: true });
  window.addEventListener("touchcancel", hide, { capture: true, passive: true });
})();
