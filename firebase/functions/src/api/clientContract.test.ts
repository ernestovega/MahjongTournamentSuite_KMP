import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

import { MOUNTED_ROUTERS } from "./app";

/**
 * The contract between the apps and the API: every call of the Kotlin client (data/.../FunctionsBackendApi.kt) must
 * reach a route of the API with the same method. A renamed or removed route, or a typo in the path of a new call
 * (registrations, number of players, table QR, ID cards...), fails here instead of on a phone at a tournament.
 */

type ServerRoute = { method: string; path: string };
type ClientCall = { method: string; path: string; line: number };

type Layer = {
  route?: { path: string | string[]; methods: Record<string, boolean> };
};

function serverRoutes(): ServerRoute[] {
  return MOUNTED_ROUTERS.flatMap(([prefix, build]) => {
    const router = build() as unknown as { stack: Layer[] };
    return router.stack.flatMap((layer) => {
      if (!layer.route) return [];
      const paths = Array.isArray(layer.route.path) ? layer.route.path : [layer.route.path];
      return paths.flatMap((path) =>
        Object.keys(layer.route!.methods).map((method) => ({ method: method.toUpperCase(), path: prefix + (path === "/" ? "" : path) })),
      );
    });
  });
}

const CLIENT_FILE = join(__dirname, "../../../../data/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/data/backend/FunctionsBackendApi.kt");

