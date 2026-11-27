import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { requireEmulator } from "../testing/firestoreFixtures";
import {
  deleteOrganization,
  forgetOrganizations,
  listOrganizations,
  mainOrganizerLogoPath,
  parseOrganizationInput,
  updateOrganization,
} from "./organizationsService";

/** Organizations stored with fields missing, as an old version or a hand edit may leave them. */
describe("organizations with missing fields", () => {
  before(requireEmulator);

  it("reads, updates and deletes an organization that has only its id", async () => {
    const id = `org-bare-${randomUUID()}`;
    await db.doc(`organizations/${id}`).set({ createdAt: null });
    forgetOrganizations();
    const bare = (await listOrganizations()).find((organization) => organization.id === id);
    assert.deepEqual([bare?.name, bare?.type, bare?.country], ["", "CLUB", ""]);
    assert.equal(await mainOrganizerLogoPath([id]), "");
    assert.equal(await mainOrganizerLogoPath([`org-gone-${randomUUID()}`]), "");
    const updated = await updateOrganization(id, parseOrganizationInput({ name: `Bare ${randomUUID()}`, type: "FEDERATION", country: "ES" }));
    assert.equal(updated.type, "FEDERATION");
    await db.doc(`organizations/${id}`).update({ logoPath: null });
    await deleteOrganization(id);
    assert.equal((await db.doc(`organizations/${id}`).get()).exists, false);
  });
});
