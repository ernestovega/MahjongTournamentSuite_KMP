import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { LANGUAGES } from "./i18n";
import {
  leftEmail,
  noEmaNumberEmail,
  organizationLanguage,
  organizationLeftEmail,
  organizationPromotedEmail,
  promotedEmail,
  registeredEmail,
  removedEmail,
  teamAddedEmail,
  teamReminderEmail,
  verifyEmail,
  weeklyReportEmail,
} from "./registrationEmails";

describe("registration emails", () => {
  const link = "https://mts.example/registration?t=t1&k=abc";
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
        organizationLeftEmail(language, { tournament: "Open", person: "Ana (1)", promoted: ["Bea (2)"], confirmed: 3, waiting: 0 }),
        organizationPromotedEmail(language, { tournament: "Open", promoted: ["Bea (2)", "Carla (3)"], confirmed: 4, waiting: 1 }),
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

  it("tells the organization who took the free places from the waiting list", () => {
    const left = organizationLeftEmail("en", { tournament: "Open", person: "Ana (1)", promoted: ["Bea (2)"], confirmed: 3, waiting: 0 });
    assert.match(left.text, /Ana \(1\) cancelled/);
    assert.match(left.text, /waiting list, in its order[^\n]*\n• Bea \(2\)/);
    assert.doesNotMatch(organizationLeftEmail("en", { tournament: "Open", person: "Ana (1)", confirmed: 3, waiting: 0 }).text, /waiting list, in its order/);
    const promoted = organizationPromotedEmail("es", { tournament: "Open", promoted: ["Bea (2)", "Carla (3)"], confirmed: 4, waiting: 1 });
    assert.equal(promoted.subject, "Plaza desde la lista de espera en Open: Bea (2), Carla (3)");
    assert.match(promoted.text, /• Bea \(2\)\n• Carla \(3\)/);
  });

  it("writes to the organization in the language of the host country", () => {
    assert.equal(organizationLanguage("ES"), "es");
    assert.equal(organizationLanguage("se"), "sv");
    assert.equal(organizationLanguage("JP"), "en");
  });

  it("tells a person who shares an email that the registration is in the shared mailbox", () => {
    const shared = registeredEmail("en", { tournament: "Open", person: "Ana", link: "https://x.test/m", waitingPosition: null, sharedEmail: true });
    const alone = registeredEmail("en", { tournament: "Open", person: "Ana", link: "https://x.test/m", waitingPosition: null });
    assert.ok(shared.text.length > alone.text.length);
    const sharedVerify = verifyEmail("en", { tournament: "Open", person: "Ana", link: "https://x.test/v", sharedEmail: true });
    assert.ok(sharedVerify.text.length > verifyEmail("en", { tournament: "Open", person: "Ana", link: "https://x.test/v" }).text.length);
  });
});
