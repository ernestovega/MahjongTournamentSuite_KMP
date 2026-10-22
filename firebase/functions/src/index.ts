import { onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";

import { buildApp } from "./api/app";
import { closeExpiredTables } from "./services/matchTimeCloseService";
import { BOOTSTRAP_KEY, ID_TOOLKIT_API_KEY, MAIL_SMTP_URI } from "./config";

const app = buildApp();

export const api = onRequest(
  {
    region: "europe-west1",
    secrets: [ID_TOOLKIT_API_KEY, BOOTSTRAP_KEY, MAIL_SMTP_URI],
  },
  app,
);

/** Completes the tables whose match time is over, so rankings and statistics count them without an organizer. */
export const closeExpiredTablesJob = onSchedule(
  { region: "europe-west1", schedule: "every 5 minutes", timeZone: "UTC" },
  async () => {
    await closeExpiredTables();
  },
);
