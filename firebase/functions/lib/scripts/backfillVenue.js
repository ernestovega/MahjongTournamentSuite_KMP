"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const firebase_1 = require("../firebase");
const venueBackfill_1 = require("../services/venueBackfill");
/**
 * Gives every tournament a venue and a contact email, as the server now requires.
 * A missing venue becomes a Google Maps search link for the host city. A missing contact email becomes
 * the email of the creator. It previews by default. Add --apply to write.
 * Tournaments that it cannot fix are listed. A person must edit them in the admin app.
 */
const applyChanges = process.argv.includes("--apply");
async function creatorEmail(uid, cache) {
    if (!uid)
        return "";
    const cached = cache.get(uid);
    if (cached !== undefined)
        return cached;
    let email = "";
    try {
        email = (await firebase_1.auth.getUser(uid)).email ?? "";
    }
    catch (_error) {
        email = String((await firebase_1.db.collection("users").doc(uid).get()).get("email") ?? "");
    }
    cache.set(uid, email);
    return email;
}
async function main() {
    console.log(`${applyChanges ? "Applying" : "Previewing"} venue backfill for ${firebase_1.firebaseProjectId}.`);
    const tournaments = await firebase_1.db.collection("tournaments").get();
    const emails = new Map();
    let changedCount = 0;
    const unresolvedTournaments = [];
    for (const tournament of tournaments.docs) {
        const venue = {
            venueName: String(tournament.get("venueName") ?? "").trim(),
            venueAddress: String(tournament.get("venueAddress") ?? "").trim(),
            venueMapUrl: String(tournament.get("venueMapUrl") ?? "").trim(),
            contactEmail: String(tournament.get("contactEmail") ?? "").trim(),
        };
        const needsEmail = venue.contactEmail === "";
        // eslint-disable-next-line no-await-in-loop
        const email = needsEmail ? await creatorEmail(tournament.get("createdByUid"), emails) : "";
        const plan = (0, venueBackfill_1.planVenueBackfill)({
            hostCity: String(tournament.get("hostCity") ?? ""),
            hostCountry: String(tournament.get("hostCountry") ?? ""),
            venue,
            creatorEmail: email,
        });
        if (plan === null)
            continue;
        changedCount += 1;
        console.log(`${tournament.id}: set ${JSON.stringify(plan.update)}${plan.unresolved.length ? `, unresolved ${plan.unresolved.join(", ")}` : ""}`);
        if (plan.unresolved.length > 0)
            unresolvedTournaments.push(tournament.id);
        if (!applyChanges || Object.keys(plan.update).length === 0)
            continue;
        // eslint-disable-next-line no-await-in-loop
        await tournament.ref.update(plan.update);
    }
    console.log(`${changedCount} of ${tournaments.size} tournaments ${applyChanges ? "changed" : "would change"}.`);
    if (unresolvedTournaments.length > 0) {
        console.log(`${unresolvedTournaments.length} tournaments need a person to edit them: ${unresolvedTournaments.join(", ")}`);
    }
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
//# sourceMappingURL=backfillVenue.js.map