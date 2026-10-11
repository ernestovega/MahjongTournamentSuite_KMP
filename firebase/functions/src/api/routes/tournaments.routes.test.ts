import assert from "node:assert/strict";
import test from "node:test";

import { requireAdmin } from "../middleware/requireAdmin";
import { requireAuth } from "../middleware/requireAuth";
import { requireTournamentEditor } from "../middleware/requireTournamentEditor";
import { tournamentsRouter } from "./tournaments.routes";

type Route = { method: string; path: string; handlers: unknown[] };

function routes(): Route[] {
  const router = tournamentsRouter() as unknown as {
    stack: Array<{ route?: { path: string; methods: Record<string, boolean>; stack: Array<{ handle: unknown }> } }>;
  };
  return router.stack.flatMap((layer) => {
    if (!layer.route) return [];
    const route = layer.route;
    return Object.keys(route.methods).map((method) => ({
      method: method.toUpperCase(),
      path: route.path,
      handlers: route.stack.map((item) => item.handle),
    }));
  });
}

function find(method: string, path: string): Route {
  const route = routes().find((item) => item.method === method && item.path === path);
  assert.ok(route, `route ${method} ${path} exists`);
  return route;
}

test("every tournament route needs a signed-in user", () => {
  for (const route of routes()) {
    assert.ok(route.handlers.includes(requireAuth), `${route.method} ${route.path} uses requireAuth`);
  }
});

test("resetting a table is for admins only", () => {
  const reset = find("POST", "/:tournamentId/tables/:roundId/:tableId/reset");

  assert.ok(reset.handlers.includes(requireAdmin));
});

test("editors can read and save tables of their tournament but cannot reset them", () => {
  const editorRoutes: Array<[string, string]> = [
    ["GET", "/:tournamentId/tables"],
    ["GET", "/:tournamentId/tables/:roundId/:tableId"],
    ["GET", "/:tournamentId/rounds/:roundId/tables-with-hands"],
    ["PUT", "/:tournamentId/tables/:roundId/:tableId/state"],
    ["GET", "/:tournamentId/sync/manifest"],
  ];
  for (const [method, path] of editorRoutes) {
    const route = find(method, path);
    assert.ok(route.handlers.includes(requireTournamentEditor), `${method} ${path} checks membership`);
    assert.ok(!route.handlers.includes(requireAdmin), `${method} ${path} stays open to editors`);
  }
});

test("every route with a tournament id checks that the user may work in that tournament", () => {
  const adminOnlyOrGlobal = (route: Route) =>
    route.handlers.includes(requireAdmin) || !route.path.startsWith("/:tournamentId");
  for (const route of routes().filter((item) => item.path.startsWith("/:tournamentId"))) {
    if (adminOnlyOrGlobal(route)) continue;
    assert.ok(
      route.handlers.includes(requireTournamentEditor),
      `${route.method} ${route.path} needs requireTournamentEditor or requireAdmin`,
    );
  }
});

test("tables can be written only through the versioned save", () => {
  const writes = routes().filter((route) => route.path.includes("/tables/") && ["PUT", "POST", "PATCH", "DELETE"].includes(route.method));

  assert.deepEqual(
    writes.map((route) => `${route.method} ${route.path}`).sort(),
    [
      "POST /:tournamentId/tables/:roundId/:tableId/reset",
      "PUT /:tournamentId/tables/:roundId/:tableId/state",
    ],
  );
});
