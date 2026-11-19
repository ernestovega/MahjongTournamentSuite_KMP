import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { createHash, randomUUID } from "node:crypto";

import { db, storage } from "../firebase";
import { badRequest, notFound } from "../api/httpError";
import { WORLD_COUNTRY_CODES } from "./countryCodes";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import { normalizeMapUrl } from "./tournamentVenue";

/**
 * A club or a federation. Organizations own the tournaments: each tournament has one or more organizers
 * (`tournaments/{id}.organizerIds`, the first one is the main organizer). The logo and the color of a tournament are
 * the ones of its main organizer. The club admins of an organization manage its tournaments.
 */
export type OrganizationType = "CLUB" | "FEDERATION";

/**
 * The premises of a club, optional: some clubs have their own place. It is not the venue of its tournaments. The form
 * reads it from a Google Maps link, like the venue of a tournament. Every field is an empty text when it is unknown.
 */
export type OrganizationPlace = {
  mapUrl: string;
  name: string;
  address: string;
  city: string;
};

export const EMPTY_PLACE: OrganizationPlace = { mapUrl: "", name: "", address: "", city: "" };
const MAX_PLACE_NAME_LENGTH = 120;
const MAX_PLACE_ADDRESS_LENGTH = 250;
const MAX_PLACE_CITY_LENGTH = 120;

export type Organization = {
  id: string;
  name: string;
  type: OrganizationType;
  /** Two-letter ISO code. */
  country: string;
  primaryColor: string;
  place: OrganizationPlace;
  logoUrl: string | null;
  /** SHA-256 of the logo file. */
  logoHash: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

/** What a tournament shows of each organizer. */
export type OrganizerView = Pick<Organization, "id" | "name" | "type" | "country" | "primaryColor" | "logoUrl" | "logoHash">;

export const DEFAULT_PRIMARY_COLOR = "#02B16B";
const COLLECTION = "organizations";
const CACHE_TTL_MS = 30_000;
const MAX_NAME_LENGTH = 80;
const MAX_LOGO_BYTES = 2 * 1024 * 1024;
const logoExtensions: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png" };

let cache: { until: number; byId: Map<string, Organization> } | null = null;

/** Forgets the organizations that this server instance read, after a change. */
export function forgetOrganizations(): void {
  cache = null;
}

function toIsoString(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

export function readPrimaryColor(value: unknown): string {
  const color = String(value ?? "").trim().toUpperCase();
  return /^#[0-9A-F]{6}$/.test(color) ? color : DEFAULT_PRIMARY_COLOR;
}

function mapOrganization(snapshot: FirebaseFirestore.DocumentSnapshot): Organization {
  return {
    id: snapshot.id,
    name: String(snapshot.get("name") ?? "").trim(),
    type: snapshot.get("type") === "FEDERATION" ? "FEDERATION" : "CLUB",
    country: String(snapshot.get("country") ?? "").trim().toUpperCase(),
    primaryColor: readPrimaryColor(snapshot.get("primaryColor")),
    place: readPlace(snapshot.get("place")),
    logoUrl: typeof snapshot.get("logoUrl") === "string" ? snapshot.get("logoUrl") : null,
    logoHash: typeof snapshot.get("logoHash") === "string" ? snapshot.get("logoHash") : null,
    createdAt: toIsoString(snapshot.get("createdAt")),
    updatedAt: toIsoString(snapshot.get("updatedAt")),
  };
}

/** The stored place of an organization. A missing or broken value is an empty place. */
export function readPlace(value: unknown): OrganizationPlace {
  if (value == null || typeof value !== "object") return { ...EMPTY_PLACE };
  const record = value as Record<string, unknown>;
  const field = (key: string) => typeof record[key] === "string" ? (record[key] as string).trim() : "";
  return { mapUrl: field("mapUrl"), name: field("name"), address: field("address"), city: field("city") };
}

function placeText(value: unknown, field: string, maxLength: number): string {
  const text = String(value ?? "").trim();
  if (text.length > maxLength) throw badRequest(`place.${field} must contain at most ${maxLength} characters`);
  return text;
}

/**
 * Reads the place of a request. The place is optional: a missing place, or one with every field empty, is no place.
 * A place with some field needs a Google Maps link or an address, so players can find it.
 */
export function parsePlace(value: unknown): OrganizationPlace {
  if (value == null) return { ...EMPTY_PLACE };
  if (typeof value !== "object" || Array.isArray(value)) throw badRequest("place must be an object");
  const record = value as Record<string, unknown>;
  let mapUrl: string;
  try {
    mapUrl = normalizeMapUrl(record.mapUrl);
  } catch (_error) {
    throw badRequest("place.mapUrl must be an https link to Google Maps");
  }
  const place = {
    mapUrl,
    name: placeText(record.name, "name", MAX_PLACE_NAME_LENGTH),
    address: placeText(record.address, "address", MAX_PLACE_ADDRESS_LENGTH),
    city: placeText(record.city, "city", MAX_PLACE_CITY_LENGTH),
  };
  const hasSomething = place.mapUrl !== "" || place.name !== "" || place.address !== "" || place.city !== "";
  if (hasSomething && place.mapUrl === "" && place.address === "") {
    throw badRequest("place needs a Google Maps link or an address");
  }
  return place;
}

/** Every organization by id. The list is short, so each server instance keeps it for a short time. */
export async function organizationsById(): Promise<Map<string, Organization>> {
  if (cache && cache.until > Date.now()) return cache.byId;
  const snapshot = await db.collection(COLLECTION).get();
  const byId = new Map(snapshot.docs.map((document) => [document.id, mapOrganization(document)]));
  cache = { until: Date.now() + CACHE_TTL_MS, byId };
  return byId;
}

export async function listOrganizations(): Promise<Organization[]> {
  return [...(await organizationsById()).values()].sort((left, right) => left.name.localeCompare(right.name));
}

export async function getOrganization(id: string): Promise<Organization> {
  const snapshot = await db.doc(`${COLLECTION}/${id}`).get();
  if (!snapshot.exists) throw notFound("Organization not found");
  return mapOrganization(snapshot);
}

export function organizerView(organization: Organization): OrganizerView {
  const { id, name, type, country, primaryColor, logoUrl, logoHash } = organization;
  return { id, name, type, country, primaryColor, logoUrl, logoHash };
}

/** The organizer ids of a stored tournament, in order and without repeats. */
export function readOrganizerIds(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map((item) => String(item ?? "").trim()).filter((id) => id !== ""))];
}

