"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveMapLink = resolveMapLink;
const httpError_1 = require("../api/httpError");
const tournamentVenue_1 = require("./tournamentVenue");
const MAX_REDIRECTS = 5;
const REQUEST_TIMEOUT_MS = 5000;
/** A short link only redirects. These hosts have no place name in the link itself. */
const SHORT_LINK_HOSTS = ["goo.gl", "maps.app.goo.gl", "g.co"];
function isShortLinkHost(hostname) {
    const host = hostname.toLowerCase();
    return SHORT_LINK_HOSTS.some((short) => host === short || host.endsWith(`.${short}`));
}
/** Google can send a consent page first. The real link is in its "continue" parameter. */
function unwrapConsent(url) {
    if (!/^consent\.google\.[a-z.]+$/i.test(url.hostname))
        return null;
    return url.searchParams.get("continue");
}
async function fetchRedirect(url) {
    const response = await fetch(url, {
        redirect: "manual",
        headers: { "accept-language": "en", "user-agent": "Mozilla/5.0 (compatible; MahjongTournamentSuite)" },
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    return { status: response.status, location: response.headers.get("location") };
}
/**
 * Follows a short Google Maps link and returns the full Google Maps link. The full link holds the place name
 * and the address. The function follows only links to Google Maps, so a user cannot make the server call other hosts.
 */
async function resolveMapLink(rawUrl, fetcher = fetchRedirect) {
    let current = (0, tournamentVenue_1.normalizeMapUrl)(rawUrl);
    if (current.length === 0)
        throw (0, httpError_1.badRequest)("url is required");
    for (let hop = 0; hop <= MAX_REDIRECTS; hop += 1) {
        const url = new URL(current);
        if (!isShortLinkHost(url.hostname))
            return current;
        if (hop === MAX_REDIRECTS)
            break;
        let step;
        try {
            step = await fetcher(current);
        }
        catch (_error) {
            throw (0, httpError_1.badRequest)("The map link could not be opened");
        }
        if (step.status < 300 || step.status > 399 || !step.location)
            break;
        const next = new URL(step.location, current);
        const unwrapped = unwrapConsent(next);
        // normalizeMapUrl accepts only https links to Google Maps.
        current = (0, tournamentVenue_1.normalizeMapUrl)(unwrapped ?? next.toString());
    }
    throw (0, httpError_1.badRequest)("The map link could not be read");
}
//# sourceMappingURL=mapLinkResolver.js.map