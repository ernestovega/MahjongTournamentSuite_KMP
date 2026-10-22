"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.planRoundClosesJob = exports.closeRoundTablesTask = exports.api = void 0;
const https_1 = require("firebase-functions/v2/https");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const tasks_1 = require("firebase-functions/v2/tasks");
const app_1 = require("./api/app");
const matchTimeCloseService_1 = require("./services/matchTimeCloseService");
const matchTimeTaskService_1 = require("./services/matchTimeTaskService");
const config_1 = require("./config");
const app = (0, app_1.buildApp)();
/** The API runs in Madrid only. The SMTP server of the mailbox accepts connections only from some countries. */
exports.api = (0, https_1.onRequest)({
    region: "europe-southwest1",
    // The code needs about 170 MB just to load. The default of 256 MiB is too small and the container is killed at start.
    memory: "512MiB",
    secrets: [config_1.ID_TOOLKIT_API_KEY, config_1.BOOTSTRAP_KEY, config_1.MAIL_SMTP_URI],
}, app);
/**
 * Completes the tables of a round when its match time is over, so rankings and statistics count them without an
 * organizer. Cloud Tasks runs it once for each round, at the end of the round. It stays in Belgium because
 * Cloud Tasks and Cloud Scheduler do not exist in europe-southwest1. It sends no email.
 */
exports.closeRoundTablesTask = (0, tasks_1.onTaskDispatched)({
    region: matchTimeTaskService_1.CLOSE_ROUND_TASK_REGION,
    memory: "512MiB",
    retryConfig: { maxAttempts: 5, minBackoffSeconds: 30 },
    rateLimits: { maxConcurrentDispatches: 5 },
}, async (request) => {
    const { tournamentId, roundId } = request.data;
    if (typeof tournamentId !== "string" || !Number.isInteger(roundId))
        return;
    await (0, matchTimeCloseService_1.closeRoundTables)(tournamentId, Number(roundId));
});
/**
 * Plans the close tasks of the rounds that end in the next 30 days. Cloud Tasks does not accept a later time,
 * so a tournament made long before its date gets its tasks from this job. Running it again is safe.
 */
exports.planRoundClosesJob = (0, scheduler_1.onSchedule)({ region: matchTimeTaskService_1.CLOSE_ROUND_TASK_REGION, schedule: "every day 03:00", timeZone: "UTC", memory: "512MiB" }, async () => {
    await (0, matchTimeTaskService_1.planRoundCloses)();
});
//# sourceMappingURL=index.js.map