import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { sendEmail } from "./mailService";
import { organizationLanguage, teamReminderEmail, weeklyReportEmail } from "./registrationEmails";
import {
  accountOfEma,
  adminRegistrationsLink,
  listStored,
  loadTournament,
  manageLink,
  recentChanges,
  refreshCounters,
  removeExpiredPending,
  type TournamentForRegistrations,
} from "./registrationsService";
import { hourlyDecision, localTime, TEAM_SIZE } from "./registrationSettings";

/** What the hourly job did with one tournament, for the logs and the tests. */
export type HourlyResult = { tournamentId: string; expired: number; closed: boolean; teamReminders: number; weeklyReport: boolean };

async function send(to: string, email: { subject: string; text: string; html: string }): Promise<boolean> {
  if (to === "") return false;
  try {
    await sendEmail({ to, ...email });
    return true;
  } catch (error) {
    console.error("A registration email of the hourly job failed", error);
    return false;
  }
}

/** The people without a full team: no team, or a team with fewer than [TEAM_SIZE] people. */
async function withoutFullTeam(tournament: TournamentForRegistrations) {
  const registrations = (await listStored(tournament)).filter((registration) => registration.status !== "PENDING");
  const size = new Map<string, number>();
  registrations.forEach((registration) => {
    if (registration.teamId != null) size.set(registration.teamId, (size.get(registration.teamId) ?? 0) + 1);
  });
  return registrations.filter((registration) => registration.teamId == null || size.get(registration.teamId)! < TEAM_SIZE);
}

async function runOne(tournamentId: string, now: Date): Promise<HourlyResult> {
  const tournament = await loadTournament(tournamentId);
  const result: HourlyResult = { tournamentId, expired: 0, closed: false, teamReminders: 0, weeklyReport: false };
  result.expired = await removeExpiredPending(tournament, now.getTime());
  // loadTournament refuses a tournament without registrations, so the map is there.
  const raw = tournament.data.registration as Record<string, unknown>;
  const local = localTime(now, tournament.timeZone);
  const decision = hourlyDecision({
    now: local,
    status: tournament.settings.status,
    closesOn: tournament.settings.closesOn,
    isTeams: tournament.isTeams,
    teamReminderSentFor: typeof raw.teamReminderSentFor === "string" ? raw.teamReminderSentFor : null,
    lastWeeklyReportOn: typeof raw.lastWeeklyReportOn === "string" ? raw.lastWeeklyReportOn : null,
    hasContact: tournament.contactEmail !== "",
  });
  if (decision.close) {
    await tournament.ref.update({ "registration.status": "CLOSED", updatedAt: FieldValue.serverTimestamp() });
    result.closed = true;
  }
  if (decision.teamReminder) {
    // The flag goes first, so a slow mailbox never sends the reminder twice.
    await tournament.ref.update({ "registration.teamReminderSentFor": tournament.settings.closesOn });
    for (const registration of await withoutFullTeam(tournament)) {
      const sent = await send(registration.email, teamReminderEmail(registration.language, {
        tournament: tournament.name,
        // The reminder is due only before a closing day.
        closesOn: tournament.settings.closesOn!,
        link: manageLink(tournament.ref.id, registration.manageToken),
      }));
      if (sent) result.teamReminders++;
    }
  }
  if (decision.weeklyReport) {
    await tournament.ref.update({ "registration.lastWeeklyReportOn": local.date });
    const settings = await refreshCounters(tournament);
    const changes = await recentChanges(tournament, now.getTime() - 7 * 86_400_000);
    result.weeklyReport = await send(tournament.contactEmail, weeklyReportEmail(organizationLanguage(tournament.hostCountry), {
      tournament: tournament.name,
      registered: changes.registered,
      left: changes.left,
      confirmed: settings.confirmed,
      waiting: settings.waiting,
      withoutTeam: tournament.isTeams ? (await withoutFullTeam(tournament)).length : null,
      link: adminRegistrationsLink(tournament.ref.id),
    }));
  }
  return result;
}

