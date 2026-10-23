"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const venueBackfill_1 = require("./venueBackfill");
const complete = { venueName: "Club", venueAddress: "Calle 1", venueMapUrl: "", contactEmail: "a@b.es" };
const empty = { venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" };
(0, node_test_1.default)("a complete tournament needs nothing", () => {
    strict_1.default.equal((0, venueBackfill_1.planVenueBackfill)({ hostCity: "Madrid", hostCountry: "ES", venue: complete, creatorEmail: "x@y.es" }), null);
});
(0, node_test_1.default)("a map link alone is a venue", () => {
    const venue = { ...empty, venueMapUrl: "https://maps.app.goo.gl/x", contactEmail: "a@b.es" };
    strict_1.default.equal((0, venueBackfill_1.planVenueBackfill)({ hostCity: "Madrid", hostCountry: "ES", venue, creatorEmail: "" }), null);
});
(0, node_test_1.default)("an empty venue gets a map search link and the creator email", () => {
    const plan = (0, venueBackfill_1.planVenueBackfill)({ hostCity: "Santiago de Compostela", hostCountry: "ES", venue: empty, creatorEmail: "x@y.es" });
    strict_1.default.deepEqual(plan, {
        update: {
            venueMapUrl: "https://www.google.com/maps/search/?api=1&query=Santiago%20de%20Compostela%2C%20ES",
            contactEmail: "x@y.es",
        },
        unresolved: [],
    });
});
(0, node_test_1.default)("a venue name without an address is not a venue", () => {
    const plan = (0, venueBackfill_1.planVenueBackfill)({ hostCity: "Madrid", hostCountry: "ES", venue: { ...complete, venueAddress: "" }, creatorEmail: "" });
    strict_1.default.ok(plan?.update.venueMapUrl);
    strict_1.default.deepEqual(plan?.unresolved, []);
});
(0, node_test_1.default)("an unknown creator email stays unresolved", () => {
    const plan = (0, venueBackfill_1.planVenueBackfill)({ hostCity: "Madrid", hostCountry: "ES", venue: { ...complete, contactEmail: "" }, creatorEmail: "" });
    strict_1.default.deepEqual(plan, { update: {}, unresolved: ["contactEmail"] });
});
(0, node_test_1.default)("a tournament without a city has no venue to guess", () => {
    const plan = (0, venueBackfill_1.planVenueBackfill)({ hostCity: " ", hostCountry: "ES", venue: empty, creatorEmail: "x@y.es" });
    strict_1.default.deepEqual(plan, { update: { contactEmail: "x@y.es" }, unresolved: ["venue"] });
});
//# sourceMappingURL=venueBackfill.test.js.map