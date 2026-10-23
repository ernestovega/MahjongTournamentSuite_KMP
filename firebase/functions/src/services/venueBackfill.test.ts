import assert from "node:assert/strict";
import test from "node:test";

import { planVenueBackfill } from "./venueBackfill";

const complete = { venueName: "Club", venueAddress: "Calle 1", venueMapUrl: "", contactEmail: "a@b.es" };
const empty = { venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" };

test("a complete tournament needs nothing", () => {
  assert.equal(planVenueBackfill({ hostCity: "Madrid", hostCountry: "ES", venue: complete, creatorEmail: "x@y.es" }), null);
});

test("a map link alone is a venue", () => {
  const venue = { ...empty, venueMapUrl: "https://maps.app.goo.gl/x", contactEmail: "a@b.es" };
  assert.equal(planVenueBackfill({ hostCity: "Madrid", hostCountry: "ES", venue, creatorEmail: "" }), null);
});

test("an empty venue gets a map search link and the creator email", () => {
  const plan = planVenueBackfill({ hostCity: "Santiago de Compostela", hostCountry: "ES", venue: empty, creatorEmail: "x@y.es" });
  assert.deepEqual(plan, {
    update: {
      venueMapUrl: "https://www.google.com/maps/search/?api=1&query=Santiago%20de%20Compostela%2C%20ES",
      contactEmail: "x@y.es",
    },
    unresolved: [],
  });
});

test("a venue name without an address is not a venue", () => {
  const plan = planVenueBackfill({ hostCity: "Madrid", hostCountry: "ES", venue: { ...complete, venueAddress: "" }, creatorEmail: "" });
  assert.ok(plan?.update.venueMapUrl);
  assert.deepEqual(plan?.unresolved, []);
});

test("an unknown creator email stays unresolved", () => {
  const plan = planVenueBackfill({ hostCity: "Madrid", hostCountry: "ES", venue: { ...complete, contactEmail: "" }, creatorEmail: "" });
  assert.deepEqual(plan, { update: {}, unresolved: ["contactEmail"] });
});

test("a tournament without a city has no venue to guess", () => {
  const plan = planVenueBackfill({ hostCity: " ", hostCountry: "ES", venue: empty, creatorEmail: "x@y.es" });
  assert.deepEqual(plan, { update: { contactEmail: "x@y.es" }, unresolved: ["venue"] });
});
