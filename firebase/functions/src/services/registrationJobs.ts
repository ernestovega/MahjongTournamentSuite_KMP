import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { sendEmail } from "./mailService";
import { organizationLanguage, teamReminderEmail, weeklyReportEmail } from "./registrationEmails";
import {
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
  return registrations.filter((registration) => registration.teamId == null || (size.get(registration.teamId) ?? 0) < TEAM_SIZE);
}

async function runOne(tournamentId: string, now: Date): Promise<HourlyResult> {
  const tournament = await loadTournament(tournamentId);
  const result: HourlyResult = { tournamentId, expired: 0, closed: false, teamReminders: 0, weeklyReport: false };
  result.expired = await removeExpiredPending(tournament, now.getTime());
  const raw = (tournament.data.registration ?? {}) as Record<string, unknown>;
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
        closesOn: tournament.settings.closesOn ?? "",
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
  return results;
}
