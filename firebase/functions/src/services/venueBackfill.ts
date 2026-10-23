import { venueRequiredProblems, type TournamentVenue } from "./tournamentVenue";

export type VenueBackfillInput = {
  hostCity: string;
  hostCountry: string;
  venue: TournamentVenue;
  /** Email of the account that created the tournament, or an empty text when it is not known. */
  creatorEmail: string;
};

/**
 * The fields that a tournament needs to meet the venue rules, or null when it already meets them.
 * A missing venue becomes a Google Maps search link for the host city. A missing contact email
 * becomes the email of the creator. Fields that are set stay as they are.
 * The result lists "unresolved" parts that the script cannot fill, so a person must fix them.
 */
export function planVenueBackfill(input: VenueBackfillInput): { update: Partial<TournamentVenue>; unresolved: string[] } | null {
  if (venueRequiredProblems(input.venue).length === 0) return null;
  const update: Partial<TournamentVenue> = {};
  const unresolved: string[] = [];
  const hasVenue = input.venue.venueMapUrl !== "" || (input.venue.venueName !== "" && input.venue.venueAddress !== "");
  if (!hasVenue) {
    const city = input.hostCity.trim();
    if (city === "") {
      unresolved.push("venue");
    } else {
      const query = encodeURIComponent(`${city}, ${input.hostCountry.trim()}`.replace(/,\s*$/, ""));
      update.venueMapUrl = `https://www.google.com/maps/search/?api=1&query=${query}`;
    }
  }
  if (input.venue.contactEmail === "") {
    if (input.creatorEmail === "") unresolved.push("contactEmail");
    else update.contactEmail = input.creatorEmail;
  }
  return { update, unresolved };
}
