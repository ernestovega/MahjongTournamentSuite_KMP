import { db } from "../firebase";

export type Country = {
  code: string;
  name: string;
};

/** Reads the global countries collection. Each document needs code and name fields. */
export async function listCountries(): Promise<Country[]> {
  const snap = await db.collection("countries").get();
  return snap.docs
    .map((document) => ({
      code: String(document.get("code") ?? document.id).trim().toUpperCase(),
      name: String(document.get("name") ?? "").trim(),
    }))
    .filter((country) => /^[A-Z]{2}$/.test(country.code) && country.name.length > 0)
    .sort((a, b) => a.name.localeCompare(b.name));
}
