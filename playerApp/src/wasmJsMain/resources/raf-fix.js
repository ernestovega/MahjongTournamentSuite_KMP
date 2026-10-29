// Workaround for Safari 27. The page stays blank when the app gives its own function to requestAnimationFrame.
// A JavaScript function around the call makes the screen draw. Load this file before the app.
(function () {
  const raf = window.requestAnimationFrame.bind(window);
  window.requestAnimationFrame = function (callback) {
    return raf(function (time) {
      callback(time);
    });
  };
})();
