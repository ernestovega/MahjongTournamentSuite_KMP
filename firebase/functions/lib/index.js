"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.api = void 0;
const https_1 = require("firebase-functions/v2/https");
const app_1 = require("./api/app");
const config_1 = require("./config");
const app = (0, app_1.buildApp)();
exports.api = (0, https_1.onRequest)({
    region: "europe-west1",
    secrets: [config_1.ID_TOOLKIT_API_KEY, config_1.BOOTSTRAP_KEY],
}, app);
//# sourceMappingURL=index.js.map