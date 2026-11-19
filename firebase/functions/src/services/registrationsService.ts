import { randomBytes } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest, conflict, forbidden, HttpError, notFound } from "../api/httpError";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import type { Language } from "./i18n";
import { LANGUAGES } from "./i18n";
import { sendEmail } from "./mailService";
import { EMA_PLAYER_REGISTRY_COLLECTION, toPlayer, validateEmaId } from "./playersService";
import {
  isValidEmail,
  normalizeFoodRestrictions,
  parseTeamName,
  PENDING_HOURS,
  readRegistrationSettings,
  TEAM_SIZE,
  type RegistrationSettingsView,
} from "./registrationSettings";
import { readFormRows, type FormRow } from "./registrationImport";
import {
  leftEmail,
  noEmaNumberEmail,
  organizationLanguage,
  organizationLeftEmail,
  promotedEmail,
  registeredEmail,
  removedEmail,
  teamAddedEmail,
  verifyEmail,
} from "./registrationEmails";

/**
 * The registrations of a tournament before its draw. One document per person:
 * `tournaments/{id}/registrations/{emaId}`. The EMA number is the key, so a person registers once.
 * - PENDING: registered without an account. It waits for the link of its email ([PENDING_HOURS] hours).
 * - CONFIRMED: has a place. WAITING: on the waiting list, because the places were full.
 * The food restrictions belong to the person, not to one registration: `personPrivate/{emaId}`. An account with
 * this EMA number sees them in its profile, also when the account comes later.
 * Teams: `tournaments/{id}/registrationTeams/{teamId}` with a name and a captain. The members are the
 * registrations with that teamId. The draw turns them into the teams of the tournament.
 * See docs/registrations.md.
 */
export type RegistrationStatus = "PENDING" | "CONFIRMED" | "WAITING";
export type RegistrationSource = "SELF" | "ADMIN" | "IMPORT";

export type StoredRegistration = {
  emaId: string;
  name: string;
  country: string;
  email: string;
  status: RegistrationStatus;
  /** The order of the list: the time of the registration, in milliseconds. A promotion keeps it. */
  order: number;
  paid: boolean;
  teamId: string | null;
  language: Language;
  source: RegistrationSource;
  manageToken: string;
  /** Milliseconds after which a PENDING registration is removed. */
  pendingUntil: number | null;
};

/** What everyone sees: no email, no payment, no food restrictions. */
export type PublicRegistration = {
  emaId: string;
  name: string;
  country: string;
  status: "CONFIRMED" | "WAITING";
  /** 1, 2, 3... in its list: the places or the waiting list. */
  position: number;
  teamId: string | null;
};

export type RegistrationTeamView = { id: string; name: string; captainEmaId: string; members: string[] };

export type PublicRegistrations = {
  settings: RegistrationSettingsView;
  isTeams: boolean;
  registrations: PublicRegistration[];
  teams: RegistrationTeamView[];
};

/** What the organizers see. */
export type AdminRegistration = Omit<PublicRegistration, "status"> & {
  status: RegistrationStatus;
  email: string;
  paid: boolean;
  foodRestrictions: string;
  source: RegistrationSource;
  registeredAt: string;
  hasAccount: boolean;
  /** Another registration of the list has the same email. One account cannot share an email. */
  sharedEmail: boolean;
};

export type OwnRegistration = Omit<PublicRegistration, "status"> & {
  status: RegistrationStatus;
  email: string;
  foodRestrictions: string;
  /** The token of the links of the emails. The app keeps it to manage the registration without an account. */
  manageToken: string;
};

const REGISTRATIONS = "registrations";
const TEAMS = "registrationTeams";
const LOG = "registrationLog";
const SLUGS = "registrationSlugs";
const PERSON_PRIVATE = "personPrivate";

/** A 404 with a code that the apps turn into a text. */
function missing(message: string, details: { code: string }): HttpError {
  return new HttpError(404, "not_found", message, details);
}

function playerAppUrl(): string {
  return (process.env.PLAYER_APP_URL ?? "https://mts.mahjongmadrid.com").replace(/\/$/, "");
}

/** The link of the emails: it confirms a pending registration and opens the page to manage it. */
export function manageLink(tournamentId: string, token: string): string {
  return `${playerAppUrl()}/inscripcion/gestionar?t=${encodeURIComponent(tournamentId)}&k=${encodeURIComponent(token)}`;
}

export function publicRegistrationLink(slug: string): string {
  return `${playerAppUrl()}/inscripcion/${encodeURIComponent(slug)}`;
}

function adminRegistrationsLink(tournamentId: string): string {
  return `${playerAppUrl()}/admin/?registrations=${encodeURIComponent(tournamentId)}`;
}

function newToken(): string {
  return randomBytes(24).toString("base64url");
}

function readLanguage(value: unknown): Language {
  return (LANGUAGES as readonly string[]).includes(String(value)) ? value as Language : "en";
}

function toStored(doc: FirebaseFirestore.DocumentSnapshot): StoredRegistration {
  const status = doc.get("status");
  const source = doc.get("source");
  return {
    emaId: doc.id,
    name: String(doc.get("name") ?? ""),
    country: String(doc.get("country") ?? ""),
    email: String(doc.get("email") ?? ""),
    status: status === "CONFIRMED" || status === "WAITING" ? status : "PENDING",
    order: Number(doc.get("order") ?? 0),
    paid: doc.get("paid") === true,
    teamId: typeof doc.get("teamId") === "string" && doc.get("teamId") !== "" ? String(doc.get("teamId")) : null,
    language: readLanguage(doc.get("language")),
    source: source === "ADMIN" || source === "IMPORT" ? source : "SELF",
    manageToken: String(doc.get("manageToken") ?? ""),
    pendingUntil: typeof doc.get("pendingUntil") === "number" ? Number(doc.get("pendingUntil")) : null,
  };
}

const byOrder = (left: StoredRegistration, right: StoredRegistration) => left.order - right.order || left.emaId.localeCompare(right.emaId);

