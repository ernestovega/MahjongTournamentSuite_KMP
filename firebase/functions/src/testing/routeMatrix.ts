import assert from "node:assert/strict";

import { requireRegisteredPlayer } from "../api/middleware/requireRegisteredPlayer";
import { requireAuth } from "../api/middleware/requireAuth";
import type { PermissionGuard } from "../api/middleware/requirePermission";
import { buildApp, MOUNTED_ROUTERS } from "../api/app";
import type { Permission } from "../models/permissions";

/**
 * Who may call what. The table is written by hand, so it states the intended rules: each route names the permission
 * that it checks. "@" marks a permission that the route checks inside the tournament of its path.
 * A test compares it with the guards of the real routes. A new route fails that test until it is listed here.
 * ROLE_PERMISSIONS (models/permissions.ts) says which role has which permission, and the tests below call every
 * route as each role, inside and outside its scope.
 */
export type Access = "PUBLIC" | "SIGNED_IN" | "PLAYER" | Permission | `@${Permission}`;

export const MATRIX: Array<[string, string, Access]> = [
  ["GET", "/health", "PUBLIC"],
  ["GET", "/version", "PUBLIC"],
  ["GET", "/config/", "PUBLIC"],
  ["POST", "/telemetry/errors", "PUBLIC"],
  ["GET", "/admin/activity", "tournament.activity"],
  ["GET", "/admin/error-groups", "dev.errors"],
  ["GET", "/admin/error-groups/:id", "dev.errors"],
  ["POST", "/auth/signIn", "PUBLIC"],
  ["POST", "/auth/passwordReset", "PUBLIC"],
  ["POST", "/support/message", "PUBLIC"],
  // A person registers with an account or with the token of the links of the emails, so the routes check it inside.
  ["POST", "/registrations/:tournamentId", "PUBLIC"],
  ["POST", "/registrations/:tournamentId/confirm", "PUBLIC"],
  ["GET", "/registrations/:tournamentId/me", "PUBLIC"],
  ["PUT", "/registrations/:tournamentId/me", "PUBLIC"],
  ["DELETE", "/registrations/:tournamentId/me", "PUBLIC"],
  ["POST", "/registrations/:tournamentId/me/team", "PUBLIC"],
  ["PUT", "/registrations/:tournamentId/me/team", "PUBLIC"],
  ["DELETE", "/registrations/:tournamentId/me/team", "PUBLIC"],
  ["POST", "/registrations/:tournamentId/me/team/members", "PUBLIC"],
  ["DELETE", "/registrations/:tournamentId/me/team/members/:emaId", "PUBLIC"],
  ["POST", "/registrations/:tournamentId/no-ema", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/registrations", "PUBLIC"],
  ["GET", "/public/registration-links/:slug", "PUBLIC"],
  ["GET", "/tournaments/:tournamentId/registrations", "@tournament.playerEmails"],
  ["POST", "/tournaments/:tournamentId/registrations", "@tournament.playerEmails"],
  ["POST", "/tournaments/:tournamentId/registrations/import", "@tournament.playerEmails"],
  ["PUT", "/tournaments/:tournamentId/registrations/:emaId", "@tournament.playerEmails"],
  ["DELETE", "/tournaments/:tournamentId/registrations/:emaId", "@tournament.playerEmails"],
  ["PUT", "/tournaments/:tournamentId/registration-settings", "@tournament.configure"],
  ["POST", "/tournaments/:tournamentId/draw", "@tournament.configure"],
  ["POST", "/tournaments/:tournamentId/player-count", "@tournament.configure"],
  ["POST", "/auth/refresh", "PUBLIC"],
  ["GET", "/auth/me", "SIGNED_IN"],
  ["PUT", "/auth/me", "SIGNED_IN"],
  ["PUT", "/auth/me/photo", "SIGNED_IN"],
  ["DELETE", "/auth/me/photo", "SIGNED_IN"],
  ["PUT", "/auth/me/photo-visibility", "SIGNED_IN"],
  ["DELETE", "/auth/me", "SIGNED_IN"],
  ["GET", "/admin/whoami", "SIGNED_IN"],
  ["GET", "/countries/", "SIGNED_IN"],
  ["GET", "/ema-player-registry/", "SIGNED_IN"],
  ["GET", "/admin/users", "accounts.view"],
  ["POST", "/admin/users", "accounts.createPlayer"],
  ["PUT", "/admin/users/:uid", "accounts.view"],
  ["GET", "/admin/users/lookup", "accounts.view"],
  ["GET", "/sync/manifest", "SIGNED_IN"],
  ["GET", "/tournaments/", "SIGNED_IN"],
  ["POST", "/venues/resolve-link", "tools.venues"],
  ["POST", "/tournaments/id-card-proof", "tools.idCards"],
  ["POST", "/tournaments/id-card-preview", "tools.idCards"],
  ["GET", "/tournaments/statistics/overview", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/groups", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/me", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/me/standings", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/persons/:emaId", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/persons/:emaId/standings", "SIGNED_IN"],
  ["POST", "/ema-player-registry/", "ema.create"],
  ["PUT", "/ema-player-registry/:emaId", "ema.edit"],
  ["POST", "/ema-player-registry/delete/preview", "ema.delete"],
  ["POST", "/ema-player-registry/delete", "ema.delete"],
  ["PUT", "/ema-player-registry/:emaId/photo", "ema.edit"],
  ["PUT", "/admin/users/:uid/disabled", "accounts.disable"],
  ["DELETE", "/admin/users/:uid", "accounts.delete"],
  ["POST", "/admin/users/password-resets", "accounts.passwordReset"],
  ["POST", "/admin/users/delete", "accounts.delete"],
  ["PUT", "/admin/users/:uid/photo", "accounts.photo"],
  ["PUT", "/admin/users/:uid/permissions", "accounts.manageStaff"],
  ["GET", "/admin/roles", "accounts.view"],
  ["POST", "/admin/roles", "permissions.manage"],
  ["PUT", "/admin/roles/:roleId", "permissions.manage"],
  ["DELETE", "/admin/roles/:roleId", "permissions.manage"],
  ["POST", "/admin/users/:uid/removal", "accounts.requestRemoval"],
  ["GET", "/admin/account-requests", "accounts.reviewRequests"],
  ["GET", "/admin/organizations", "tournament.view"],
  ["POST", "/admin/organizations", "organizations.create"],
  ["PUT", "/admin/organizations/:organizationId", "organizations.edit"],
  ["DELETE", "/admin/organizations/:organizationId", "organizations.delete"],
  ["POST", "/admin/account-requests/:requestId", "accounts.reviewRequests"],
  ["DELETE", "/admin/users/:uid/photo", "accounts.photo"],
  ["POST", "/tournaments/", "tournaments.create"],
  ["PUT", "/tournaments/:tournamentId/settings", "@tournament.configure"],
  ["DELETE", "/tournaments/:tournamentId", "@tournament.delete"],
  ["PUT", "/tournaments/:tournamentId", "@tournament.configure"],
  ["POST", "/tournaments/:tournamentId/tables/:roundId/:tableId/reset", "@tournament.resetTable"],
  ["GET", "/tournaments/:tournamentId/sync/manifest", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/id-cards", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/id-cards/staff-names", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/id-cards/staff-candidates", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/id-cards/staff-names", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/id-list", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/table-qr-codes", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/score-sheets", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/integrity", "@tournament.view"],
  ["POST", "/tournaments/:tournamentId/ema-report", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/users/lookup", "@tournament.members"],
  ["GET", "/tournaments/:tournamentId/members", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/members/:uid", "@tournament.members"],
  ["DELETE", "/tournaments/:tournamentId/members/:uid", "@tournament.members"],
  ["GET", "/tournaments/:tournamentId/players", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/players/:playerId", "@tournament.edit"],
  ["PUT", "/tournaments/:tournamentId/players/:playerId/substitution", "@tournament.edit"],
  ["DELETE", "/tournaments/:tournamentId/players/:playerId/substitution", "@tournament.edit"],
  ["POST", "/tournaments/:tournamentId/player-accounts/send", "@tournament.edit"],
  ["PUT", "/tournaments/:tournamentId/player-emails", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId/player-import/export", "@tournament.view"],
  ["POST", "/tournaments/:tournamentId/player-import/preview", "@tournament.edit"],
  ["POST", "/tournaments/:tournamentId/player-import/apply", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId/teams", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/teams/:teamId", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId/rounds", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/round-clocks", "@tournament.view"],
  ["POST", "/tournaments/:tournamentId/rounds/:roundId/clock", "@tournament.edit"],
  ["POST", "/tournaments/:tournamentId/rounds/:roundId/control", "@tournament.roundControl"],
  ["POST", "/tournaments/:tournamentId/complete-open-tables", "@tournament.roundControl"],
  ["POST", "/tournaments/:tournamentId/results-visibility", "@tournament.roundControl"],
  ["GET", "/tournaments/:tournamentId/tables", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/tables-with-hands", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/tables/:roundId/:tableId", "@tournament.view"],
  ["GET", "/player/tournaments/:tournamentId/my-hidden-tables", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/hands/preview", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/penalties", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/hands", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/seats", "PLAYER"],
  ["PUT", "/tournaments/:tournamentId/tables/:roundId/:tableId/state", "@tournament.edit"],
  ["GET", "/public/sync/manifest", "PUBLIC"],
  ["GET", "/public/tournaments", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/sync/manifest", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/players", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/teams", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/rounds", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/round-clocks", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/tables", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/tables-with-hands", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/tables/:roundId/:tableId", "PUBLIC"],
  ["GET", "/public/statistics/overview", "PUBLIC"],
  ["GET", "/public/statistics/groups", "PUBLIC"],
  ["GET", "/public/statistics/persons/:emaId", "PUBLIC"],
  ["GET", "/public/statistics/persons/:emaId/standings", "PUBLIC"],
  ["GET", "/public/players", "PUBLIC"],
  ["GET", "/public/countries", "PUBLIC"],
  ["GET", "/me/", "SIGNED_IN"],
];

export function actualGuards(): Map<string, Access> {
  const app = buildApp() as unknown as { router: { stack: any[] } };
  const result = new Map<string, Access>();
  const walk = (layer: any, prefix: string) => {
    if (layer.route) {
      const handlers: unknown[] = layer.route.stack.map((item: any) => item.handle);
      const guard = handlers.find((handler) => typeof (handler as Partial<PermissionGuard>).permission === "string") as PermissionGuard | undefined;
      const access: Access = guard ? (guard.scope === "tournament" ? `@${guard.permission}` : guard.permission)
        : handlers.includes(requireRegisteredPlayer) ? "PLAYER"
        : handlers.includes(requireAuth) ? "SIGNED_IN"
        : "PUBLIC";
      for (const method of Object.keys(layer.route.methods)) result.set(`${method.toUpperCase()} ${prefix}${layer.route.path}`, access);
    } else if (layer.name === "router") {
      layer.handle.stack.forEach((inner: any) => walk(inner, prefix));
    }
  };
  // The mounted routers come in the order of MOUNTED_ROUTERS, so the order gives each one its prefix.
  const routerLayers = app.router.stack.filter((layer) => layer.name === "router");
  assert.equal(routerLayers.length, MOUNTED_ROUTERS.length);
  routerLayers.forEach((layer, index) => walk(layer, MOUNTED_ROUTERS[index][0]));
  app.router.stack.filter((layer) => layer.route).forEach((layer) => walk(layer, ""));
  return result;
}

/** The path of a route with its parameters filled in, for the tournament [tournamentId]. */
export const concrete = (path: string, tournamentId: string): string => {
  const filled = path
    .replace(":tournamentId", tournamentId)
    .replace(":roundId", "1").replace(":tableId", "1").replace(":playerId", "1").replace(":teamId", "1")
    .replace(":uid", "unknown-user").replace(":invitationId", "unknown-invitation").replace(":submissionId", "unknown-submission")
    .replace(":emaId", "unknown-ema").replace(":requestId", "unknown-request").replace(":organizationId", "unknown-organization").replace(":roleId", "unknown-role");
  // A club admin reads the activity of one tournament of its organizations.
  return filled === "/admin/activity" ? `${filled}?tournamentId=${tournamentId}` : filled;
};
