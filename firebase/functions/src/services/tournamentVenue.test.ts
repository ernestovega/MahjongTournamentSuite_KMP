import assert from "node:assert/strict";
import { test } from "node:test";

import { normalizeMapUrl, normalizeVenue, venueRequiredProblems } from "./tournamentVenue";

test("an empty venue becomes empty texts", () => {
  assert.deepEqual(normalizeVenue({}), { venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" });
  assert.deepEqual(normalizeVenue(null), { venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" });
});

test("trims the venue name and address", () => {
  const venue = normalizeVenue({ venueName: " Eurobridge Club ", venueAddress: " Calle Mayor 1, Madrid " });
  assert.equal(venue.venueName, "Eurobridge Club");
  assert.equal(venue.venueAddress, "Calle Mayor 1, Madrid");
});

test("accepts Google Maps links", () => {
  assert.equal(normalizeMapUrl("https://maps.app.goo.gl/abc123"), "https://maps.app.goo.gl/abc123");
  assert.equal(normalizeMapUrl("https://www.google.com/maps/place/Madrid"), "https://www.google.com/maps/place/Madrid");
  assert.equal(normalizeMapUrl("https://maps.google.com/?q=40.4,-3.7"), "https://maps.google.com/?q=40.4,-3.7");
});

test("rejects links that are not Google Maps over https", () => {
  assert.throws(() => normalizeMapUrl("http://maps.app.goo.gl/abc"), /Google Maps/);
  assert.throws(() => normalizeMapUrl("https://example.com/maps"), /Google Maps/);
  assert.throws(() => normalizeMapUrl("https://evilgoogle.com/maps"), /Google Maps/);
  assert.throws(() => normalizeMapUrl("https://www.google.com/search?q=x"), /Google Maps/);
  assert.throws(() => normalizeMapUrl("not a link"), /valid link/);
});

test("rejects texts that are too long", () => {
  assert.throws(() => normalizeVenue({ venueName: "x".repeat(121) }), /at most 120/);
});

test("accepts an empty or valid contact email", () => {
  assert.equal(normalizeVenue({ contactEmail: " info@club.es " }).contactEmail, "info@club.es");
  assert.equal(normalizeVenue({ contactEmail: "" }).contactEmail, "");
});

test("rejects a contact email that is not valid", () => {
  assert.throws(() => normalizeVenue({ contactEmail: "not an email" }), /valid email/);
});

test("a venue needs a link or a name with an address, and a contact email", () => {
  const complete = { venueName: "Club", venueAddress: "Calle 1", venueMapUrl: "", contactEmail: "a@b.es" };
  assert.deepEqual(venueRequiredProblems(complete), []);
  assert.deepEqual(venueRequiredProblems({ ...complete, venueName: "", venueMapUrl: "https://maps.app.goo.gl/x" }), []);
  assert.equal(venueRequiredProblems({ ...complete, venueAddress: "" }).length, 1);
  assert.deepEqual(venueRequiredProblems({ ...complete, contactEmail: "" }), ["Required"]);
  assert.equal(venueRequiredProblems({ venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" }).length, 2);
});
