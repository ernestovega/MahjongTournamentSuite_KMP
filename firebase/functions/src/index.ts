import { onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onTaskDispatched } from "firebase-functions/v2/tasks";

import { buildApp } from "./api/app";
import { closeRoundTables } from "./services/matchTimeCloseService";
import { CLOSE_ROUND_TASK_REGION, planRoundCloses } from "./services/matchTimeTaskService";
import { ID_TOOLKIT_API_KEY, MAIL_SMTP_URI, PLACES_API_KEY } from "./config";

const app = buildApp();

/** The API runs in Madrid only. The SMTP server of the mailbox accepts connections only from some countries. */
export const api = onRequest(
  {
    region: "europe-southwest1",
    // The code needs about 170 MB just to load. The default of 256 MiB is too small and the container is killed at start.
    memory: "512MiB",
    secrets: [ID_TOOLKIT_API_KEY, MAIL_SMTP_URI, PLACES_API_KEY],
  },
  app,
);

/**
 * Completes the tables of a round when its match time is over, so rankings and statistics count them without an
 * organizer. Cloud Tasks runs it once for each round, at the end of the round. It stays in Belgium because
 * Cloud Tasks and Cloud Scheduler do not exist in europe-southwest1. It sends no email.
 */
export const closeRoundTablesTask = onTaskDispatched(
  {
    region: CLOSE_ROUND_TASK_REGION,
    memory: "512MiB",
    retryConfig: { maxAttempts: 5, minBackoffSeconds: 30 },
    rateLimits: { maxConcurrentDispatches: 5 },
  },
  async (request) => {
    const { tournamentId, roundId } = request.data as { tournamentId?: string; roundId?: number };
    if (typeof tournamentId !== "string" || !Number.isInteger(roundId)) return;
    await closeRoundTables(tournamentId, Number(roundId));
  },
);

/**
 * Plans the close tasks of the rounds that end in the next 30 days. Cloud Tasks does not accept a later time,
 * so a tournament made long before its date gets its tasks from this job. Running it again is safe.
 */
export const planRoundClosesJob = onSchedule(
  { region: CLOSE_ROUND_TASK_REGION, schedule: "every day 03:00", timeZone: "UTC", memory: "512MiB" },
  async () => {
    await planRoundCloses();
  },
);
