import { badRequest, conflict } from "../api/httpError";
import { isValidIsoDate } from "./tournamentDates";

/**
 * The registrations of a tournament, before its draw. See docs/registrations.md.
 * - OPEN: anyone can register. CLOSED: nobody can, and the organizer can make the draw or open them again.
 * - [maxPlayers] is the number of places. Who registers when they are full goes to the waiting list.
 * - [closesOn] is the last day to register (inclusive), a local date of the venue. The hourly job closes them after it.
 * - [slug] names the public link `/<slug>`, for example "6thmmc". It opens the registrations while they last and the
 *   tournament after the draw. The old links `/inscripcion/<slug>` still work.
 */
export type RegistrationPhase = "OPEN" | "CLOSED";

export type RegistrationSettingsView = {
  status: RegistrationPhase;
  maxPlayers: number | null;
  closesOn: string | null;
  slug: string;
  /** Registrations with a place. */
  confirmed: number;
  /** Registrations on the waiting list. */
  waiting: number;
};

export const MAX_REGISTRATION_PLAYERS = 1000;
export const MAX_SLUG_LENGTH = 40;
/** The size of a team in a tournament with teams. */
export const TEAM_SIZE = 4;
export const MAX_TEAM_NAME_LENGTH = 40;
export const MAX_FOOD_RESTRICTIONS_LENGTH = 300;
/** Hours that a registration waits for the confirmation of its email before the hourly job removes it. */
export const PENDING_HOURS = 48;
/** Days before the close when the people without a full team get a reminder. */
export const TEAM_REMINDER_DAYS = 7;
/** The weekly report goes out on Sunday from this local hour of the venue. */
export const WEEKLY_REPORT_HOUR = 21;

export function readRegistrationSettings(value: unknown): RegistrationSettingsView | null {
  if (value == null || typeof value !== "object") return null;
  const data = value as Record<string, unknown>;
  const maxPlayers = Number(data.maxPlayers);
  return {
    status: data.status === "OPEN" ? "OPEN" : "CLOSED",
    maxPlayers: Number.isInteger(maxPlayers) && maxPlayers > 0 ? maxPlayers : null,
    closesOn: typeof data.closesOn === "string" && isValidIsoDate(data.closesOn) ? data.closesOn : null,
    slug: typeof data.slug === "string" ? data.slug : "",
    confirmed: Number.isInteger(data.confirmed) ? Number(data.confirmed) : 0,
    waiting: Number.isInteger(data.waiting) ? Number(data.waiting) : 0,
  };
}

/** Turns a text into the part of a link: lower case letters, digits and single dashes. "MMC 2027!" gives "mmc-2027". */
export function slugify(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, MAX_SLUG_LENGTH)
    .replace(/-+$/g, "");
}

/**
 * The first segments of the addresses of the site, which a link name cannot take: the apps, the API, the static pages
 * and the old links. A new top-level page of the site goes here too. See docs/registrations.md.
 */
export const RESERVED_SLUGS: ReadonlySet<string> = new Set([
  "admin", "api", "api-docs", "version", "privacy", "support", "green-book", "t", "r", "registration", "registrations",
  "inscripcion", "inscripciones", "manage", "signin", "login", "logout", "help", "about", "terms", "contact", "static",
  "assets", "index", "player", "tournament", "tournaments", "stats", "rules", "www",
]);

/** The link name without dashes or spaces: "6th MMC" gives "6thmmc". */
function compactSlug(value: string): string {
  return slugify(value).replace(/-/g, "");
}

/**
 * The link names that the app proposes, shortest first: the short name of the tournament without spaces ("6thmmc"),
 * then with the year of its start ("6thmmc2027") for a short name that another tournament already uses.
 */
export function defaultSlugs(shortName: string, eventStartDate: string | null): string[] {
  const base = compactSlug(shortName);
  const year = eventStartDate != null && isValidIsoDate(eventStartDate) ? eventStartDate.slice(0, 4) : "";
  const withYear = base.endsWith(year) ? base : `${base}${year}`.slice(0, MAX_SLUG_LENGTH);
  const slugs = [...new Set([base, withYear])].filter((slug) => slug.length >= 3 && !RESERVED_SLUGS.has(slug));
  // A very short name ("MO") without a date: a name that is still short and free of the reserved words.
  return slugs.length > 0 ? slugs : [`${base}mahjong`.slice(0, MAX_SLUG_LENGTH)];
}

export function parseSlug(value: unknown): string {
  const raw = String(value ?? "").trim();
  const slug = slugify(raw);
  if (slug.length < 3 || slug !== raw.toLowerCase()) {
    throw badRequest("The link name needs 3 to 40 lower case letters, digits or dashes", { code: "INVALID_SLUG", value: raw });
  }
  if (RESERVED_SLUGS.has(slug)) throw conflict("The site uses this link name", { code: "SLUG_TAKEN", value: slug });
  return slug;
}

export function parseMaxPlayers(value: unknown): number | null {
  if (value == null || value === "") return null;
  const number = Number(value);
  if (!Number.isInteger(number) || number < 4 || number > MAX_REGISTRATION_PLAYERS) {
    throw badRequest(`The places must be a whole number from 4 to ${MAX_REGISTRATION_PLAYERS}`, { code: "INVALID_MAX_PLAYERS" });
  }
  return number;
}

