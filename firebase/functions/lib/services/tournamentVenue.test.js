"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const tournamentVenue_1 = require("./tournamentVenue");
(0, node_test_1.test)("an empty venue becomes empty texts", () => {
    strict_1.default.deepEqual((0, tournamentVenue_1.normalizeVenue)({}), { venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" });
    strict_1.default.deepEqual((0, tournamentVenue_1.normalizeVenue)(null), { venueName: "", venueAddress: "", venueMapUrl: "", contactEmail: "" });
});
(0, node_test_1.test)("trims the venue name and address", () => {
    const venue = (0, tournamentVenue_1.normalizeVenue)({ venueName: " Eurobridge Club ", venueAddress: " Calle Mayor 1, Madrid " });
    strict_1.default.equal(venue.venueName, "Eurobridge Club");
    strict_1.default.equal(venue.venueAddress, "Calle Mayor 1, Madrid");
});
(0, node_test_1.test)("accepts Google Maps links", () => {
    strict_1.default.equal((0, tournamentVenue_1.normalizeMapUrl)("https://maps.app.goo.gl/abc123"), "https://maps.app.goo.gl/abc123");
    strict_1.default.equal((0, tournamentVenue_1.normalizeMapUrl)("https://www.google.com/maps/place/Madrid"), "https://www.google.com/maps/place/Madrid");
    strict_1.default.equal((0, tournamentVenue_1.normalizeMapUrl)("https://maps.google.com/?q=40.4,-3.7"), "https://maps.google.com/?q=40.4,-3.7");
});
(0, node_test_1.test)("rejects links that are not Google Maps over https", () => {
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeMapUrl)("http://maps.app.goo.gl/abc"), /Google Maps/);
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeMapUrl)("https://example.com/maps"), /Google Maps/);
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeMapUrl)("https://evilgoogle.com/maps"), /Google Maps/);
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeMapUrl)("https://www.google.com/search?q=x"), /Google Maps/);
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeMapUrl)("not a link"), /valid link/);
});
(0, node_test_1.test)("rejects texts that are too long", () => {
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeVenue)({ venueName: "x".repeat(121) }), /at most 120/);
});
(0, node_test_1.test)("accepts an empty or valid contact email", () => {
    strict_1.default.equal((0, tournamentVenue_1.normalizeVenue)({ contactEmail: " info@club.es " }).contactEmail, "info@club.es");
    strict_1.default.equal((0, tournamentVenue_1.normalizeVenue)({ contactEmail: "" }).contactEmail, "");
});
(0, node_test_1.test)("rejects a contact email that is not valid", () => {
    strict_1.default.throws(() => (0, tournamentVenue_1.normalizeVenue)({ contactEmail: "not an email" }), /valid email/);
});
//# sourceMappingURL=tournamentVenue.test.js.map