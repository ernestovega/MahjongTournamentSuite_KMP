import assert from "node:assert/strict";
import test from "node:test";

import { EMPTY_PLACE, parseOrganizationInput, parsePlace, readOrganizerIds, readPlace } from "./organizationsService";

const link = "https://maps.app.goo.gl/abc123";

test("a club without a place has an empty place", () => {
  assert.deepEqual(parsePlace(undefined), EMPTY_PLACE);
  assert.deepEqual(parsePlace(null), EMPTY_PLACE);
  assert.deepEqual(parsePlace({}), EMPTY_PLACE);
  assert.deepEqual(parsePlace({ mapUrl: " ", name: "", address: "  ", city: "" }), EMPTY_PLACE);
});

test("a place keeps its link, name, address and city, without spaces around", () => {
  assert.deepEqual(
    parsePlace({ mapUrl: ` ${link} `, name: " Club room ", address: " Calle Mayor 1 ", city: " Madrid " }),
    { mapUrl: link, name: "Club room", address: "Calle Mayor 1", city: "Madrid" },
  );
});

test("a place needs a Google Maps link or an address", () => {
  assert.deepEqual(parsePlace({ mapUrl: link }), { ...EMPTY_PLACE, mapUrl: link });
  assert.deepEqual(parsePlace({ address: "Calle Mayor 1" }), { ...EMPTY_PLACE, address: "Calle Mayor 1" });
  assert.throws(() => parsePlace({ name: "Club room" }), /link or an address/);
  assert.throws(() => parsePlace({ city: "Madrid" }), /link or an address/);
});

test("the link of a place goes to Google Maps over https", () => {
  assert.throws(() => parsePlace({ mapUrl: "https://example.com/place" }), /Google Maps/);
  assert.throws(() => parsePlace({ mapUrl: "http://maps.app.goo.gl/abc" }), /Google Maps/);
  assert.throws(() => parsePlace({ mapUrl: "not a link" }), /Google Maps/);
});

test("the place rejects long texts and wrong shapes", () => {
  assert.throws(() => parsePlace({ address: "x".repeat(251) }), /place.address/);
  assert.throws(() => parsePlace({ address: "a", name: "x".repeat(121) }), /place.name/);
  assert.throws(() => parsePlace({ address: "a", city: "x".repeat(121) }), /place.city/);
  assert.throws(() => parsePlace("Calle Mayor 1"), /object/);
  assert.throws(() => parsePlace([link]), /object/);
});

test("a stored place reads back, and a missing or broken one is empty", () => {
  assert.deepEqual(readPlace({ mapUrl: link, name: "Room", address: "Street", city: "Madrid" }), { mapUrl: link, name: "Room", address: "Street", city: "Madrid" });
  assert.deepEqual(readPlace(undefined), EMPTY_PLACE);
  assert.deepEqual(readPlace("Street"), EMPTY_PLACE);
  assert.deepEqual(readPlace({ address: 5, city: "Madrid" }), { ...EMPTY_PLACE, city: "Madrid" });
});

test("the organization form carries the place", () => {
  const body = { name: "Club", type: "CLUB", country: "ES", primaryColor: "#123456" };
  assert.deepEqual(parseOrganizationInput(body).place, EMPTY_PLACE);
  assert.deepEqual(parseOrganizationInput({ ...body, place: { address: "Calle Mayor 1" } }).place, { ...EMPTY_PLACE, address: "Calle Mayor 1" });
  assert.throws(() => parseOrganizationInput({ ...body, place: { name: "Room" } }));
});

test("reads organizer ids without blanks or repeats, and refuses an organization without a type or a country", () => {
  assert.deepEqual(readOrganizerIds([null, " org-a ", "org-a", "", 7]), ["org-a", "7"]);
  assert.deepEqual(readOrganizerIds("org-a"), []);
  assert.throws(() => parseOrganizationInput({ name: "Club", country: "ES" }), /type must be CLUB or FEDERATION/);
  assert.throws(() => parseOrganizationInput({ name: "Club", type: "CLUB" }), /country must be the two-letter code/);
  assert.equal(parseOrganizationInput({ name: "Club", type: "CLUB", country: "es" }).type, "CLUB");
});
