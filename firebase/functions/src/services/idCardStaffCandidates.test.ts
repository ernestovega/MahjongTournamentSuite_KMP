import assert from "node:assert/strict";
import test from "node:test";

import { idCardStaffCandidatesOf } from "./idCardStaffCandidates";

const user = (uid: string, role: "PLAYER" | "EDITOR" | "CLUB_ADMIN" | "SUPER_ADMIN", extra: Partial<{ alias: string; emaId: string | null; disabled: boolean; tournaments: string[] }> = {}) => ({
  uid,
  alias: extra.alias ?? "",
  emaId: extra.emaId ?? null,
  role,
  disabled: extra.disabled ?? false,
  tournamentAssignments: (extra.tournaments ?? []).map((tournamentId) => ({ tournamentId, tournamentName: tournamentId })),
});

test("the staff of the tournament comes first, then other editors and admins, then players", () => {
  const candidates = idCardStaffCandidatesOf(
    [
      user("p1", "PLAYER", { emaId: "1", tournaments: ["t1"] }),
      user("e2", "EDITOR", { alias: "Zoe" }),
      user("a1", "SUPER_ADMIN", { alias: "Admin" }),
      user("e1", "EDITOR", { alias: "Bea", tournaments: ["t1"] }),
      user("x", "EDITOR", { alias: "Off", disabled: true }),
      user("n", "EDITOR"),
    ],
    "t1",
    new Map([["1", "Ana Pérez"]]),
  );
  assert.deepEqual(candidates.map((it) => [it.name, it.inTournament]), [
    ["Bea", true],
    ["Zoe", false],
    ["Admin", false],
    ["Ana Pérez", false],
  ]);
  assert.ok(candidates.every((it) => !("email" in it)));
});
