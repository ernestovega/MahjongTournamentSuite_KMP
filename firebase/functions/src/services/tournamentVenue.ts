import { badRequest } from "../api/httpError";

export type TournamentVenue = {
  venueName: string;
  venueAddress: string;
  venueMapUrl: string;
  contactEmail: string;
};

const MAX_NAME_LENGTH = 120;
const MAX_ADDRESS_LENGTH = 250;
const MAX_URL_LENGTH = 500;
const MAX_EMAIL_LENGTH = 254;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAP_HOSTS = ["google.com", "google.es", "goo.gl", "maps.app.goo.gl", "g.co"];

function text(value: unknown, field: string, maxLength: number): string {
  const normalized = String(value ?? "").trim();
  if (normalized.length > maxLength) throw badRequest(`${field} must contain at most ${maxLength} characters`);
  return normalized;
}

function isGoogleMapsHost(hostname: string): boolean {
  const host = hostname.toLowerCase();
  return MAP_HOSTS.some((allowed) => host === allowed || host.endsWith(`.${allowed}`));
}

/** An empty value means "no map link". A link must use https and point to Google Maps. */
export function normalizeMapUrl(value: unknown): string {
  const raw = text(value, "venueMapUrl", MAX_URL_LENGTH);
  if (raw.length === 0) return "";
  let url: URL;
  try {
    url = new URL(raw);
  } catch (_error) {
    throw badRequest("venueMapUrl must be a valid link");
  }
  if (url.protocol !== "https:" || !isGoogleMapsHost(url.hostname)) {
    throw badRequest("venueMapUrl must be an https link to Google Maps");
  }
  const host = url.hostname.toLowerCase();
  const isPlainGoogleHost = /^(www\.)?google\.(com|es)$/.test(host);
  if (isPlainGoogleHost && !url.pathname.startsWith("/maps")) {
    throw badRequest("venueMapUrl must be an https link to Google Maps");
  }
  return url.toString();
}

/** An empty value means "no contact email". A value must look like an email address. */
export function normalizeContactEmail(value: unknown): string {
  const raw = text(value, "contactEmail", MAX_EMAIL_LENGTH);
  if (raw.length > 0 && !EMAIL_PATTERN.test(raw)) throw badRequest("contactEmail must be a valid email address");
  return raw;
}

/** The venue of the tournament. Every field is optional, so a missing field becomes an empty text. */
export function normalizeVenue(input: Record<string, unknown> | null | undefined): TournamentVenue {
  return {
    venueName: text(input?.venueName, "venueName", MAX_NAME_LENGTH),
    venueAddress: text(input?.venueAddress, "venueAddress", MAX_ADDRESS_LENGTH),
    venueMapUrl: normalizeMapUrl(input?.venueMapUrl),
    contactEmail: normalizeContactEmail(input?.contactEmail),
  };
}
