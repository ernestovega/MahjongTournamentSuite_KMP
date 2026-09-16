"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listCountries = listCountries;
const firebase_1 = require("../firebase");
/** Reads the global countries collection. Each document needs code and name fields. */
async function listCountries() {
    const snap = await firebase_1.db.collection("countries").get();
    return snap.docs
        .map((document) => ({
        code: String(document.get("code") ?? document.id).trim().toUpperCase(),
        name: String(document.get("name") ?? "").trim(),
    }))
        .filter((country) => /^[A-Z]{2}$/.test(country.code) && country.name.length > 0)
        .sort((a, b) => a.name.localeCompare(b.name));
}
//# sourceMappingURL=countriesService.js.map