import { onRequest } from "firebase-functions/v2/https";
import { onTaskDispatched } from "firebase-functions/v2/tasks";

import { buildApp } from "./api/app";
import { closeRoundTables } from "./services/matchTimeCloseService";
import { CLOSE_ROUND_TASK_REGION } from "./services/matchTimeTaskService";
import { ID_TOOLKIT_API_KEY, MAIL_SMTP_URI, PLACES_API_KEY, SUPPORT_EMAIL } from "./config";

const app = buildApp();

/** The API runs in Madrid only. The SMTP server of the mailbox accepts connections only from some countries. */
export const api = onRequest(
  {
    region: "europe-southwest1",
    // The code needs about 170 MB just to load. The default of 256 MiB is too small and the container is killed at start.
    memory: "512MiB",
    // A brake on the bill: the public routes are open to everyone, and a flood of requests must not scale without end.
    maxInstances: 20,
    secrets: [ID_TOOLKIT_API_KEY, MAIL_SMTP_URI, PLACES_API_KEY, SUPPORT_EMAIL],
  },
  app,
);

/**
 * Completes the tables of a round when its match time is over, so rankings and statistics count them without an
 * organizer. Cloud Tasks runs it at the end of each round, as the clock of the round (the Play of the Timer) sets it.
 * It stays in Belgium because Cloud Tasks does not exist in europe-southwest1. It sends no email.
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
