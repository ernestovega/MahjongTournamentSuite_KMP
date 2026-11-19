import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  addDays,
  defaultSlug,
  hourlyDecision,
  localTime,
  normalizeFoodRestrictions,
  parseClosesOn,
  parseMaxPlayers,
  parseSlug,
  parseTeamName,
  readRegistrationSettings,
  slugify,
  teamFromForm,
} from "./registrationSettings";

describe("registration settings", () => {
  it("makes link names from the short name and the year", () => {
    assert.equal(slugify("MMC 2027!"), "mmc-2027");
    assert.equal(slugify("  Ñandú  Open  "), "nandu-open");
    assert.equal(defaultSlug("6th MMC", "2027-02-20"), "6th-mmc-2027");
    assert.equal(defaultSlug("MMC", null), "mmc");
  });

  it("refuses link names with other characters or too short", () => {
    assert.equal(parseSlug("mmc-2027"), "mmc-2027");
    assert.throws(() => parseSlug("MMC 2027"));
    assert.throws(() => parseSlug("ab"));
    assert.throws(() => parseSlug("mmc_2027"));
  });

  it("reads the places, the close date and the team names", () => {
    assert.equal(parseMaxPlayers(""), null);
    assert.equal(parseMaxPlayers(64), 64);
    assert.throws(() => parseMaxPlayers(3));
    assert.throws(() => parseMaxPlayers(10.5));
    assert.equal(parseClosesOn(null), null);
    assert.equal(parseClosesOn("2027-01-31"), "2027-01-31");
    assert.throws(() => parseClosesOn("31/01/2027"));
    assert.equal(parseTeamName("  Los   Dragones "), "Los Dragones");
    assert.throws(() => parseTeamName("   "));
    assert.throws(() => parseTeamName("x".repeat(41)));
  });

  it("treats the answers that say no restriction as empty", () => {
    for (const none of ["no", "NO", "None", "Ninguna", "non", "-", "", "nada.", "нет"]) assert.equal(normalizeFoodRestrictions(none), "", none);
    assert.equal(normalizeFoodRestrictions(" Soy  celíaca "), "Soy celíaca");
    assert.equal(normalizeFoodRestrictions("No shellfish"), "No shellfish");
    assert.throws(() => normalizeFoodRestrictions("x".repeat(301)));
  });

  it("treats the placeholders of the old form as no team", () => {
    for (const none of ["?", "-", "TBD", "(Pending)", "", "  "]) assert.equal(teamFromForm(none), null, none);
    assert.equal(teamFromForm("Mahjong Valencia "), "Mahjong Valencia");
    assert.equal(teamFromForm("#masti"), "#masti");
  });

  it("reads stored settings and ignores broken values", () => {
    assert.equal(readRegistrationSettings(null), null);
    assert.deepEqual(readRegistrationSettings({ status: "OPEN", maxPlayers: 64, closesOn: "2027-01-31", slug: "mmc-2027", confirmed: 3, waiting: 1 }), {
      status: "OPEN", maxPlayers: 64, closesOn: "2027-01-31", slug: "mmc-2027", confirmed: 3, waiting: 1,
    });
    assert.deepEqual(readRegistrationSettings({ status: "X", maxPlayers: -1, closesOn: "bad" }), {
      status: "CLOSED", maxPlayers: null, closesOn: null, slug: "", confirmed: 0, waiting: 0,
    });
  });

  it("gives the local date, hour and weekday of a time zone", () => {
    // 2027-01-31 is a Sunday. 20:30 UTC is 21:30 in Madrid (winter time).
    assert.deepEqual(localTime(new Date("2027-01-31T20:30:00Z"), "Europe/Madrid"), { date: "2027-01-31", hour: 21, weekday: 0 });
    assert.deepEqual(localTime(new Date("2027-01-31T23:30:00Z"), "Europe/Madrid"), { date: "2027-02-01", hour: 0, weekday: 1 });
    assert.equal(localTime(new Date("2027-01-31T20:30:00Z"), "Not/AZone").hour, 21);
    assert.equal(addDays("2027-03-01", -7), "2027-02-22");
  });
});

describe("hourly decision", () => {
  const base = {
    status: "OPEN" as const, closesOn: "2027-01-31", isTeams: true, teamReminderSentFor: null, lastWeeklyReportOn: null, hasContact: true,
  };

  it("closes the registrations the day after the close date", () => {
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-31", hour: 23, weekday: 0 } }).close, false);
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-02-01", hour: 0, weekday: 1 } }).close, true);
    assert.equal(hourlyDecision({ ...base, status: "CLOSED", now: { date: "2027-02-01", hour: 0, weekday: 1 } }).close, false);
    assert.equal(hourlyDecision({ ...base, closesOn: null, now: { date: "2030-01-01", hour: 0, weekday: 2 } }).close, false);
  });

  it("sends the team reminder once, one week before the close, from 10:00", () => {
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-23", hour: 12, weekday: 6 } }).teamReminder, false);
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-24", hour: 9, weekday: 0 } }).teamReminder, false);
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-24", hour: 10, weekday: 0 } }).teamReminder, true);
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-26", hour: 10, weekday: 2 } }).teamReminder, true);
    assert.equal(hourlyDecision({ ...base, teamReminderSentFor: "2027-01-31", now: { date: "2027-01-26", hour: 10, weekday: 2 } }).teamReminder, false);
    assert.equal(hourlyDecision({ ...base, isTeams: false, now: { date: "2027-01-24", hour: 10, weekday: 0 } }).teamReminder, false);
    // A new close date gets its own reminder.
    assert.equal(hourlyDecision({ ...base, closesOn: "2027-02-07", teamReminderSentFor: "2027-01-31", now: { date: "2027-01-31", hour: 11, weekday: 0 } }).teamReminder, true);
  });

  it("sends the weekly report on Sunday from 21:00, once, also after the close", () => {
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-17", hour: 20, weekday: 0 } }).weeklyReport, false);
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-17", hour: 21, weekday: 0 } }).weeklyReport, true);
    assert.equal(hourlyDecision({ ...base, lastWeeklyReportOn: "2027-01-17", now: { date: "2027-01-17", hour: 22, weekday: 0 } }).weeklyReport, false);
    assert.equal(hourlyDecision({ ...base, now: { date: "2027-01-18", hour: 21, weekday: 1 } }).weeklyReport, false);
    assert.equal(hourlyDecision({ ...base, status: "CLOSED", now: { date: "2027-02-07", hour: 21, weekday: 0 } }).weeklyReport, true);
    assert.equal(hourlyDecision({ ...base, hasContact: false, now: { date: "2027-01-17", hour: 21, weekday: 0 } }).weeklyReport, false);
  });
});
