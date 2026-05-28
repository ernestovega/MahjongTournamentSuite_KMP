import { onRequest } from "firebase-functions/v2/https";

import { buildApp } from "./api/app";
import { BOOTSTRAP_KEY, ID_TOOLKIT_API_KEY } from "./config";

const app = buildApp();

export const api = onRequest(
  {
    region: "europe-west1",
    secrets: [ID_TOOLKIT_API_KEY, BOOTSTRAP_KEY],
  },
  app,
);
