import { defineSecret } from "firebase-functions/params";

// Secret names must not start with reserved prefixes (e.g. FIREBASE_).
export const ID_TOOLKIT_API_KEY = defineSecret("MTS_ID_TOOLKIT_API_KEY");
// The key of Google Places (New). The server uses it to read the place of a Google Maps link.
export const PLACES_API_KEY = defineSecret("PLACES_API_KEY");
