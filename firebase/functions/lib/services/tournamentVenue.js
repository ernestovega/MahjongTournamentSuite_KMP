"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isGoogleMapsHost = isGoogleMapsHost;
exports.normalizeMapUrl = normalizeMapUrl;
exports.normalizeContactEmail = normalizeContactEmail;
exports.venueRequiredProblems = venueRequiredProblems;
exports.normalizeVenue = normalizeVenue;
const httpError_1 = require("../api/httpError");
const MAX_NAME_LENGTH = 120;
const MAX_ADDRESS_LENGTH = 250;
const MAX_URL_LENGTH = 500;
const MAX_EMAIL_LENGTH = 254;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAP_HOSTS = ["google.com", "google.es", "goo.gl", "maps.app.goo.gl", "g.co"];
function text(value, field, maxLength) {
    const normalized = String(value ?? "").trim();
    if (normalized.length > maxLength)
        throw (0, httpError_1.badRequest)(`${field} must contain at most ${maxLength} characters`);
    return normalized;
}
function isGoogleMapsHost(hostname) {
    const host = hostname.toLowerCase();
    return MAP_HOSTS.some((allowed) => host === allowed || host.endsWith(`.${allowed}`));
}
/** An empty value means "no map link". A link must use https and point to Google Maps. */
function normalizeMapUrl(value) {
    const raw = text(value, "venueMapUrl", MAX_URL_LENGTH);
    if (raw.length === 0)
        return "";
    let url;
    try {
        url = new URL(raw);
    }
    catch (_error) {
        throw (0, httpError_1.badRequest)("venueMapUrl must be a valid link");
    }
    if (url.protocol !== "https:" || !isGoogleMapsHost(url.hostname)) {
        throw (0, httpError_1.badRequest)("venueMapUrl must be an https link to Google Maps");
    }
    const host = url.hostname.toLowerCase();
    const isPlainGoogleHost = /^(www\.)?google\.(com|es)$/.test(host);
    if (isPlainGoogleHost && !url.pathname.startsWith("/maps")) {
        throw (0, httpError_1.badRequest)("venueMapUrl must be an https link to Google Maps");
    }
    return url.toString();
}
/** An empty value means "no contact email". A value must look like an email address. */
function normalizeContactEmail(value) {
    const raw = text(value, "contactEmail", MAX_EMAIL_LENGTH);
    if (raw.length > 0 && !EMAIL_PATTERN.test(raw))
        throw (0, httpError_1.badRequest)("contactEmail must be a valid email address");
    return raw;
}
/**
 * A tournament needs a venue and a contact email. The venue is a Google Maps link, or a name and an address.
 * Returns one message for each missing part. The list is empty when the venue is complete.
 */
function venueRequiredProblems(venue) {
    const problems = [];
    if (venue.venueMapUrl === "" && (venue.venueName === "" || venue.venueAddress === "")) {
        problems.push("Required: a Google Maps link (venueMapUrl), or a venue name and an address");
    }
    if (venue.contactEmail === "")
        problems.push("Required");
    return problems;
}
/** The venue of the tournament. Every field is optional, so a missing field becomes an empty text. */
function normalizeVenue(input) {
    return {
        venueName: text(input?.venueName, "venueName", MAX_NAME_LENGTH),
        venueAddress: text(input?.venueAddress, "venueAddress", MAX_ADDRESS_LENGTH),
        venueMapUrl: normalizeMapUrl(input?.venueMapUrl),
        contactEmail: normalizeContactEmail(input?.contactEmail),
    };
}
//# sourceMappingURL=tournamentVenue.js.map