/**
 * The organizer ids that a request sends, checked against the stored organizations. The first one is the main
 * organizer. A tournament needs at least one.
 */
export async function parseOrganizerIds(value: unknown): Promise<string[]> {
  if (!Array.isArray(value)) throw badRequest("organizerIds must be an array of organization ids");
  const ids = readOrganizerIds(value);
  if (ids.length === 0) throw badRequest("A tournament needs at least one organizer");
  if (ids.length !== value.length) throw badRequest("organizerIds must not repeat an organization");
  await assertOrganizationsExist(ids);
  return ids;
}

export async function assertOrganizationsExist(ids: readonly string[]): Promise<void> {
  const known = await organizationsById();
  let missing = ids.filter((id) => !known.has(id));
  if (missing.length > 0) {
    // An organization created on another server instance may be missing from the cache.
    forgetOrganizations();
    const fresh = await organizationsById();
    missing = ids.filter((id) => !fresh.has(id));
  }
  if (missing.length > 0) throw badRequest(`Unknown organization (${missing.join(", ")})`);
}

/** The organizers of a tournament as it shows them, in order. An organization that no longer exists is left out. */
export async function organizersOf(organizerIds: readonly string[]): Promise<OrganizerView[]> {
  if (organizerIds.length === 0) return [];
  const known = await organizationsById();
  return organizerIds.map((id) => known.get(id)).filter((item): item is Organization => item != null).map(organizerView);
}

function hasValidLogoSignature(bytes: Buffer, contentType: string): boolean {
  if (contentType === "image/jpeg") {
    return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  }
  return bytes.length >= 8
    && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
}

/** Saves the logo of an organization in Storage. Each save gets a new file name, so a cached old logo never shows. */
export async function saveOrganizationLogo(
  organizationId: string,
  contentType: string,
  dataBase64: string,
): Promise<{ path: string; url: string; hash: string }> {
  const extension = logoExtensions[contentType];
  if (!extension) throw badRequest("The logo must be a JPEG or PNG image");
  const bytes = Buffer.from(dataBase64, "base64");
  if (bytes.length === 0) throw badRequest("The logo is empty");
  if (bytes.length > MAX_LOGO_BYTES) throw badRequest("The logo must be 2 MB or smaller");
  if (!hasValidLogoSignature(bytes, contentType)) throw badRequest("The logo data does not match its image type");
  const hash = createHash("sha256").update(bytes).digest("hex");
  const path = `organizationLogos/${organizationId}-${hash.slice(0, 12)}.${extension}`;
  const token = randomUUID();
  await storage.file(path).save(bytes, {
    contentType,
    metadata: { cacheControl: "public, max-age=604800", metadata: { firebaseStorageDownloadTokens: token } },
  });
  return {
    path,
    url: `https://firebasestorage.googleapis.com/v0/b/${storage.name}/o/${encodeURIComponent(path)}?alt=media&token=${token}`,
    hash,
  };
}

export type OrganizationInput = {
  name: string;
  type: OrganizationType;
  country: string;
  primaryColor: string;
  place: OrganizationPlace;
  /** A new logo. Null keeps the current one. */
  logo: { contentType: string; dataBase64: string } | null;
  removeLogo: boolean;
};

