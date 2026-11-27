import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { lookupPlace, placeTextOf, type PlacesSearch } from "./placesLookup";

const link = "https://www.google.com/maps/place/Eskalerria,+Calle+Cipriano+Sancho+36,+Madrid/@40.4,-3.6,17z/data=!3d40.41!4d-3.61";

const found = {
  places: [
    {
      displayName: { text: "Eskalerria" },
      formattedAddress: "Calle Cipriano Sancho, 36, 28002 Madrid, Spain",
      addressComponents: [
        { longText: "Madrid", shortText: "Madrid", types: ["locality", "political"] },
        { longText: "Spain", shortText: "ES", types: ["country", "political"] },
      ],
      timeZone: { id: "Europe/Madrid" },
    },
  ],
};

describe("placeTextOf", () => {
  it("reads the text of a place link", () => {
    assert.equal(placeTextOf(link), "Eskalerria, Calle Cipriano Sancho 36, Madrid");
  });

  it("reads the q parameter of a search link", () => {
    assert.equal(placeTextOf("https://www.google.com/maps?q=Hotel+Foo,+Bar"), "Hotel Foo, Bar");
  });

  it("returns null for coordinates and for links without text", () => {
    assert.equal(placeTextOf("https://www.google.com/maps/place/40.4,-3.6"), null);
    assert.equal(placeTextOf("https://www.google.com/maps/@40.4,-3.6,17z"), null);
  });

  it("returns null for a text that is not a link, an empty search and a broken escape", () => {
    assert.equal(placeTextOf("not a link"), null);
    assert.equal(placeTextOf("https://www.google.com/maps?q=+"), null);
    assert.equal(placeTextOf("https://www.google.com/maps/place/%E0%A4%A"), null);
  });
});

describe("lookupPlace", () => {
  it("maps the answer of Places and removes the country from the address", async () => {
    const bodies: Record<string, unknown>[] = [];
    const search: PlacesSearch = async (body) => {
      bodies.push(body);
      return found;
    };
    const place = await lookupPlace(link, "key", search);
    assert.deepEqual(place, {
      name: "Eskalerria",
      address: "Calle Cipriano Sancho, 36, 28002 Madrid",
      city: "Madrid",
      countryCode: "ES",
      timeZone: "Europe/Madrid",
    });
    assert.equal(bodies[0].textQuery, "Eskalerria, Calle Cipriano Sancho 36, Madrid");
    assert.deepEqual((bodies[0].locationBias as { circle: { center: unknown } }).circle.center, {
      latitude: 40.41,
      longitude: -3.61,
    });
  });

  it("returns null without a key, without text, without a result, or on an error", async () => {
    const ok: PlacesSearch = async () => found;
    assert.equal(await lookupPlace(link, "", ok), null);
    assert.equal(await lookupPlace("https://www.google.com/maps/@40.4,-3.6,17z", "key", ok), null);
    assert.equal(await lookupPlace(link, "key", async () => ({ places: [] })), null);
    assert.equal(
      await lookupPlace(link, "key", async () => {
        throw new Error("down");
      }),
      null,
    );
  });

  it("reads a place that misses its fields, a link without coordinates and an address without the country", async () => {
    const bodies: Record<string, unknown>[] = [];
    const bare = await lookupPlace("https://www.google.com/maps?q=Club", "key", async (body) => {
      bodies.push(body);
      return { places: [{}] };
    });
    assert.deepEqual(bare, { name: null, address: null, city: null, countryCode: null, timeZone: null });
    assert.equal(bodies[0].locationBias, undefined);

    const odd = await lookupPlace(link, "key", async () => ({
      places: [{
        displayName: { text: " " }, formattedAddress: "Calle 1, Lisboa",
        addressComponents: [{ longText: "Spain", shortText: "Spain", types: ["country"] }, { types: ["postal_town"] }],
        timeZone: { id: "not a zone" },
      }],
    }));
    assert.deepEqual(odd, { name: null, address: "Calle 1, Lisboa", city: null, countryCode: null, timeZone: null });
  });

  it("asks Google Places and treats a refused request as no place", async () => {
    const calls: Array<{ url: string; init: RequestInit }> = [];
    const answers = [new Response(JSON.stringify(found), { status: 200 }), new Response("no", { status: 403 })];
    const fetching = mock.method(globalThis, "fetch", async (url: string, init: RequestInit) => {
      calls.push({ url, init });
      return answers.shift() as Response;
    });
    try {
      assert.equal((await lookupPlace(link, "secret-key"))?.name, "Eskalerria");
      assert.equal(await lookupPlace(link, "secret-key"), null);
    } finally {
      fetching.mock.restore();
    }
    assert.equal(calls[0].url, "https://places.googleapis.com/v1/places:searchText");
    assert.equal((calls[0].init.headers as Record<string, string>)["x-goog-api-key"], "secret-key");
  });
});
