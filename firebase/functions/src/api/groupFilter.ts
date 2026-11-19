import { badRequest } from "./httpError";
import type { GroupFilter } from "../services/statisticsGroups";

/** The most tournaments that one group request can name. */
export const MAX_GROUP_TOURNAMENTS = 200;

/** The value of the player country that keeps the players without a country. */
export const NO_COUNTRY = "none";

const one = (value: unknown): string | undefined => {
  if (value == null) return undefined;
  if (Array.isArray(value)) throw badRequest("A filter is given twice");
  return String(value).trim();
};

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Reads the filter of a statistics group from the query:
 * `tournaments` (ids, comma separated), `tournamentCountry` (two-letter ISO code), `playerCountry` (EMA code, or
 * `none` for the players without a country), `from` and `to` (ISO dates of the first day of the tournament).
 */
export function readGroupFilter(query: Record<string, unknown>): GroupFilter {
  const filter: GroupFilter = {};
  const tournaments = one(query.tournaments);
  if (tournaments != null && tournaments !== "") {
    const ids = [...new Set(tournaments.split(",").map((id) => id.trim()).filter((id) => id !== ""))];
    if (ids.length > MAX_GROUP_TOURNAMENTS) throw badRequest("Too many tournaments", { max: MAX_GROUP_TOURNAMENTS });
    filter.tournamentIds = ids;
  }
  const tournamentCountry = one(query.tournamentCountry)?.toUpperCase();
  if (tournamentCountry != null && tournamentCountry !== "") {
    if (!/^[A-Z]{2}$/.test(tournamentCountry)) throw badRequest("Invalid tournament country");
    filter.tournamentCountry = tournamentCountry;
  }
  const playerCountry = one(query.playerCountry);
  if (playerCountry != null && playerCountry !== "") {
    if (playerCountry.toLowerCase() === NO_COUNTRY) {
      filter.playerCountry = "";
    } else {
      if (!/^[A-Za-z]{3}$/.test(playerCountry)) throw badRequest("Invalid player country");
      filter.playerCountry = playerCountry.toUpperCase();
    }
  }
  for (const key of ["from", "to"] as const) {
    const value = one(query[key]);
    if (value == null || value === "") continue;
    if (!ISO_DATE.test(value)) throw badRequest(`Invalid ${key} date`);
    filter[key] = value;
  }
  if (filter.from != null && filter.to != null && filter.from > filter.to) throw badRequest("The from date is after the to date");
  return filter;
}
