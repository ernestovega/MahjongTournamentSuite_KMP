import assert from "node:assert/strict";
import test from "node:test";

import { deleteTournamentResources } from "./tournamentsService";

test("deletes the stored logo before the tournament tree", async () => {
  const calls: string[] = [];

  await deleteTournamentResources({
    logoPath: "tournamentLogos/tournament-1.png",
    deleteLogo: async (logoPath) => {
      calls.push(`logo:${logoPath}`);
    },
    deleteTournamentTree: async () => {
      calls.push("tree");
    },
  });

  assert.deepEqual(calls, ["logo:tournamentLogos/tournament-1.png", "tree"]);
});

test("deletes the tournament tree when no stored logo exists", async () => {
  const calls: string[] = [];

  await deleteTournamentResources({
    logoPath: "",
    deleteLogo: async () => {
      calls.push("logo");
    },
    deleteTournamentTree: async () => {
      calls.push("tree");
    },
  });

  assert.deepEqual(calls, ["tree"]);
});

test("keeps the tournament tree when stored logo deletion fails", async () => {
  let treeDeleted = false;

  await assert.rejects(
    deleteTournamentResources({
      logoPath: "tournamentLogos/tournament-1.png",
      deleteLogo: async () => {
        throw new Error("Storage unavailable");
      },
      deleteTournamentTree: async () => {
        treeDeleted = true;
      },
    }),
    /Storage unavailable/,
  );

  assert.equal(treeDeleted, false);
});