/** Reads and checks the body of a create or an update. */
export function parseOrganizationInput(body: unknown): OrganizationInput {
  const record = body != null && typeof body === "object" ? body as Record<string, unknown> : {};
  const name = String(record.name ?? "").trim();
  if (name === "") throw badRequest("The organization needs a name");
  if (name.length > MAX_NAME_LENGTH) throw badRequest(`The name must have at most ${MAX_NAME_LENGTH} characters`);
  const type = record.type === "FEDERATION" ? "FEDERATION" : record.type === "CLUB" ? "CLUB" : null;
  if (type == null) throw badRequest("type must be CLUB or FEDERATION");
  const country = String(record.country ?? "").trim().toUpperCase();
  if (!WORLD_COUNTRY_CODES.has(country)) throw badRequest("country must be the two-letter code of a real country");
  const primaryColor = String(record.primaryColor ?? DEFAULT_PRIMARY_COLOR).trim().toUpperCase();
  if (!/^#[0-9A-F]{6}$/.test(primaryColor)) throw badRequest("primaryColor must use #RRGGBB format");
  const contentType = record.logoContentType == null ? null : String(record.logoContentType).trim();
  const dataBase64 = record.logoDataBase64 == null ? null : String(record.logoDataBase64).trim();
  if ((contentType == null) !== (dataBase64 == null)) throw badRequest("The logo type and the logo data go together");
  return {
    name,
    type,
    country,
    primaryColor,
    place: parsePlace(record.place),
    logo: contentType != null && dataBase64 != null ? { contentType, dataBase64 } : null,
    removeLogo: record.removeLogo === true,
  };
}

async function assertNameAvailable(name: string, exceptId?: string): Promise<void> {
  const taken = [...(await organizationsById()).values()]
    .some((organization) => organization.id !== exceptId && organization.name.toLowerCase() === name.toLowerCase());
  if (taken) throw badRequest("Another organization already has this name");
}

/** The tournaments that some organizations own or co-organize change their look, so the lists read them again. */
async function afterChange(): Promise<void> {
  forgetOrganizations();
  await bumpGlobalDataVersion("tournaments");
}

export async function createOrganization(input: OrganizationInput, createdByUid: string): Promise<Organization> {
  await assertNameAvailable(input.name);
  const ref = db.collection(COLLECTION).doc();
  const logo = input.logo ? await saveOrganizationLogo(ref.id, input.logo.contentType, input.logo.dataBase64) : null;
  await ref.set({
    name: input.name,
    type: input.type,
    country: input.country,
    primaryColor: input.primaryColor,
    place: input.place,
    logoUrl: logo?.url ?? null,
    logoPath: logo?.path ?? null,
    logoHash: logo?.hash ?? null,
    createdByUid,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  await afterChange();
  return mapOrganization(await ref.get());
}

export async function updateOrganization(id: string, input: OrganizationInput): Promise<Organization> {
  const ref = db.doc(`${COLLECTION}/${id}`);
  const before = await ref.get();
  if (!before.exists) throw notFound("Organization not found");
  await assertNameAvailable(input.name, id);
  const oldLogoPath = String(before.get("logoPath") ?? "").trim();
  const logo = input.logo ? await saveOrganizationLogo(id, input.logo.contentType, input.logo.dataBase64) : undefined;
  const update: Record<string, unknown> = {
    name: input.name,
    type: input.type,
    country: input.country,
    primaryColor: input.primaryColor,
    place: input.place,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (logo !== undefined || input.removeLogo) {
    update.logoUrl = logo?.url ?? null;
    update.logoPath = logo?.path ?? null;
    update.logoHash = logo?.hash ?? null;
  }
  await ref.update(update);
  if (oldLogoPath !== "" && "logoPath" in update && update.logoPath !== oldLogoPath) {
    await storage.file(oldLogoPath).delete({ ignoreNotFound: true }).catch(() => undefined);
  }
  await afterChange();
  return mapOrganization(await ref.get());
}

/** The number of tournaments that an organization organizes or co-organizes. */
export async function countOrganizationTournaments(id: string): Promise<number> {
  const snapshot = await db.collection("tournaments").where("organizerIds", "array-contains", id).count().get();
  return snapshot.data().count;
}

/**
 * Deletes an organization that organizes no tournament. Its club admins lose it, and the ones that keep no
 * organization lose the club admin role.
 */
export async function deleteOrganization(id: string): Promise<{ affectedUids: string[] }> {
  const ref = db.doc(`${COLLECTION}/${id}`);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw notFound("Organization not found");
  if (await countOrganizationTournaments(id) > 0) {
    throw badRequest("The organization still organizes tournaments. Change their organizers first.");
  }
  const admins = await db.collection("users").where("adminOrganizations", "array-contains", id).get();
  await Promise.all(admins.docs.map((document) => document.ref.update({
    adminOrganizations: FieldValue.arrayRemove(id),
    updatedAt: FieldValue.serverTimestamp(),
  })));
  await ref.delete();
  const logoPath = String(snapshot.get("logoPath") ?? "").trim();
  if (logoPath !== "") await storage.file(logoPath).delete({ ignoreNotFound: true }).catch(() => undefined);
  await afterChange();
  return { affectedUids: admins.docs.map((document) => document.id) };
}

/** The logo file of the main organizer of a tournament, for the ID cards. */
export async function mainOrganizerLogoPath(organizerIds: readonly string[]): Promise<string> {
  const mainId = organizerIds[0];
  if (mainId == null) return "";
  const snapshot = await db.doc(`${COLLECTION}/${mainId}`).get();
  return snapshot.exists ? String(snapshot.get("logoPath") ?? "").trim() : "";
}