/** Gives each confirmed and waiting registration its number in its list. */
export function withPositions(registrations: StoredRegistration[]): Array<StoredRegistration & { position: number }> {
  const counters: Record<RegistrationStatus, number> = { PENDING: 0, CONFIRMED: 0, WAITING: 0 };
  return [...registrations].sort(byOrder).map((registration) => ({
    ...registration,
    position: ++counters[registration.status],
  }));
}

export type TournamentForRegistrations = {
  ref: FirebaseFirestore.DocumentReference;
  name: string;
  isTeams: boolean;
  contactEmail: string;
  hostCountry: string;
  timeZone: string | null;
  drawPending: boolean;
  settings: RegistrationSettingsView;
  data: FirebaseFirestore.DocumentData;
};

export async function loadTournament(tournamentId: string): Promise<TournamentForRegistrations> {
  const ref = db.collection("tournaments").doc(tournamentId);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw notFound("Tournament not found");
  const settings = readRegistrationSettings(snapshot.get("registration"));
  if (settings == null) throw missing("This tournament has no registrations", { code: "NO_REGISTRATIONS" });
  return {
    ref,
    name: String(snapshot.get("name") ?? ""),
    isTeams: snapshot.get("isTeams") === true,
    contactEmail: String(snapshot.get("contactEmail") ?? "").trim(),
    hostCountry: String(snapshot.get("hostCountry") ?? ""),
    timeZone: typeof snapshot.get("timeZone") === "string" ? String(snapshot.get("timeZone")) : null,
    drawPending: snapshot.get("drawPending") === true,
    settings,
    data: snapshot.data() ?? {},
  };
}

async function listStored(tournament: TournamentForRegistrations): Promise<StoredRegistration[]> {
  const snapshot = await tournament.ref.collection(REGISTRATIONS).get();
  const now = Date.now();
  return snapshot.docs.map(toStored).filter((registration) => registration.status !== "PENDING" || (registration.pendingUntil ?? 0) > now);
}

async function listTeams(tournament: TournamentForRegistrations, registrations: StoredRegistration[]): Promise<RegistrationTeamView[]> {
  if (!tournament.isTeams) return [];
  const snapshot = await tournament.ref.collection(TEAMS).get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    name: String(doc.get("name") ?? ""),
    captainEmaId: String(doc.get("captainEmaId") ?? ""),
    members: registrations.filter((registration) => registration.teamId === doc.id && registration.status !== "PENDING")
      .sort(byOrder).map((registration) => registration.emaId),
  })).sort((left, right) => left.name.localeCompare(right.name));
}

function toPublic(registration: StoredRegistration & { position: number }): PublicRegistration {
  return {
    emaId: registration.emaId,
    name: registration.name,
    country: registration.country,
    status: registration.status === "WAITING" ? "WAITING" : "CONFIRMED",
    position: registration.position,
    teamId: registration.teamId,
  };
}

/** The list that everyone sees. Pending registrations stay out until their email is confirmed. */
export async function publicRegistrations(tournamentId: string): Promise<PublicRegistrations> {
  const tournament = await loadTournament(tournamentId);
  const registrations = (await listStored(tournament)).filter((registration) => registration.status !== "PENDING");
  return {
    settings: tournament.settings,
    isTeams: tournament.isTeams,
    registrations: withPositions(registrations).map(toPublic),
    teams: await listTeams(tournament, registrations),
  };
}

async function foodOf(emaIds: string[]): Promise<Map<string, string>> {
  const result = new Map<string, string>();
  for (let start = 0; start < emaIds.length; start += 300) {
    const refs = emaIds.slice(start, start + 300).map((emaId) => db.doc(`${PERSON_PRIVATE}/${emaId}`));
    if (refs.length === 0) continue;
    (await db.getAll(...refs)).forEach((snapshot) => {
      if (snapshot.exists) result.set(snapshot.id, String(snapshot.get("foodRestrictions") ?? ""));
    });
  }
  return result;
}

async function accountsOf(emaIds: string[]): Promise<Map<string, string>> {
  const uids = new Map<string, string>();
  for (let start = 0; start < emaIds.length; start += 300) {
    const refs = emaIds.slice(start, start + 300).map((emaId) => db.doc(`emaIdIndex/${emaId}`));
    if (refs.length === 0) continue;
    (await db.getAll(...refs)).forEach((snapshot) => {
      if (snapshot.exists) uids.set(snapshot.id, String(snapshot.get("uid") ?? ""));
    });
  }
  return uids;
}

/** The list of the organizers, with emails, payments and food restrictions. */
export async function adminRegistrations(tournamentId: string): Promise<{
  settings: RegistrationSettingsView;
  isTeams: boolean;
  drawPending: boolean;
  registrations: AdminRegistration[];
  teams: RegistrationTeamView[];
}> {
  const tournament = await loadTournament(tournamentId);
  const stored = await listStored(tournament);
  const emaIds = stored.map((registration) => registration.emaId);
  const [food, accounts, docs] = await Promise.all([
    foodOf(emaIds),
    accountsOf(emaIds),
    tournament.ref.collection(REGISTRATIONS).select("createdAt").get(),
  ]);
  const createdAt = new Map(docs.docs.map((doc) => [doc.id, doc.get("createdAt")]));
  const emailCount = new Map<string, number>();
  stored.forEach((registration) => {
    if (registration.email !== "") emailCount.set(registration.email, (emailCount.get(registration.email) ?? 0) + 1);
  });
  const nonPending = stored.filter((registration) => registration.status !== "PENDING");
  return {
    settings: tournament.settings,
    isTeams: tournament.isTeams,
    drawPending: tournament.drawPending,
    registrations: withPositions(stored).map((registration) => {
      const created = createdAt.get(registration.emaId);
      return {
        ...toPublic(registration),
        status: registration.status,
        email: registration.email,
        paid: registration.paid,
        foodRestrictions: food.get(registration.emaId) ?? "",
        source: registration.source,
        registeredAt: new Date(registration.order || (created instanceof Timestamp ? created.toMillis() : 0)).toISOString(),
        hasAccount: accounts.has(registration.emaId),
        sharedEmail: registration.email !== "" && (emailCount.get(registration.email) ?? 0) > 1,
      };
    }),
    teams: await listTeams(tournament, nonPending),
  };
}

