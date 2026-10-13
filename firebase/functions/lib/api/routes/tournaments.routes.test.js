"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const requireAdmin_1 = require("../middleware/requireAdmin");
const requireAuth_1 = require("../middleware/requireAuth");
const requireTournamentEditor_1 = require("../middleware/requireTournamentEditor");
const requireTournamentReader_1 = require("../middleware/requireTournamentReader");
const tournaments_routes_1 = require("./tournaments.routes");
function routes() {
    const router = (0, tournaments_routes_1.tournamentsRouter)();
    return router.stack.flatMap((layer) => {
        if (!layer.route)
            return [];
        const route = layer.route;
        return Object.keys(route.methods).map((method) => ({
            method: method.toUpperCase(),
            path: route.path,
            handlers: route.stack.map((item) => item.handle),
        }));
    });
}
function find(method, path) {
    const route = routes().find((item) => item.method === method && item.path === path);
    strict_1.default.ok(route, `route ${method} ${path} exists`);
    return route;
}
(0, node_test_1.default)("every tournament route needs a signed-in user", () => {
    for (const route of routes()) {
        strict_1.default.ok(route.handlers.includes(requireAuth_1.requireAuth), `${route.method} ${route.path} uses requireAuth`);
    }
});
(0, node_test_1.default)("resetting a table is for admins only", () => {
    const reset = find("POST", "/:tournamentId/tables/:roundId/:tableId/reset");
    strict_1.default.ok(reset.handlers.includes(requireAdmin_1.requireAdmin));
});
(0, node_test_1.default)("editors can read and save tables of their tournament but cannot reset them", () => {
    const editorRoutes = [
        ["GET", "/:tournamentId/tables"],
        ["GET", "/:tournamentId/tables/:roundId/:tableId"],
        ["GET", "/:tournamentId/rounds/:roundId/tables-with-hands"],
        ["PUT", "/:tournamentId/tables/:roundId/:tableId/state"],
        ["GET", "/:tournamentId/sync/manifest"],
    ];
    for (const [method, path] of editorRoutes) {
        const route = find(method, path);
        strict_1.default.ok(route.handlers.includes(requireTournamentEditor_1.requireTournamentEditor) || route.handlers.includes(requireTournamentReader_1.requireTournamentReader), `${method} ${path} checks membership`);
        strict_1.default.ok(!route.handlers.includes(requireAdmin_1.requireAdmin), `${method} ${path} stays open to editors`);
    }
});
(0, node_test_1.default)("every route with a tournament id checks that the user may work in that tournament", () => {
    const adminOnlyOrGlobal = (route) => route.handlers.includes(requireAdmin_1.requireAdmin) || !route.path.startsWith("/:tournamentId");
    for (const route of routes().filter((item) => item.path.startsWith("/:tournamentId"))) {
        if (adminOnlyOrGlobal(route))
            continue;
        strict_1.default.ok(route.handlers.includes(requireTournamentEditor_1.requireTournamentEditor) || route.handlers.includes(requireTournamentReader_1.requireTournamentReader), `${route.method} ${route.path} needs requireTournamentEditor, requireTournamentReader or requireAdmin`);
    }
});
(0, node_test_1.default)("tables can be written only through the versioned save", () => {
    const writes = routes().filter((route) => route.path.includes("/tables/") && ["PUT", "POST", "PATCH", "DELETE"].includes(route.method));
    strict_1.default.deepEqual(writes.map((route) => `${route.method} ${route.path}`).sort(), [
        "POST /:tournamentId/tables/:roundId/:tableId/reset",
        "PUT /:tournamentId/tables/:roundId/:tableId/state",
    ]);
});
//# sourceMappingURL=tournaments.routes.test.js.map