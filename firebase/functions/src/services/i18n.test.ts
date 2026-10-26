import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
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

  it("picks French and Italian", () => {
    assert.equal(parseAcceptLanguage("fr-CH"), "fr");
    assert.equal(parseAcceptLanguage("it-IT, en;q=0.5"), "it");
    assert.equal(parseAcceptLanguage("de, fr;q=0.4"), "fr");
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
    assert.equal(parseAcceptLanguage("ja-JP, es;q=0.5"), "es");
    assert.equal(parseAcceptLanguage("ja, de"), "en");
  });

  it("ignores a language with quality zero and a broken quality", () => {
    assert.equal(parseAcceptLanguage("es;q=0, en"), "en");
    assert.equal(parseAcceptLanguage("es;q=abc, en;q=0.4"), "en");
  });

  it("reads a header that arrives as a list", () => {
    assert.equal(parseAcceptLanguage(["ja", "es-ES"]), "es");
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

  it("builds the French and Italian registration emails", () => {
    const registration = { tournamentName: "Madrid Open", link: "https://app/?tournament=x" };
    assert.match(buildRegistrationEmail("fr", registration).html, /<strong>Madrid Open<\/strong>/);
    assert.match(buildRegistrationEmail("it", registration).html, /<strong>Madrid Open<\/strong>/);
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
    const email = buildRegistrationEmail("es", { tournamentName: "A <b> & C", link: "https://app" });
    assert.ok(email.html.includes("<strong>A &lt;b&gt; &amp; C</strong>"));
    assert.ok(!email.html.includes("<b>"));
  });
});