/** Writes the counters of the tournament, so the lists of tournaments show them without reading the registrations. */
export async function refreshCounters(tournament: TournamentForRegistrations): Promise<RegistrationSettingsView> {
  const stored = await listStored(tournament);
  const confirmed = stored.filter((registration) => registration.status === "CONFIRMED").length;
  const waiting = stored.filter((registration) => registration.status === "WAITING").length;
  await tournament.ref.update({ "registration.confirmed": confirmed, "registration.waiting": waiting, updatedAt: FieldValue.serverTimestamp() });
  await bumpGlobalDataVersion("tournaments");
  return { ...tournament.settings, confirmed, waiting };
}

async function logChange(tournament: TournamentForRegistrations, kind: "registered" | "left" | "removed" | "promoted", registration: { emaId: string; name: string }) {
  await tournament.ref.collection(LOG).add({ kind, emaId: registration.emaId, name: registration.name, at: FieldValue.serverTimestamp() });
}

type RegistryPerson = { emaId: string; name: string; country: string };

export async function registryPerson(emaIdValue: unknown): Promise<RegistryPerson> {
  const emaId = validateEmaId(emaIdValue);
  const snapshot = await db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${emaId}`).get();
  if (!snapshot.exists) throw missing("EMA player not found", { code: "EMA_NOT_FOUND" });
  const player = toPlayer(emaId, snapshot.data() ?? {});
  if (player.disabled) throw missing("EMA player not found", { code: "EMA_NOT_FOUND" });
  return { emaId, name: `${player.firstName} ${player.lastName}`.trim(), country: player.country };
}

async function accountOfEma(emaId: string): Promise<{ uid: string; email: string | null } | null> {
  const index = await db.doc(`emaIdIndex/${emaId}`).get();
  if (!index.exists) return null;
  const uid = String(index.get("uid") ?? "");
  try {
    const user = await auth.getUser(uid);
    return { uid, email: user.email?.toLowerCase() ?? null };
  } catch {
    return null;
  }
}

async function emaIdOfUid(uid: string): Promise<string | null> {
  const emaId = (await db.doc(`users/${uid}`).get()).get("emaId");
  return typeof emaId === "string" && emaId !== "" ? emaId : null;
}

/** Saves the food restrictions of a person. An empty text removes them. */
export async function saveFoodRestrictions(emaId: string, food: string, consent: boolean): Promise<void> {
  const ref = db.doc(`${PERSON_PRIVATE}/${emaId}`);
  if (food === "") {
    await ref.set({ foodRestrictions: FieldValue.delete(), foodConsentAt: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return;
  }
  // The restrictions can tell about health (allergies, intolerances), so they need the consent of the person.
  if (!consent) throw badRequest("The food restrictions need the consent", { code: "CONSENT_REQUIRED" });
  await ref.set({ foodRestrictions: food, foodConsentAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}

export async function foodRestrictionsOf(emaId: string): Promise<string> {
  const snapshot = await db.doc(`${PERSON_PRIVATE}/${emaId}`).get();
  return String(snapshot.get("foodRestrictions") ?? "");
}

function normalizedEmail(value: unknown): string {
  const email = String(value ?? "").trim().toLowerCase();
  if (!isValidEmail(email)) throw badRequest("Invalid email", { code: "INVALID_EMAIL" });
  return email;
}

function assertOpen(tournament: TournamentForRegistrations): void {
  if (!tournament.drawPending) throw conflict("The draw is made: the registrations are over", { code: "DRAW_MADE" });
  if (tournament.settings.status !== "OPEN") throw conflict("The registrations are closed", { code: "REGISTRATION_CLOSED" });
}

function assertBeforeDraw(tournament: TournamentForRegistrations): void {
  if (!tournament.drawPending) throw conflict("The draw is made: the registrations are over", { code: "DRAW_MADE" });
}

/**
 * Gives the registration its place or the waiting list, in a transaction, so two people never take the last place.
 * Returns the status and the position in its list.
 */
async function placeRegistration(
  tournament: TournamentForRegistrations,
  emaId: string,
  data: Record<string, unknown>,
  forced?: "CONFIRMED" | "WAITING",
): Promise<{ status: "CONFIRMED" | "WAITING"; position: number }> {
  const result = await db.runTransaction(async (transaction) => {
    const [tournamentSnapshot, all] = await Promise.all([
      transaction.get(tournament.ref),
      transaction.get(tournament.ref.collection(REGISTRATIONS)),
    ]);
    const settings = readRegistrationSettings(tournamentSnapshot.get("registration"));
    const others = all.docs.map(toStored).filter((registration) => registration.emaId !== emaId);
    const confirmed = others.filter((registration) => registration.status === "CONFIRMED").length;
    const full = settings?.maxPlayers != null && confirmed >= settings.maxPlayers;
    const status = forced ?? (full ? "WAITING" : "CONFIRMED");
    const order = Number(data.order ?? Date.now());
    transaction.set(tournament.ref.collection(REGISTRATIONS).doc(emaId), {
      ...data, status, order, pendingUntil: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    const sameList = others.filter((registration) => registration.status === status && (registration.order < order
      || (registration.order === order && registration.emaId < emaId)));
    return { status, position: sameList.length + 1 };
  });
  return result;
}

async function sendSafely(to: string, email: { subject: string; text: string; html: string }, replyTo?: string): Promise<boolean> {
  if (to === "") return false;
  try {
    await sendEmail({ to, ...email, replyTo });
    return true;
  } catch (error) {
    console.error("A registration email failed", error);
    return false;
  }
}

export type RegisterResult = {
  /** PENDING: the person must open the link of the email. */
  status: RegistrationStatus;
  position: number | null;
  /** The email that got the link, partly hidden, for example "er***@gmail.com". */
  emailHint: string;
  /** Only for a signed-in person: the app keeps it to open the registration later. */
  manageToken: string | null;
};

export function emailHint(email: string): string {
  const [user, domain] = email.split("@");
  if (!domain) return "";
  return `${user.slice(0, 2)}***@${domain}`;
}

/**
 * Registers a person.
 * - A signed-in account with an EMA number registers itself: the place is confirmed at once.
 * - Without an account, the person picks its EMA number and writes an email. It gets a link to confirm. When an
 *   account owns that EMA number, the link goes to the email of the account, so nobody registers another person
 *   with an own email.
 */
export async function register(params: {
  tournamentId: string;
  uid: string | null;
  emaId: unknown;
  email: unknown;
  foodRestrictions: unknown;
  consent: boolean;
  language: Language;
}): Promise<RegisterResult> {
  const tournament = await loadTournament(params.tournamentId);
  assertOpen(tournament);
  const signedInEma = params.uid == null ? null : await emaIdOfUid(params.uid);
  if (params.uid != null && signedInEma == null && (params.emaId == null || params.emaId === "")) {
    throw badRequest("The account has no EMA number", { code: "NO_EMA_ID" });
  }
  const person = await registryPerson(signedInEma ?? params.emaId);
  const existingDoc = await tournament.ref.collection(REGISTRATIONS).doc(person.emaId).get();
  const existing = existingDoc.exists ? toStored(existingDoc) : null;
  if (existing != null && existing.status !== "PENDING") {
    throw conflict("This EMA number is already registered", { code: "ALREADY_REGISTERED" });
  }
  const food = normalizeFoodRestrictions(params.foodRestrictions);
  const account = await accountOfEma(person.emaId);
  const ownAccount = params.uid != null && signedInEma === person.emaId;
  const email = ownAccount
    ? account?.email ?? ""
    : account?.email ?? normalizedEmail(params.email);
  if (food !== "" && !params.consent) throw badRequest("The food restrictions need the consent", { code: "CONSENT_REQUIRED" });
  const token = existing?.manageToken || newToken();
  const base = {
    emaId: person.emaId,
    name: person.name,
    country: person.country,
    email,
    paid: existing?.paid ?? false,
    teamId: null,
    language: params.language,
    source: "SELF",
    manageToken: token,
    ...(params.uid != null ? { uid: params.uid } : {}),
    ...(existing == null ? { createdAt: FieldValue.serverTimestamp() } : {}),
  };
  if (ownAccount) {
    await saveFoodRestrictions(person.emaId, food, params.consent);
    const placed = await placeRegistration(tournament, person.emaId, base);
    await refreshCounters(tournament);
    await logChange(tournament, "registered", person);
    await sendSafely(email, registeredEmail(params.language, {
      tournament: tournament.name, person: person.name, link: manageLink(tournament.ref.id, token),
      waitingPosition: placed.status === "WAITING" ? placed.position : null,
    }));
    return { status: placed.status, position: placed.position, emailHint: emailHint(email), manageToken: token };
  }
  // Without an account the registration waits for the email. The food restrictions wait with it.
  await tournament.ref.collection(REGISTRATIONS).doc(person.emaId).set({
    ...base,
    status: "PENDING",
    order: Date.now(),
    pendingUntil: Date.now() + PENDING_HOURS * 3_600_000,
    pendingFood: food,
    pendingConsent: params.consent,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  const sent = await sendSafely(email, verifyEmail(params.language, {
    tournament: tournament.name, person: person.name, link: manageLink(tournament.ref.id, token),
  }));
  if (!sent) throw new HttpError(502, "email_failed", "The email could not be sent. Try again.");
  return { status: "PENDING", position: null, emailHint: emailHint(email), manageToken: null };
}

/** The registration of the caller: by the token of the links, or by the EMA number of the signed-in account. */
export async function resolveOwnRegistration(params: {
  tournamentId: string;
  uid: string | null;
  token: string | null;
}): Promise<{ tournament: TournamentForRegistrations; registration: StoredRegistration }> {
  const tournament = await loadTournament(params.tournamentId);
  if (params.token != null && params.token !== "") {
    const found = await tournament.ref.collection(REGISTRATIONS).where("manageToken", "==", params.token).limit(1).get();
    if (!found.empty) return { tournament, registration: toStored(found.docs[0]) };
    throw missing("Registration not found", { code: "REGISTRATION_NOT_FOUND" });
  }
  if (params.uid != null) {
    const emaId = await emaIdOfUid(params.uid);
    if (emaId != null) {
      const doc = await tournament.ref.collection(REGISTRATIONS).doc(emaId).get();
      if (doc.exists) return { tournament, registration: toStored(doc) };
    }
    throw missing("Registration not found", { code: "REGISTRATION_NOT_FOUND" });
  }
  throw forbidden("Sign in or open the link of your email");
}

async function ownView(tournament: TournamentForRegistrations, registration: StoredRegistration): Promise<OwnRegistration> {
  const all = await listStored(tournament);
  const positioned = withPositions(all).find((item) => item.emaId === registration.emaId);
  const pendingFood = registration.status === "PENDING"
    ? String((await tournament.ref.collection(REGISTRATIONS).doc(registration.emaId).get()).get("pendingFood") ?? "")
    : null;
  return {
    ...toPublic({ ...registration, position: positioned?.position ?? 0 }),
    status: registration.status,
    email: registration.email,
    foodRestrictions: pendingFood ?? await foodRestrictionsOf(registration.emaId),
    manageToken: registration.manageToken,
  };
}

export async function ownRegistration(params: { tournamentId: string; uid: string | null; token: string | null }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  return ownView(tournament, registration);
}

/** The link of the email confirms a pending registration. Opening it again changes nothing. */
export async function confirmRegistration(params: { tournamentId: string; token: string }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration({ tournamentId: params.tournamentId, uid: null, token: params.token });
  if (registration.status !== "PENDING") return ownView(tournament, registration);
  if ((registration.pendingUntil ?? 0) < Date.now()) throw missing("The link is too old. Register again.", { code: "LINK_EXPIRED" });
  assertOpen(tournament);
  const doc = await tournament.ref.collection(REGISTRATIONS).doc(registration.emaId).get();
  const food = String(doc.get("pendingFood") ?? "");
  if (food !== "") await saveFoodRestrictions(registration.emaId, food, doc.get("pendingConsent") === true);
  const placed = await placeRegistration(tournament, registration.emaId, {
    order: Date.now(), pendingFood: FieldValue.delete(), pendingConsent: FieldValue.delete(), confirmedAt: FieldValue.serverTimestamp(),
  });
  await refreshCounters(tournament);
  await logChange(tournament, "registered", registration);
  await sendSafely(registration.email, registeredEmail(registration.language, {
    tournament: tournament.name, person: registration.name, link: manageLink(tournament.ref.id, registration.manageToken),
    waitingPosition: placed.status === "WAITING" ? placed.position : null,
  }));
  return ownView(tournament, { ...registration, status: placed.status });
}

export async function updateOwnRegistration(params: {
  tournamentId: string; uid: string | null; token: string | null; foodRestrictions: unknown; consent: boolean;
}): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  const food = normalizeFoodRestrictions(params.foodRestrictions);
  if (registration.status === "PENDING") throw conflict("Confirm the registration first", { code: "PENDING" });
  await saveFoodRestrictions(registration.emaId, food, params.consent);
  return ownView(tournament, registration);
}

/** Removes the person from its team. A captain hands the team to the next member, and an empty team goes away. */
async function leaveTeamOf(tournament: TournamentForRegistrations, registration: StoredRegistration): Promise<void> {
  if (registration.teamId == null) return;
  const teamRef = tournament.ref.collection(TEAMS).doc(registration.teamId);
  await db.runTransaction(async (transaction) => {
    const [team, members] = await Promise.all([
      transaction.get(teamRef),
      transaction.get(tournament.ref.collection(REGISTRATIONS).where("teamId", "==", registration.teamId)),
    ]);
    const rest = members.docs.map(toStored).filter((member) => member.emaId !== registration.emaId).sort(byOrder);
    transaction.update(tournament.ref.collection(REGISTRATIONS).doc(registration.emaId), { teamId: null, updatedAt: FieldValue.serverTimestamp() });
    if (!team.exists) return;
    if (rest.length === 0) {
      transaction.delete(teamRef);
    } else if (team.get("captainEmaId") === registration.emaId) {
      transaction.update(teamRef, { captainEmaId: rest[0].emaId, updatedAt: FieldValue.serverTimestamp() });
    }
  });
}

/** The person leaves the tournament. The organization gets an email with the new counts. */
export async function cancelOwnRegistration(params: { tournamentId: string; uid: string | null; token: string | null }): Promise<void> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  assertBeforeDraw(tournament);
  await leaveTeamOf(tournament, registration);
  await tournament.ref.collection(REGISTRATIONS).doc(registration.emaId).delete();
  if (registration.status === "PENDING") return;
  const settings = await refreshCounters(tournament);
  await logChange(tournament, "left", registration);
  await Promise.all([
    sendSafely(registration.email, leftEmail(registration.language, { tournament: tournament.name })),
    sendSafely(tournament.contactEmail, organizationLeftEmail(organizationLanguage(tournament.hostCountry), {
      tournament: tournament.name,
      person: `${registration.name} (${registration.emaId})`,
      confirmed: settings.confirmed,
      waiting: settings.waiting,
    })),
  ]);
}

async function assertTeamNameFree(tournament: TournamentForRegistrations, name: string, exceptId?: string): Promise<void> {
  const teams = await tournament.ref.collection(TEAMS).get();
  if (teams.docs.some((team) => team.id !== exceptId && String(team.get("name") ?? "").toLowerCase() === name.toLowerCase())) {
    throw conflict("Another team has this name", { code: "TEAM_NAME_TAKEN" });
  }
}

function assertTeams(tournament: TournamentForRegistrations): void {
  if (!tournament.isTeams) throw badRequest("This tournament has no teams", { code: "NO_TEAMS" });
}

/** A registered person makes a team and becomes its captain. */
export async function createOwnTeam(params: { tournamentId: string; uid: string | null; token: string | null; name: unknown }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  assertTeams(tournament);
  assertBeforeDraw(tournament);
  if (registration.status === "PENDING") throw conflict("Confirm the registration first", { code: "PENDING" });
  if (registration.teamId != null) throw conflict("You are already in a team", { code: "ALREADY_IN_TEAM" });
  const name = parseTeamName(params.name);
  await assertTeamNameFree(tournament, name);
  const teamRef = tournament.ref.collection(TEAMS).doc();
  const batch = db.batch();
  batch.set(teamRef, { name, captainEmaId: registration.emaId, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  batch.update(tournament.ref.collection(REGISTRATIONS).doc(registration.emaId), { teamId: teamRef.id, updatedAt: FieldValue.serverTimestamp() });
  await batch.commit();
  return ownView(tournament, { ...registration, teamId: teamRef.id });
}

export async function renameOwnTeam(params: { tournamentId: string; uid: string | null; token: string | null; name: unknown }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  const team = await captainTeam(tournament, registration);
  const name = parseTeamName(params.name);
  await assertTeamNameFree(tournament, name, team.id);
  await team.ref.update({ name, updatedAt: FieldValue.serverTimestamp() });
  return ownView(tournament, registration);
}

async function captainTeam(tournament: TournamentForRegistrations, registration: StoredRegistration): Promise<FirebaseFirestore.DocumentSnapshot> {
  assertTeams(tournament);
  assertBeforeDraw(tournament);
  if (registration.teamId == null) throw conflict("You are not in a team", { code: "NO_TEAM" });
  const team = await tournament.ref.collection(TEAMS).doc(registration.teamId).get();
  if (!team.exists || team.get("captainEmaId") !== registration.emaId) throw forbidden("Only the captain changes the team");
  return team;
}

/** Puts a registered person without a team into a team. Returns false when the team is full. */
async function joinTeam(tournament: TournamentForRegistrations, teamId: string, emaId: string): Promise<StoredRegistration> {
  return db.runTransaction(async (transaction) => {
    const [member, members] = await Promise.all([
      transaction.get(tournament.ref.collection(REGISTRATIONS).doc(emaId)),
      transaction.get(tournament.ref.collection(REGISTRATIONS).where("teamId", "==", teamId)),
    ]);
    if (!member.exists) throw missing("Registration not found", { code: "REGISTRATION_NOT_FOUND" });
    const stored = toStored(member);
    if (stored.status === "PENDING") throw conflict("That person has not confirmed the registration", { code: "PENDING" });
    if (stored.teamId === teamId) return stored;
    if (stored.teamId != null) throw conflict("That person is already in a team", { code: "ALREADY_IN_TEAM" });
    if (members.size >= TEAM_SIZE) throw conflict(`A team has ${TEAM_SIZE} players`, { code: "TEAM_FULL" });
    transaction.update(member.ref, { teamId, updatedAt: FieldValue.serverTimestamp() });
    return { ...stored, teamId };
  });
}

/** The captain adds a registered person without a team. That person gets an email and can leave the team. */
export async function addOwnTeamMember(params: { tournamentId: string; uid: string | null; token: string | null; emaId: unknown }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  const team = await captainTeam(tournament, registration);
  const emaId = validateEmaId(params.emaId);
  const member = await joinTeam(tournament, team.id, emaId);
  await sendSafely(member.email, teamAddedEmail(member.language, {
    tournament: tournament.name,
    team: String(team.get("name") ?? ""),
    captain: registration.name,
    link: manageLink(tournament.ref.id, member.manageToken),
  }));
  return ownView(tournament, registration);
}

export async function removeOwnTeamMember(params: { tournamentId: string; uid: string | null; token: string | null; emaId: unknown }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  const team = await captainTeam(tournament, registration);
  const emaId = validateEmaId(params.emaId);
  if (emaId === registration.emaId) throw badRequest("Leave the team instead", { code: "USE_LEAVE" });
  const doc = await tournament.ref.collection(REGISTRATIONS).doc(emaId).get();
  if (!doc.exists || doc.get("teamId") !== team.id) throw missing("That person is not in your team", { code: "NOT_IN_TEAM" });
  await leaveTeamOf(tournament, toStored(doc));
  return ownView(tournament, registration);
}

export async function leaveOwnTeam(params: { tournamentId: string; uid: string | null; token: string | null }): Promise<OwnRegistration> {
  const { tournament, registration } = await resolveOwnRegistration(params);
  assertTeams(tournament);
  assertBeforeDraw(tournament);
  await leaveTeamOf(tournament, registration);
  return ownView(tournament, { ...registration, teamId: null });
}

/** Someone without an EMA number asks the organization to register it. The organization gets an email. */
export async function requestWithoutEmaNumber(params: {
  tournamentId: string; name: unknown; country: unknown; email: unknown; message: unknown;
}): Promise<void> {
  const tournament = await loadTournament(params.tournamentId);
  assertOpen(tournament);
  const name = String(params.name ?? "").trim().replace(/\s+/g, " ").slice(0, 100);
  if (name.length < 2) throw badRequest("Write your name", { code: "INVALID_NAME" });
  const email = normalizedEmail(params.email);
  const country = String(params.country ?? "").trim().toUpperCase().slice(0, 2);
  const message = String(params.message ?? "").trim().slice(0, 2000);
  if (tournament.contactEmail === "") throw conflict("The tournament has no contact email", { code: "NO_CONTACT" });
  const sent = await sendSafely(tournament.contactEmail, noEmaNumberEmail(organizationLanguage(tournament.hostCountry), {
    tournament: tournament.name, person: `${name}${country ? ` (${country})` : ""} <${email}>`, message,
  }), email);
  if (!sent) throw new HttpError(502, "email_failed", "The email could not be sent. Try again.");
}

// ---------------------------------------------------------------------------------------------------------------
// Organizers.

/** An organizer adds a person. The place is confirmed at once, or the waiting list when the places are full. */
export async function adminAddRegistration(params: {
  tournamentId: string; emaId: unknown; email: unknown; status?: unknown; paid?: unknown; foodRestrictions?: unknown;
  consent?: boolean; teamName?: unknown; notify?: boolean; language: Language;
}): Promise<AdminRegistration> {
  const tournament = await loadTournament(params.tournamentId);
  assertBeforeDraw(tournament);
  const person = await registryPerson(params.emaId);
  const existing = await tournament.ref.collection(REGISTRATIONS).doc(person.emaId).get();
  if (existing.exists && toStored(existing).status !== "PENDING") throw conflict("This EMA number is already registered", { code: "ALREADY_REGISTERED" });
  const account = await accountOfEma(person.emaId);
  const typed = String(params.email ?? "").trim();
  const email = typed === "" ? account?.email ?? "" : normalizedEmail(typed);
  const food = normalizeFoodRestrictions(params.foodRestrictions);
  // The organizer writes what the person said, with the consent that the person gave to the organization.
  if (food !== "") await saveFoodRestrictions(person.emaId, food, true);
  const token = existing.exists ? toStored(existing).manageToken || newToken() : newToken();
  const forced = params.status === "WAITING" ? "WAITING" : params.status === "CONFIRMED" ? "CONFIRMED" : undefined;
  const placed = await placeRegistration(tournament, person.emaId, {
    emaId: person.emaId, name: person.name, country: person.country, email, paid: params.paid === true, teamId: null,
    language: params.language, source: "ADMIN", manageToken: token, createdAt: FieldValue.serverTimestamp(),
  }, forced);
  if (params.teamName != null && String(params.teamName).trim() !== "") {
    await adminSetTeam(tournament, person.emaId, params.teamName);
  }
  await refreshCounters(tournament);
  await logChange(tournament, "registered", person);
  if (params.notify !== false && email !== "") {
    await sendSafely(email, registeredEmail(params.language, {
      tournament: tournament.name, person: person.name, link: manageLink(tournament.ref.id, token),
      waitingPosition: placed.status === "WAITING" ? placed.position : null,
    }));
  }
  return (await adminRegistrations(params.tournamentId)).registrations.find((item) => item.emaId === person.emaId) as AdminRegistration;
}

/** Puts a person in the team with this name, made when it does not exist. An empty name takes the person out of its team. */
async function adminSetTeam(tournament: TournamentForRegistrations, emaId: string, teamName: unknown): Promise<void> {
  assertTeams(tournament);
  const doc = await tournament.ref.collection(REGISTRATIONS).doc(emaId).get();
  if (!doc.exists) throw missing("Registration not found", { code: "REGISTRATION_NOT_FOUND" });
  const registration = toStored(doc);
  const text = String(teamName ?? "").trim();
  if (text === "") {
    await leaveTeamOf(tournament, registration);
    return;
  }
  const name = parseTeamName(text);
  const teams = await tournament.ref.collection(TEAMS).get();
  const found = teams.docs.find((team) => String(team.get("name") ?? "").toLowerCase() === name.toLowerCase());
  if (found?.id === registration.teamId) return;
  await leaveTeamOf(tournament, registration);
  if (found != null) {
    await joinTeam(tournament, found.id, emaId);
    return;
  }
  const teamRef = tournament.ref.collection(TEAMS).doc();
  const batch = db.batch();
  batch.set(teamRef, { name, captainEmaId: emaId, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  batch.update(doc.ref, { teamId: teamRef.id, updatedAt: FieldValue.serverTimestamp() });
  await batch.commit();
}

/** An organizer changes a registration: email, payment, team, food restrictions, or moves it between the lists. */
export async function adminUpdateRegistration(params: {
  tournamentId: string; emaId: unknown; email?: unknown; status?: unknown; paid?: unknown; teamName?: unknown; foodRestrictions?: unknown;
}): Promise<AdminRegistration> {
  const tournament = await loadTournament(params.tournamentId);
  assertBeforeDraw(tournament);
  const emaId = validateEmaId(params.emaId);
  const ref = tournament.ref.collection(REGISTRATIONS).doc(emaId);
  const doc = await ref.get();
  if (!doc.exists) throw missing("Registration not found", { code: "REGISTRATION_NOT_FOUND" });
  const before = toStored(doc);
  const update: Record<string, unknown> = { updatedAt: FieldValue.serverTimestamp() };
  if (params.email !== undefined) update.email = String(params.email ?? "").trim() === "" ? "" : normalizedEmail(params.email);
  if (params.paid !== undefined) update.paid = params.paid === true;
  let promoted = false;
  if (params.status !== undefined) {
    if (params.status !== "CONFIRMED" && params.status !== "WAITING") throw badRequest("status must be CONFIRMED or WAITING");
    // An organizer confirms a pending registration too, for example when the person never got the email.
    update.status = params.status;
    update.pendingUntil = FieldValue.delete();
    promoted = params.status === "CONFIRMED" && before.status === "WAITING";
  }
  await ref.update(update);
  if (params.foodRestrictions !== undefined) await saveFoodRestrictions(emaId, normalizeFoodRestrictions(params.foodRestrictions), true);
  if (params.teamName !== undefined) await adminSetTeam(tournament, emaId, params.teamName);
  if (params.status !== undefined) await refreshCounters(tournament);
  if (promoted) {
    await logChange(tournament, "promoted", before);
    const email = typeof update.email === "string" ? update.email : before.email;
    await sendSafely(email, promotedEmail(before.language, {
      tournament: tournament.name, person: before.name, link: manageLink(tournament.ref.id, before.manageToken),
    }));
  }
  return (await adminRegistrations(params.tournamentId)).registrations.find((item) => item.emaId === emaId) as AdminRegistration;
}

/** An organizer removes a registration. The person gets an email, unless [notify] is false. */
export async function adminRemoveRegistration(params: { tournamentId: string; emaId: unknown; notify?: boolean }): Promise<void> {
  const tournament = await loadTournament(params.tournamentId);
  assertBeforeDraw(tournament);
  const emaId = validateEmaId(params.emaId);
  const doc = await tournament.ref.collection(REGISTRATIONS).doc(emaId).get();
  if (!doc.exists) throw missing("Registration not found", { code: "REGISTRATION_NOT_FOUND" });
  const registration = toStored(doc);
  await leaveTeamOf(tournament, registration);
  await doc.ref.delete();
  await refreshCounters(tournament);
  if (registration.status === "PENDING") return;
  await logChange(tournament, "removed", registration);
  if (params.notify !== false) {
    await sendSafely(registration.email, removedEmail(registration.language, {
      tournament: tournament.name, contact: tournament.contactEmail || "—",
    }));
  }
}

/** Changes the state, places, close date or link name of the registrations. */
export async function updateRegistrationSettings(params: {
  tournamentId: string;
  status: "OPEN" | "CLOSED";
  maxPlayers: number | null;
  closesOn: string | null;
  slug: string;
}): Promise<RegistrationSettingsView> {
  const tournament = await loadTournament(params.tournamentId);
  assertBeforeDraw(tournament);
  await claimSlug(params.slug, tournament.ref.id, tournament.settings.slug);
  await tournament.ref.update({
    "registration.status": params.status,
    "registration.maxPlayers": params.maxPlayers,
    "registration.closesOn": params.closesOn,
    "registration.slug": params.slug,
    updatedAt: FieldValue.serverTimestamp(),
  });
  return refreshCounters(await loadTournament(params.tournamentId));
}

/** Keeps the link name for one tournament. Another tournament cannot take it. */
export async function claimSlug(slug: string, tournamentId: string, previous: string | null): Promise<void> {
  await db.runTransaction(async (transaction) => {
    const ref = db.collection(SLUGS).doc(slug);
    const current = await transaction.get(ref);
    if (current.exists && current.get("tournamentId") !== tournamentId) {
      const owner = await transaction.get(db.collection("tournaments").doc(String(current.get("tournamentId"))));
      if (owner.exists) throw conflict("Another tournament uses this link", { code: "SLUG_TAKEN" });
    }
    transaction.set(ref, { tournamentId, updatedAt: FieldValue.serverTimestamp() });
    if (previous != null && previous !== "" && previous !== slug) transaction.delete(db.collection(SLUGS).doc(previous));
  });
}

export async function tournamentOfSlug(slug: string): Promise<string> {
  const doc = await db.collection(SLUGS).doc(slug.toLowerCase()).get();
  const tournamentId = doc.exists ? String(doc.get("tournamentId") ?? "") : "";
  if (tournamentId === "") throw notFound("Registration link not found");
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists || tournament.get("isPublic") === false) throw notFound("Registration link not found");
  return tournamentId;
}

/** Removes the pending registrations whose link is too old. */
export async function removeExpiredPending(tournament: TournamentForRegistrations, nowMs: number): Promise<number> {
  const pending = await tournament.ref.collection(REGISTRATIONS).where("status", "==", "PENDING").get();
  const expired = pending.docs.filter((doc) => Number(doc.get("pendingUntil") ?? 0) < nowMs);
  if (expired.length === 0) return 0;
  const batch = db.batch();
  expired.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
  return expired.length;
}

/** The changes of the last days, for the weekly report. */
export async function recentChanges(tournament: TournamentForRegistrations, sinceMs: number): Promise<{ registered: string[]; left: string[] }> {
  const log = await tournament.ref.collection(LOG).where("at", ">=", Timestamp.fromMillis(sinceMs)).get();
  const describe = (doc: FirebaseFirestore.QueryDocumentSnapshot) => `${String(doc.get("name") ?? "")} (${doc.get("emaId")})`;
  return {
    registered: log.docs.filter((doc) => doc.get("kind") === "registered").map(describe),
    left: log.docs.filter((doc) => doc.get("kind") === "left" || doc.get("kind") === "removed").map(describe),
  };
}

export type ImportRow = FormRow & {
  /** The name of the EMA registry, which the app uses instead of the name of the file. */
  registryName: string | null;
  registryCountry: string | null;
  /** Another row has the same email. */
  sharedEmail: boolean;
};

/**
 * Reads the answers of the old Google Form. Without [apply] it only checks them. With [apply] it registers every
 * row without problems, in the order of the times of the form: places first, then the waiting list when the places
 * are full. It sends no email: the people registered long ago.
 */
export async function importRegistrations(params: {
  tournamentId: string; csv: unknown; apply: boolean; language: Language;
}): Promise<{ rows: ImportRow[]; imported: number }> {
  const tournament = await loadTournament(params.tournamentId);
  assertBeforeDraw(tournament);
  if (typeof params.csv !== "string" || params.csv.trim() === "") throw badRequest("Send the CSV file", { code: "EMPTY_FILE" });
  const formRows = readFormRows(params.csv);
  const validIds = formRows.filter((row) => /^\d{8}$/.test(row.emaId)).map((row) => row.emaId);
  const registry = new Map<string, FirebaseFirestore.DocumentSnapshot>();
  const existing = new Set((await tournament.ref.collection(REGISTRATIONS).select("status").get()).docs
    .filter((doc) => doc.get("status") !== "PENDING").map((doc) => doc.id));
  for (let start = 0; start < validIds.length; start += 300) {
    const refs = validIds.slice(start, start + 300).map((emaId) => db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${emaId}`));
    if (refs.length > 0) (await db.getAll(...refs)).forEach((snapshot) => registry.set(snapshot.id, snapshot));
  }
  const emailCount = new Map<string, number>();
  formRows.forEach((row) => {
    if (row.email !== "") emailCount.set(row.email, (emailCount.get(row.email) ?? 0) + 1);
  });
  const rows: ImportRow[] = formRows.map((row) => {
    const snapshot = registry.get(row.emaId);
    const player = snapshot?.exists ? toPlayer(row.emaId, snapshot.data() ?? {}) : null;
    const problems = [...row.problems];
    if (problems.length === 0 && (player == null || player.disabled)) problems.push("EMA_NOT_FOUND");
    if (problems.length === 0 && existing.has(row.emaId)) problems.push("ALREADY_REGISTERED");
    return {
      ...row,
      problems,
      registryName: player == null ? null : `${player.firstName} ${player.lastName}`.trim(),
      registryCountry: player?.country ?? null,
      sharedEmail: row.email !== "" && (emailCount.get(row.email) ?? 0) > 1,
    };
  });
  if (!params.apply) return { rows, imported: 0 };
  let imported = 0;
  const ordered = rows.filter((row) => row.problems.length === 0)
    .sort((left, right) => (left.registeredAt ?? Number.MAX_SAFE_INTEGER) - (right.registeredAt ?? Number.MAX_SAFE_INTEGER) || left.line - right.line);
  for (const row of ordered) {
    await placeRegistration(tournament, row.emaId, {
      emaId: row.emaId,
      name: row.registryName ?? `${row.firstName} ${row.lastName}`.trim(),
      country: row.registryCountry ?? "",
      email: row.email,
      paid: false,
      teamId: null,
      language: params.language,
      source: "IMPORT",
      manageToken: newToken(),
      order: row.registeredAt ?? Date.now() + row.line,
      createdAt: FieldValue.serverTimestamp(),
    });
    if (row.foodRestrictions !== "") await saveFoodRestrictions(row.emaId, row.foodRestrictions, true);
    if (tournament.isTeams && row.team != null) {
      try {
        await adminSetTeam(tournament, row.emaId, row.team);
      } catch (error) {
        // A full team or a strange name leaves the person without a team. The organizer sees it in the list.
        console.warn("Import: the team of a row was not set", row.line, error);
      }
    }
    imported++;
  }
  await refreshCounters(tournament);
  return { rows, imported };
}

export { adminRegistrationsLink, listStored, toStored, REGISTRATIONS, TEAMS };
