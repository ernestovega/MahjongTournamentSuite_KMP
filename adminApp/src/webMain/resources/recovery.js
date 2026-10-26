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
  const indicator = document.createElement("div");
  indicator.style.cssText = "position:fixed;top:0;left:50%;width:36px;height:36px;margin-left:-18px;" +
    "border-radius:50%;background:#fff;box-shadow:0 2px 6px rgba(0,0,0,.35);z-index:10;" +
    "display:none;pointer-events:none;align-items:center;justify-content:center;font:20px sans-serif;color:#333";
  indicator.textContent = "↻";
  document.addEventListener("DOMContentLoaded", () => document.body.appendChild(indicator));

  const hide = () => {
    indicator.style.display = "none";
    startY = null;
    pulled = 0;
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
      const shown = Math.min(pulled, TRIGGER_PX);
      indicator.style.display = "flex";
      indicator.style.transform = "translateY(" + (shown - 36) + "px) rotate(" + shown * 2 + "deg)";
      indicator.style.opacity = String(shown / TRIGGER_PX);
    } else {
      indicator.style.display = "none";
    }
  }, { capture: true, passive: true });

  window.addEventListener("touchend", () => {
    const done = pulled >= TRIGGER_PX;
    hide();
    if (done) reload();
  }, { capture: true, passive: true });
  window.addEventListener("touchcancel", hide, { capture: true, passive: true });
})();
