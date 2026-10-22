"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.closeExpiredTablesJob = exports.api = void 0;
const https_1 = require("firebase-functions/v2/https");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const app_1 = require("./api/app");
const matchTimeCloseService_1 = require("./services/matchTimeCloseService");
const config_1 = require("./config");
const app = (0, app_1.buildApp)();
exports.api = (0, https_1.onRequest)({
    region: "europe-west1",
    secrets: [config_1.ID_TOOLKIT_API_KEY, config_1.BOOTSTRAP_KEY, config_1.MAIL_SMTP_URI],
}, app);
/** Completes the tables whose match time is over, so rankings and statistics count them without an organizer. */
exports.closeExpiredTablesJob = (0, scheduler_1.onSchedule)({ region: "europe-west1", schedule: "every 5 minutes", timeZone: "UTC" }, async () => {
    await (0, matchTimeCloseService_1.closeExpiredTables)();
});
//# sourceMappingURL=index.js.map