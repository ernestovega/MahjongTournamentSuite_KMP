"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.planVenueBackfill = planVenueBackfill;
const tournamentVenue_1 = require("./tournamentVenue");
/**
 * The fields that a tournament needs to meet the venue rules, or null when it already meets them.
 * A missing venue becomes a Google Maps search link for the host city. A missing contact email
 * becomes the email of the creator. Fields that are set stay as they are.
 * The result lists "unresolved" parts that the script cannot fill, so a person must fix them.
 */
function planVenueBackfill(input) {
    if ((0, tournamentVenue_1.venueRequiredProblems)(input.venue).length === 0)
        return null;
    const update = {};
    const unresolved = [];
    const hasVenue = input.venue.venueMapUrl !== "" || (input.venue.venueName !== "" && input.venue.venueAddress !== "");
    if (!hasVenue) {
        const city = input.hostCity.trim();
        if (city === "") {
            unresolved.push("venue");
        }
        else {
            const query = encodeURIComponent(`${city}, ${input.hostCountry.trim()}`.replace(/,\s*$/, ""));
            update.venueMapUrl = `https://www.google.com/maps/search/?api=1&query=${query}`;
        }
    }
    if (input.venue.contactEmail === "") {
        if (input.creatorEmail === "")
            unresolved.push("contactEmail");
        else
            update.contactEmail = input.creatorEmail;
    }
    return { update, unresolved };
}
//# sourceMappingURL=venueBackfill.js.map