import { defineSecret } from "firebase-functions/params";

// Secret names must not start with reserved prefixes (e.g. FIREBASE_).
export const ID_TOOLKIT_API_KEY = defineSecret("MTS_ID_TOOLKIT_API_KEY");
export const BOOTSTRAP_KEY = defineSecret("MTS_BOOTSTRAP_KEY");
