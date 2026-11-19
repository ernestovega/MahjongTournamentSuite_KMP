import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { LANGUAGES } from "./i18n";
import {
  leftEmail,
  noEmaNumberEmail,
  organizationLanguage,
  organizationLeftEmail,
  promotedEmail,
  registeredEmail,
  removedEmail,
  teamAddedEmail,
  teamReminderEmail,
  verifyEmail,
  weeklyReportEmail,
} from "./registrationEmails";

describe("registration emails", () => {
  const link = "https://mts.example/inscripcion/gestionar?t=t1&k=abc";
  it("has every email in the eight languages, with the tournament and the link", () => {
    for (const language of LANGUAGES) {
      const emails = [
        verifyEmail(language, { tournament: "Open <X>", person: "Ana", link }),
        registeredEmail(language, { tournament: "Open <X>", person: "Ana", link, waitingPosition: null }),
        registeredEmail(language, { tournament: "Open <X>", person: "Ana", link, waitingPosition: 3 }),
        promotedEmail(language, { tournament: "Open <X>", person: "Ana", link }),
        teamAddedEmail(language, { tournament: "Open <X>", team: "Dragons", captain: "Bea", link }),
        teamReminderEmail(language, { tournament: "Open <X>", closesOn: "2027-01-31", link }),
        weeklyReportEmail(language, { tournament: "Open <X>", registered: ["Ana (1)"], left: [], confirmed: 10, waiting: 2, withoutTeam: 3, link }),
      ];
      for (const email of emails) {
        assert.ok(email.subject.includes("Open <X>"), `${language}: ${email.subject}`);
        assert.ok(email.text.includes(link), `${language}: ${email.subject}`);
        assert.ok(email.html.includes("Open &lt;X&gt;"), `${language}: html escapes`);
        assert.ok(!email.html.includes("<X>"), `${language}: html escapes`);
      }
      for (const email of [
        removedEmail(language, { tournament: "Open", contact: "org@example.com" }),
        leftEmail(language, { tournament: "Open" }),
        organizationLeftEmail(language, { tournament: "Open", person: "Ana (1)", confirmed: 3, waiting: 0 }),
        noEmaNumberEmail(language, { tournament: "Open", person: "Ana", message: "Hello" }),
      ]) {
        assert.ok(email.subject.length > 0 && email.text.length > 0, language);
      }
    }
  });

  it("tells the waiting position and keeps the subject on one line", () => {
    assert.match(registeredEmail("en", { tournament: "Open", person: "Ana", link, waitingPosition: 3 }).text, /number 3/);
    assert.match(registeredEmail("es", { tournament: "Open", person: "Ana", link, waitingPosition: 3 }).text, /número 3/);
    assert.equal(verifyEmail("en", { tournament: "A\r\nBcc: x@y.z", person: "Ana", link }).subject.includes("\n"), false);
  });

  it("writes to the organization in the language of the host country", () => {
    assert.equal(organizationLanguage("ES"), "es");
    assert.equal(organizationLanguage("se"), "sv");
    assert.equal(organizationLanguage("JP"), "en");
  });
});
