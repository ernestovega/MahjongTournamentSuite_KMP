import assert from "node:assert/strict";
import test from "node:test";

import { slotCleanupOf } from "./retiredSlotFields";

const isDelete = (value: unknown) => typeof value === "object" && value !== null && value.constructor.name === "DeleteTransform";

test("a slot with a non-member becomes an open slot", () => {
  const update = slotCleanupOf({
    id: 7, name: "Ann Guest", country: "ITA", assignedCountry: "ITA", nonMember: { firstName: "Ann", lastName: "Guest", country: "ITA" },
  }, 7)!;

  assert.ok(isDelete(update.nonMember));
  assert.equal(update.name, "Player 7");
  assert.equal(update.country, "");
  assert.equal(update.assignedCountry, "");
  assert.ok(update.updatedAt != null);
});

test("a slot with an empty non-member field only loses the field and keeps its name", () => {
  const update = slotCleanupOf({ id: 3, name: "Ana Perez", country: "ESP", assignedEmaId: "00000001", nonMember: null }, 3)!;

  assert.ok(isDelete(update.nonMember));
  assert.equal(update.name, undefined);
  assert.equal(update.country, undefined);
});

test("the retired invitation fields are removed from a slot", () => {
  const update = slotCleanupOf({
    id: 1, assignedEmaId: "00000001", accountUid: "uid", invitedEmail: "a@b.es", invitationStatus: "INVITED", invitedAt: new Date(),
    notifiedAt: new Date(), email: "pending@b.es",
  }, 1)!;

  assert.ok(isDelete(update.invitedEmail));
  assert.ok(isDelete(update.invitationStatus));
  assert.ok(isDelete(update.invitedAt));
  assert.ok(isDelete(update.notifiedAt));
  // The EMA number and the pending email of the import stay.
  assert.equal(update.assignedEmaId, undefined);
  assert.equal("email" in update, false);
});

test("a clean slot needs no update, so a second run changes nothing", () => {
  assert.equal(slotCleanupOf({ id: 1, name: "Ana", assignedEmaId: "00000001", accountUid: "uid", email: "a@b.es" }, 1), null);
  assert.equal(slotCleanupOf({ id: 2, name: "Player 2", country: "", assignedCountry: "" }, 2), null);
});
