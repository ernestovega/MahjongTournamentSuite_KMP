import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";

import { LANGUAGES } from "./i18n";
import { setMailTransportForTests, type OutgoingEmail } from "./mailService";
import { leaveEmail, placeEmail, sendPlayerCountEmails, stayEmail } from "./playerCountEmails";

const change = { tournament: "Open <Madrid>", from: 20, to: 16, link: "https://mts.example/?tournament=t1" };

describe("the emails of a change of the number of players", () => {
  afterEach(() => setMailTransportForTests(null));

  it("has every text in the eight languages, with the numbers and the link", () => {
    for (const language of LANGUAGES) {
      const stay = stayEmail(language, { ...change, oldNumber: 9, number: 5 });
      const same = stayEmail(language, { ...change, oldNumber: 2, number: 2 });
      const leave = leaveEmail(language, { tournament: change.tournament, from: 20, to: 16, contact: "org@example.com" });
      const place = placeEmail(language, { ...change, from: 16, to: 20, number: 17 });
      for (const email of [stay, same, leave, place]) {
        assert.ok(email.subject.includes("Open <Madrid>"), `${language}: ${email.subject}`);
        assert.ok(email.text.length > 40, language);
        // The name of the tournament is escaped in the HTML.
        assert.ok(!email.html.includes("<Madrid>"), language);
      }
      assert.ok(/\b9\b/.test(stay.text) && /\b5\b/.test(stay.text), `${language}: ${stay.text}`);
      assert.ok(/\b2\b/.test(same.text), language);
      assert.notEqual(stay.text, same.text, language);
      assert.ok(stay.text.includes(change.link) && stay.html.includes("href="), language);
      assert.ok(leave.text.includes("org@example.com") && !leave.text.includes(change.link), language);
      assert.ok(/\b17\b/.test(place.text) && place.text.includes(change.link), language);
    }
    assert.ok(!leaveEmail("en", { tournament: "T", from: 20, to: 16, contact: "" }).text.includes("write to"));
  });

  it("sends each email once, and counts the people without an address and the failed sends", async () => {
    const sent: OutgoingEmail[] = [];
    setMailTransportForTests(async (email) => {
      if (email.to === "bad@example.com") throw new Error("SMTP down");
      sent.push(email);
    });
    const email = stayEmail("en", { ...change, oldNumber: 1, number: 1 });
    const result = await sendPlayerCountEmails([
      { emaId: "3", to: "a@example.com", email },
      { emaId: "2", to: null, email },
      { emaId: "1", to: "bad@example.com", email },
      { emaId: "4", to: "b@example.com", email },
      { emaId: "0", to: "", email },
    ], "org@example.com");
    assert.deepEqual(result, { sent: 2, noEmail: ["0", "2"], failed: ["1"] });
    assert.deepEqual(sent.map((each) => each.to).sort(), ["a@example.com", "b@example.com"]);
    assert.ok(sent.every((each) => each.replyTo === "org@example.com"));
    assert.deepEqual(await sendPlayerCountEmails([]), { sent: 0, noEmail: [], failed: [] });
  });
});
