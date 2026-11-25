import { onRequest } from "firebase-functions/v2/https";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onSchedule } from "firebase-functions/v2/scheduler";

import { buildApp } from "./api/app";
import { closeRoundTables } from "./services/matchTimeCloseService";
import { runRegistrationsHour } from "./services/registrationJobs";
import { CLOSE_ROUND_TASK_REGION } from "./services/matchTimeTaskService";
import {
  callRegistrationsHour,
  REGISTRATIONS_HOUR_REGION,
  registrationsHourHandler,
} from "./services/registrationHourRelay";
import { ID_TOOLKIT_API_KEY, MAIL_SMTP_URI, PLACES_API_KEY } from "./config";
import { shouldWarmUp, warmUpFirestore } from "./services/startupWarmup";

const app = buildApp();
if (shouldWarmUp(process.env)) void warmUpFirestore();

/** The API runs in Madrid only. The SMTP server of the mailbox accepts connections only from some countries. */
export const api = onRequest(
  {
    region: "europe-southwest1",
    // The code needs about 170 MB just to load. The default of 256 MiB is too small and the container is killed at start.
    memory: "512MiB",
    // A whole CPU: below 1 CPU an instance serves one request at a time, and with the phones of a tournament reading
    // every 2 s the saves of hands waited in the queue (p95 about 2 s in the game day of 2026-11-18). With 1 CPU an
    // instance serves many requests at once, and fewer instances cost about the same.
    cpu: 1,
    concurrency: 80,
    // A brake on the bill: the public routes are open to everyone, and a flood of requests must not scale without end.
    maxInstances: 20,
    // The exports (ID cards, ID list, EMA report) and the creation of a big tournament can take long. The apps wait up
    // to 180 s for them. Firebase Hosting still cuts a request of the web apps at 60 s.
    timeoutSeconds: 180,
    // MTS_SUPPORT_EMAIL is the mailbox of the "Need help?" form. A deploy fails when a bound secret is missing.
    secrets: [ID_TOOLKIT_API_KEY, MAIL_SMTP_URI, PLACES_API_KEY, "MTS_SUPPORT_EMAIL"],
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

/**
 * The hourly work of the registrations of tournaments without a draw: it removes the pending registrations whose
 * link is too old, closes the registrations after their last day, sends the team reminders one week before the close
 * and the weekly report to the organization on Sunday evening. See docs/registrations.md.
 * Cloud Scheduler does not exist in europe-southwest1, so the schedule runs in Belgium and only calls
 * registrationsHourRun, which does the work in Madrid, like the API, because the SMTP server of the mailbox accepts
 * connections only from some countries.
 */
export const registrationsHourly = onSchedule(
  {
    schedule: "5 * * * *",
    timeZone: "Etc/UTC",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 540,
    retryCount: 0,
  },
  async () => {
    const answer = (await callRegistrationsHour()) as { results?: unknown[] } | undefined;
    if ((answer?.results?.length ?? 0) > 0) console.log("Registrations hour", JSON.stringify(answer?.results));
  },
);

/** The work of registrationsHourly in Madrid. Only the service account of the functions can call it. */
export const registrationsHourRun = onRequest(
  {
    region: REGISTRATIONS_HOUR_REGION,
    memory: "512MiB",
    timeoutSeconds: 540,
    maxInstances: 1,
    secrets: [MAIL_SMTP_URI],
  },
  registrationsHourHandler(() => runRegistrationsHour(new Date())),
);