/** The calls of the client whose path is a text in the code. Calls that build the path in a function are left out. */
export function clientCalls(text: string): ClientCall[] {
  // Paths that start with a helper, such as ${playerTablePath(tournamentId, roundId, tableId)}/hands, take the text of
  // the helper: private fun playerTablePath(...) = "/player/tournaments/$tournamentId/tables/$roundId/$tableId".
  const helpers = new Map<string, string>();
  for (const match of text.matchAll(/fun (\w+)\([^)]*\)\s*=\s*"([^"]+)"/g)) helpers.set(match[1], match[2]);
  const source = text.replace(/\$\{(\w+)\([^)]*\)\}/g, (whole, name: string) => helpers.get(name) ?? whole);
  const calls: ClientCall[] = [];
  const lineOf = (index: number) => source.slice(0, index).split("\n").length;
  const patterns: Array<[RegExp, (match: RegExpExecArray) => [string, string]]> = [
    // get(path = "/x"), post<A, B>(path = "/x", ...), delete(path = "/x")
    [/\b(get|post|put|delete|patch)(?:<[^>(]*>)?\(\s*path\s*=\s*"([^"]+)"/g, (m) => [m[1], m[2]]],
    // httpClient.get(url("/x")), httpClient.get(readUrl("/x", ...)) and httpClient.post(longUrl("/x"))
    [/httpClient\.(get|post|put|delete|patch)\(\s*(?:url|readUrl|longUrl)\(\s*"([^"]+)"/g, (m) => [m[1], m[2]]],
    // registrationCall(HttpMethod.Post, "/registrations/...", ...)
    [/registrationCall(?:<[^>(]*>)?\(\s*HttpMethod\.(\w+),\s*"([^"]+)"/g, (m) => [m[1], m[2]]],
  ];
  for (const [pattern, read] of patterns) {
    for (let match = pattern.exec(source); match != null; match = pattern.exec(source)) {
      const [method, path] = read(match);
      calls.push({ method: method.toUpperCase(), path, line: lineOf(match.index) });
    }
  }
  return calls;
}

/** The path of a call as a pattern: the query goes away and each Kotlin template is any text. */
export function clientPathPattern(path: string): RegExp {
  const withoutQuery = path.replace(/\?.*$/, "");
  const parts = withoutQuery.split(/(\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*)/);
  const body = parts
    .map((part) => (part.startsWith("$") ? "(?:[^/]+(?:/[^/]+)*)" : part.replace(/[.*+?^()|[\]\\]/g, "\\$&")))
    .join("");
  return new RegExp(`^${body}/?$`);
}

/** A sample path that a route accepts, with `x1`, `x2`... in its parameters, to try the patterns of the client on it. */
function samplePath(route: string): string {
  let index = 0;
  return route.replace(/:[A-Za-z_][A-Za-z0-9_]*/g, () => `x${++index}`);
}

/** True when the route takes the call: same method and a path that the call can produce. */
function routeTakes(route: ServerRoute, call: ClientCall): boolean {
  if (route.method !== call.method) return false;
  return clientPathPattern(call.path).test(samplePath(route.path)) || routeMatchesLiteral(route.path, call.path);
}

/** A call without templates must be a path of the route, with its parameters as any segment. */
function routeMatchesLiteral(route: string, path: string): boolean {
  const literal = path.replace(/\?.*$/, "");
  if (literal.includes("$")) return false;
  const pattern = new RegExp(`^${route.replace(/[.*+?^()|[\]\\]/g, "\\$&").replace(/:[A-Za-z_][A-Za-z0-9_]*/g, "[^/]+")}/?$`);
  return pattern.test(literal);
}

test("the parser of the client finds the calls written in every form", () => {
  const source = [
    'suspend fun a(): X = get(path = "/config")',
    'suspend fun b(): X = post(\n        path = "/tournaments/$tournamentId/draw",\n        requestBody = request,\n    )',
    'val response = httpClient.get(url("/tournaments/$tournamentId/table-qr-codes?perSheet=$perSheet")) {',
    'registrationCall<OkResponseDto, Unit>(HttpMethod.Delete, "/registrations/${tournamentId.encodeURLPath()}/me", idToken, token, null)',
  ].join("\n");
  assert.deepEqual(
    clientCalls(source).map((call) => `${call.method} ${call.path}`).sort(),
    [
      "DELETE /registrations/${tournamentId.encodeURLPath()}/me",
      "GET /config",
      "GET /tournaments/$tournamentId/table-qr-codes?perSheet=$perSheet",
      "POST /tournaments/$tournamentId/draw",
    ],
  );
  assert.ok(clientPathPattern("/tournaments/$tournamentId/table-qr-codes?perSheet=$perSheet").test("/tournaments/x1/table-qr-codes"));
  assert.ok(!clientPathPattern("/tournaments/$tournamentId/draw").test("/tournaments/x1/player-count"));
});

test("every call of the Kotlin client reaches a route of the API with the same method", () => {
  const routes = serverRoutes();
  const calls = clientCalls(readFileSync(CLIENT_FILE, "utf8"));
  // The client has about a hundred calls. Far fewer means that the parser no longer reads the file.
  assert.ok(calls.length >= 90, `found ${calls.length} calls in FunctionsBackendApi.kt`);
  const missing = calls.filter((call) => !routes.some((route) => routeTakes(route, call)));
  assert.deepEqual(
    missing.map((call) => `FunctionsBackendApi.kt:${call.line} ${call.method} ${call.path}`),
    [],
    "calls of the app without a route in the API",
  );
});

test("the calls of the features of these days are in the contract", () => {
  const calls = clientCalls(readFileSync(CLIENT_FILE, "utf8")).map((call) => `${call.method} ${call.path.replace(/\?.*$/, "")}`);
  for (const expected of [
    // Registrations: the person, the public list, the short link, and the organizer.
    "POST /registrations/${tournamentId.encodeURLPath()}",
    "GET /public/registration-links/${slug.encodeURLPath()}",
    "GET /public/tournaments/${tournamentId.encodeURLPath()}/registrations",
    "PUT /tournaments/$tournamentId/registration-settings",
    "POST /tournaments/$tournamentId/registrations/import",
    "POST /tournaments/$tournamentId/draw",
    // Number of players of a drawn tournament.
    "POST /tournaments/$tournamentId/player-count",
    // QR of the tournament and the ID cards with their extra sheets.
    "GET /tournaments/$tournamentId/table-qr-codes",
    "GET /tournaments/$tournamentId/id-cards/staff-candidates",
    "PUT /tournaments/$tournamentId/id-cards/staff-names",
  ]) {
    assert.ok(calls.includes(expected), `the client calls ${expected}`);
  }
});
