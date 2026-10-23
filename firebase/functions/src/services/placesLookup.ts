/** What Google Places tells about the place of a map link. */
export type VenuePlace = {
  name: string | null;
  address: string | null;
  city: string | null;
  /** Two-letter country code. */
  countryCode: string | null;
};

type PlacesComponent = { longText?: string; shortText?: string; types?: string[] };
type PlacesResponse = {
  places?: { displayName?: { text?: string }; formattedAddress?: string; addressComponents?: PlacesComponent[] }[];
};

/** Sends the search to Places. The default sends it to Google. A test sends its own function. */
export type PlacesSearch = (body: Record<string, unknown>, apiKey: string) => Promise<PlacesResponse>;

const PLACES_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText";
const FIELD_MASK = "places.displayName,places.formattedAddress,places.addressComponents";
const REQUEST_TIMEOUT_MS = 5000;
const COORDINATES = /^-?\d+(\.\d+)?,\s*-?\d+(\.\d+)?$/;
/** The link holds the coordinates of the place after "!3d" and "!4d", or after "@" for the map center. */
const PLACE_COORDINATES = /!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)/;
const VIEW_COORDINATES = /@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)/;

async function searchPlaces(body: Record<string, unknown>, apiKey: string): Promise<PlacesResponse> {
  const response = await fetch(PLACES_SEARCH_URL, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": apiKey, "x-goog-fieldmask": FIELD_MASK },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`Places answered ${response.status}`);
  return (await response.json()) as PlacesResponse;
}

function decodeLinkText(raw: string): string {
  try {
    return decodeURIComponent(raw.replace(/\+/g, " ")).trim();
  } catch (_error) {
    return "";
  }
}

/** The text of the place in a full Google Maps link, or null when the link has none. */
export function placeTextOf(link: string): string | null {
  let url: URL;
  try {
    url = new URL(link);
  } catch (_error) {
    return null;
  }
  const fromPath = /^\/maps\/(?:place|search)\/([^/]+)/.exec(url.pathname)?.[1];
  const raw = fromPath ?? url.searchParams.get("query") ?? url.searchParams.get("q");
  if (!raw) return null;
  const text = fromPath ? decodeLinkText(raw) : raw.trim();
  if (text.length === 0 || text.startsWith("@") || COORDINATES.test(text)) return null;
  return text;
}

function coordinatesOf(link: string): { latitude: number; longitude: number } | null {
  const match = PLACE_COORDINATES.exec(link) ?? VIEW_COORDINATES.exec(link);
  if (!match) return null;
  return { latitude: Number(match[1]), longitude: Number(match[2]) };
}

function componentOf(components: PlacesComponent[], types: string[]): PlacesComponent | undefined {
  for (const type of types) {
    const found = components.find((component) => component.types?.includes(type));
    if (found) return found;
  }
  return undefined;
}

/** The country is a separate field in the form, so the address does not end with it. */
function withoutCountry(address: string | undefined, country: string | undefined): string {
  const trimmed = (address ?? "").trim();
  const suffix = country ? `, ${country.trim()}` : "";
  return suffix && trimmed.endsWith(suffix) ? trimmed.slice(0, -suffix.length) : trimmed;
}

/**
 * Looks up the place of a full Google Maps link in Google Places. Returns null when the link has no place text,
 * when Places finds nothing, or when the request fails. The caller then reads the place from the link text.
 */
export async function lookupPlace(
  link: string,
  apiKey: string,
  search: PlacesSearch = searchPlaces,
): Promise<VenuePlace | null> {
  const text = placeTextOf(link);
  if (text === null || apiKey.length === 0) return null;

  const center = coordinatesOf(link);
  const body: Record<string, unknown> = { textQuery: text, languageCode: "en", pageSize: 1 };
  if (center) body.locationBias = { circle: { center, radius: 500 } };

  let response: PlacesResponse;
  try {
    response = await search(body, apiKey);
  } catch (_error) {
    return null;
  }
  const place = response.places?.[0];
  if (!place) return null;

  const components = place.addressComponents ?? [];
  const countryComponent = componentOf(components, ["country"]);
  const country = countryComponent?.shortText?.trim().toUpperCase();
  const city = componentOf(components, ["locality", "postal_town", "administrative_area_level_3", "administrative_area_level_2"])
    ?.longText?.trim();
  return {
    name: place.displayName?.text?.trim() || null,
    address: withoutCountry(place.formattedAddress, countryComponent?.longText) || null,
    city: city || null,
    countryCode: country && /^[A-Z]{2}$/.test(country) ? country : null,
  };
}
