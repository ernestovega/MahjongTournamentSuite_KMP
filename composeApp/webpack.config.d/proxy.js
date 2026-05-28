// Dev-only proxy to avoid CORS when calling Cloud Functions from the browser.
// Requests to http://localhost:8080/api/* will be proxied to:
//   https://europe-west1-mahjong-tournament-suite.cloudfunctions.net/api/*
//
// This is used together with the wasmJs `functionsBaseUrlPlatform()` = "/api".

config.devServer = config.devServer || {};

// webpack-dev-server v5 expects `devServer.proxy` to be an array of proxy configs.
// (Kotlin/Wasm tooling currently wires a schema that enforces the array form.)
const existingProxy = config.devServer.proxy;
config.devServer.proxy = Array.isArray(existingProxy) ? existingProxy : [];

config.devServer.proxy.push({
  context: ["/api"],
  target: "https://europe-west1-mahjong-tournament-suite.cloudfunctions.net",
  changeOrigin: true,
  secure: true,
});