/**
 * Runs every hour: removes the old pending registrations, closes the registrations after their last day, sends the
 * team reminders one week before the close and the weekly report on Sunday evening. Only tournaments without a draw.
 * One failed tournament does not stop the others.
 */
export async function runRegistrationsHour(now: Date = new Date()): Promise<HourlyResult[]> {
  const pending = await db.collection("tournaments").where("drawPending", "==", true).get();
  const results: HourlyResult[] = [];
  for (const doc of pending.docs) {
    try {
      results.push(await runOne(doc.id, now));
    } catch (error) {
      console.error("The hourly job of the registrations failed for a tournament", doc.id, error);
    }
  }
  // Once a day, at night, the private data of the registrations of old tournaments goes away.
  if (now.getUTCHours() === PURGE_HOUR_UTC) {
    try {
      const purge = await purgeFinishedRegistrations(now);
      if (purge.tournaments.length > 0) console.log("Registrations purged", JSON.stringify(purge));
    } catch (error) {
      console.error("Purging the registrations of finished tournaments failed", error);
    }
  }
  return results;
}

/** Days after the last day of a tournament that the emails and food restrictions of its registrations are kept. */
export const REGISTRATION_DATA_DAYS = 30;
const PURGE_HOUR_UTC = 3;

export type PurgeResult = { tournaments: string[]; registrations: number; foodRestrictions: number };

function isoDateDaysBefore(now: Date, days: number): string {
  return new Date(now.getTime() - days * 86_400_000).toISOString().slice(0, 10);
}

/**
 * The privacy policy promises that the data of the registrations is not kept forever. [REGISTRATION_DATA_DAYS] days
 * after the last day of a tournament:
 * - each registration loses its email and the token of its links (the name, country and EMA number are public anyway);
 * - the log of the registrations, with names and times, is deleted;
 * - the food restrictions (`personPrivate/{emaId}`) of a person without an account are deleted, unless the person is
 *   registered in a tournament that has not ended that long ago. An account keeps them: they are in its profile, and it
 *   deletes them there.
 * Each tournament is done once (`registrationsPurgedAt`).
 */
export async function purgeFinishedRegistrations(now: Date = new Date()): Promise<PurgeResult> {
  const cutoff = isoDateDaysBefore(now, REGISTRATION_DATA_DAYS);
  const result: PurgeResult = { tournaments: [], registrations: 0, foodRestrictions: 0 };
  const finished = await db.collection("tournaments").where("eventEndDate", "<", cutoff).get();
  const people = new Set<string>();
  for (const tournament of finished.docs) {
    if (tournament.get("registrationsPurgedAt") != null) continue;
    const registrations = await tournament.ref.collection("registrations").get();
    const log = await tournament.ref.collection("registrationLog").get();
    const writes = [
      ...registrations.docs.map((doc) => (batch: FirebaseFirestore.WriteBatch) => {
        people.add(doc.id);
        batch.update(doc.ref, { email: "", manageToken: FieldValue.delete(), sharedEmail: false });
      }),
      ...log.docs.map((doc) => (batch: FirebaseFirestore.WriteBatch) => batch.delete(doc.ref)),
    ];
    for (let start = 0; start < writes.length; start += 400) {
      const batch = db.batch();
      writes.slice(start, start + 400).forEach((write) => write(batch));
      await batch.commit();
    }
    await tournament.ref.update({ registrationsPurgedAt: FieldValue.serverTimestamp() });
    result.tournaments.push(tournament.id);
    result.registrations += registrations.size;
  }
  if (people.size === 0) return result;
  // The tournaments that still use the data: they have not ended, or ended less than the days ago.
  const current = await db.collection("tournaments").where("eventEndDate", ">=", cutoff).get();
  for (const emaId of people) {
    const privateRef = db.doc(`personPrivate/${emaId}`);
    if (!(await privateRef.get()).exists) continue;
    if ((await accountOfEma(emaId)) != null) continue;
    const elsewhere = current.empty ? [] : await db.getAll(...current.docs.map((doc) => doc.ref.collection("registrations").doc(emaId)));
    if (elsewhere.some((snapshot) => snapshot.exists)) continue;
    await privateRef.delete();
    result.foodRestrictions++;
  }
  return result;
}
