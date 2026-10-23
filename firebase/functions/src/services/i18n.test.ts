import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  buildInvitationEmail,
  buildRegistrationEmail,
  DEFAULT_LANGUAGE,
  LANGUAGES,
  emailTexts,
  parseAcceptLanguage,
} from "./i18n";

describe("parseAcceptLanguage", () => {
  it("gives English when the header is missing or empty", () => {
    assert.equal(parseAcceptLanguage(undefined), "en");
    assert.equal(parseAcceptLanguage(null), "en");
    assert.equal(parseAcceptLanguage(""), "en");
  });

  it("picks Spanish for a Spanish tag with or without a region", () => {
    assert.equal(parseAcceptLanguage("es"), "es");
    assert.equal(parseAcceptLanguage("es-ES"), "es");
    assert.equal(parseAcceptLanguage("ES-mx"), "es");
  });

  it("follows the quality values", () => {
    assert.equal(parseAcceptLanguage("en;q=0.5, es;q=0.9"), "es");
    assert.equal(parseAcceptLanguage("es;q=0.2, en;q=0.8"), "en");
  });

  it("keeps the order of the header when the quality is equal", () => {
    assert.equal(parseAcceptLanguage("es, en"), "es");
    assert.equal(parseAcceptLanguage("en, es"), "en");
  });

  it("skips languages that the emails do not have", () => {
    assert.equal(parseAcceptLanguage("fr-FR, es;q=0.5"), "es");
    assert.equal(parseAcceptLanguage("fr, de"), "en");
  });

  it("ignores a language with quality zero and a broken quality", () => {
    assert.equal(parseAcceptLanguage("es;q=0, en"), "en");
    assert.equal(parseAcceptLanguage("es;q=abc, en;q=0.4"), "en");
  });

  it("reads a header that arrives as a list", () => {
    assert.equal(parseAcceptLanguage(["fr", "es-ES"]), "es");
  });
});

describe("email texts", () => {
  it("has every text in every language", () => {
    const english = Object.keys(emailTexts("en")).sort();
    for (const language of LANGUAGES) {
      assert.deepEqual(Object.keys(emailTexts(language)).sort(), english);
    }
    assert.equal(DEFAULT_LANGUAGE, "en");
  });

  it("builds the English invitation as before", () => {
    const email = buildInvitationEmail("en", { tournamentName: "Madrid Open", link: "https://app/invite?token=t", days: 14 });
    assert.equal(email.subject, "Invitation to Madrid Open");
    assert.match(email.text, /^You are invited to play in Madrid Open\./);
    assert.match(email.text, /Open this link to accept: https:\/\/app\/invite\?token=t/);
    assert.match(email.text, /expires in 14 days\.$/);
    assert.match(email.html, /<p>You are invited to play in <strong>Madrid Open<\/strong>\.<\/p>/);
    assert.match(email.html, /<a href="https:\/\/app\/invite\?token=t">Accept the invitation<\/a>/);
  });

  it("builds the Spanish invitation", () => {
    const email = buildInvitationEmail("es", { tournamentName: "Madrid Open", link: "https://app/invite?token=t", days: 14 });
    assert.equal(email.subject, "Invitación a Madrid Open");
    assert.match(email.text, /Te invitamos a jugar en Madrid Open\./);
    assert.match(email.text, /Abre este enlace para aceptar/);
    assert.match(email.text, /caduca en 14 días/);
    assert.match(email.html, /<strong>Madrid Open<\/strong>/);
    assert.match(email.html, />Aceptar la invitación<\/a>/);
  });

  it("builds the registration emails in both languages", () => {
    const params = { tournamentName: "Madrid Open", link: "https://app/?tournament=x" };
    const english = buildRegistrationEmail("en", params);
    assert.equal(english.subject, "You are registered in Madrid Open");
    assert.match(english.text, /You have been added to the tournament "Madrid Open"\./);
    assert.match(english.html, /tournament <strong>Madrid Open<\/strong>\./);
    const spanish = buildRegistrationEmail("es", params);
    assert.equal(spanish.subject, "Estás inscrito en Madrid Open");
    assert.match(spanish.text, /Te han añadido al torneo «Madrid Open»\./);
    assert.match(spanish.html, /<strong>Madrid Open<\/strong>/);
    assert.match(spanish.html, />Abrir el torneo<\/a>/);
  });

  it("escapes the name of the tournament in the HTML", () => {
    const email = buildInvitationEmail("es", { tournamentName: "A <b> & C", link: "https://app", days: 14 });
    assert.ok(email.html.includes("<strong>A &lt;b&gt; &amp; C</strong>"));
    assert.ok(!email.html.includes("<b>"));
  });
});