export function parseClosesOn(value: unknown): string | null {
  if (value == null || value === "") return null;
  const text = String(value).trim();
  if (!isValidIsoDate(text)) throw badRequest("The close date must use yyyy-MM-dd", { code: "INVALID_CLOSE_DATE" });
  return text;
}

export function parsePhase(value: unknown): RegistrationPhase {
  if (value === "OPEN" || value === "CLOSED") return value;
  throw badRequest("The state of the registrations must be OPEN or CLOSED");
}

/** A team name, with single spaces. Throws for an empty or long name. */
export function parseTeamName(value: unknown): string {
  const name = String(value ?? "").trim().replace(/\s+/g, " ");
  if (name.length === 0 || name.length > MAX_TEAM_NAME_LENGTH) {
    throw badRequest(`A team name needs 1 to ${MAX_TEAM_NAME_LENGTH} characters`, { code: "INVALID_TEAM_NAME" });
  }
  return name;
}

const NO_RESTRICTIONS = new Set([
  "", "-", "--", ".", "no", "non", "nein", "nej", "nee", "нет", "none", "nothing", "nada", "ninguna", "ninguno", "niente",
  "nessuna", "nessuno", "aucune", "aucun", "rien", "keine", "inga", "geen", "n/a", "na", "/", "0",
]);

/** The food restrictions as the person wrote them, or "" when the text says that there are none ("no", "ninguna"...). */
export function normalizeFoodRestrictions(value: unknown): string {
  const text = String(value ?? "").trim().replace(/\s+/g, " ");
  if (NO_RESTRICTIONS.has(text.toLowerCase().replace(/[.!]+$/, ""))) return "";
  if (text.length > MAX_FOOD_RESTRICTIONS_LENGTH) {
    throw badRequest(`The food restrictions take at most ${MAX_FOOD_RESTRICTIONS_LENGTH} characters`, { code: "FOOD_TOO_LONG" });
  }
  return text;
}

/** Team values of the old forms that mean "no team yet". */
const NO_TEAM = new Set(["", "-", "?", "??", "tbd", "tba", "(pending)", "pending", "no", "none", "ninguno", "-/-", "n/a"]);

export function teamFromForm(value: unknown): string | null {
  const text = String(value ?? "").trim().replace(/\s+/g, " ");
  return NO_TEAM.has(text.toLowerCase()) ? null : text.slice(0, MAX_TEAM_NAME_LENGTH);
}

const EMAIL_PATTERN = /^[^\s@<>()[\]\\,;:"]+@[^\s@<>()[\]\\,;:"]+\.[^\s@<>()[\]\\,;:"]+$/;

export function isValidEmail(value: string): boolean {
  return value.length <= 254 && EMAIL_PATTERN.test(value);
}

/** The parts of a date and time in a time zone. */
export type LocalTime = { date: string; hour: number; weekday: number };

/** The local date (yyyy-MM-dd), hour (0-23) and weekday (0 is Sunday) of [instant] in [timeZone]. Europe/Madrid by default. */
export function localTime(instant: Date, timeZone: string | null): LocalTime {
  const zone = timeZone ?? "Europe/Madrid";
  let parts: Intl.DateTimeFormatPart[];
  try {
    parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", hourCycle: "h23", weekday: "short",
    }).formatToParts(instant);
  } catch {
    return localTime(instant, "Europe/Madrid");
  }
  // The format above always gives each of these parts.
  const part = (type: string) => parts.find((item) => item.type === type)!.value;
  const weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  return {
    date: `${part("year")}-${part("month")}-${part("day")}`,
    hour: Number(part("hour")) % 24,
    weekday: Math.max(0, weekdays.indexOf(part("weekday"))),
  };
}

/** The date [days] days after an ISO date. */
export function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** What the hourly job does with one tournament with open registrations. */
export type HourlyDecision = {
  /** The last day to register is over. */
  close: boolean;
  /** One week before the close: the people without a full team get a reminder, once. */
  teamReminder: boolean;
  /** Sunday evening: the organization gets the weekly report, once a day, until the draw. */
  weeklyReport: boolean;
};

export function hourlyDecision(params: {
  now: LocalTime;
  status: RegistrationPhase;
  closesOn: string | null;
  isTeams: boolean;
  teamReminderSentFor: string | null;
  lastWeeklyReportOn: string | null;
  hasContact: boolean;
}): HourlyDecision {
  const open = params.status === "OPEN";
  const close = open && params.closesOn != null && params.now.date > params.closesOn;
  const reminderDay = params.closesOn == null ? null : addDays(params.closesOn, -TEAM_REMINDER_DAYS);
  const teamReminder = open && !close && params.isTeams && reminderDay != null
    && params.now.date >= reminderDay && params.now.hour >= 10 && params.teamReminderSentFor !== params.closesOn;
  // The report goes on after the close, until the draw: people still leave and the organizer still moves places.
  const weeklyReport = params.hasContact && params.now.weekday === 0 && params.now.hour >= WEEKLY_REPORT_HOUR
    && params.lastWeeklyReportOn !== params.now.date;
  return { close, teamReminder, weeklyReport };
}
