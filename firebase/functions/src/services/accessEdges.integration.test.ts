import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it } from "node:test";

import { requireAuthEmulator } from "../testing/authFixtures";
import { loadAccess, tournamentOrganizers } from "./accessService";

/** The access of accounts that do not exist, and the caches when they fill up. */
describe("access at the edges", () => {
  before(requireAuthEmulator);

  it("gives an account that does not exist no role, as a disabled account", async () => {
    const access = await loadAccess(`gone-${randomUUID()}`);
    assert.equal(access.disabled, true);
    assert.equal(access.superAdmin, false);
    assert.equal(access.editor, false);
    assert.equal(access.adminOrganizations.size, 0);
  });

  it("starts the cache of the organizers again when it holds too many tournaments", async () => {
    const prefix = `gone-${randomUUID()}`;
    for (let start = 0; start < 2001; start += 200) {
      await Promise.all(Array.from({ length: Math.min(200, 2001 - start) }, (_, index) => tournamentOrganizers(`${prefix}-${start + index}`)));
    }
    assert.equal(await tournamentOrganizers(`${prefix}-0`), null);
  });
});
