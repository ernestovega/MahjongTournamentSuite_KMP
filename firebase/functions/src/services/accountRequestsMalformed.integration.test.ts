import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { auth, db } from "../firebase";
import { setMailTransportForTests } from "./mailService";
import { createAccountRequest, listPendingAccountRequests, MAX_REASON_LENGTH, resolveAccountRequest } from "./accountRequests";

/** Requests to disable or delete an account whose documents miss fields, and accounts without an email. */
describe("account requests of malformed documents", () => {
  it("lists a request without its fields, and refuses a long reason", async () => {
    const id = `requests-malformed-${Date.now()}`;
    await db.doc(`accountRequests/${id}`).set({ status: "PENDING" });
    const listed = (await listPendingAccountRequests()).find((request) => request.id === id);
    assert.deepEqual(listed, {
      id, kind: "DISABLE", status: "PENDING", targetUid: "", targetEmail: "", reason: "", requestedByUid: "", requestedByEmail: "",
      createdAtMs: 0, resolvedByUid: null, resolvedAtMs: null,
    });
    const resolved = await resolveAccountRequest(id, false, "requests-admin");
    assert.equal(resolved.status, "REJECTED");

    await assert.rejects(
      createAccountRequest({ kind: "DELETE", targetUid: "x", reason: "a".repeat(MAX_REASON_LENGTH + 1), requestedByUid: "y" }),
      /at most 1000 characters/,
    );
  });

  it("saves a request about and by accounts without an email, and keeps one open request of each kind", async () => {
    setMailTransportForTests(async () => {});
    try {
      const target = await auth.createUser({});
      const requester = await auth.createUser({});
      const first = await createAccountRequest({ kind: "DELETE", targetUid: target.uid, reason: " ", requestedByUid: requester.uid });
      assert.deepEqual([first.targetEmail, first.requestedByEmail, first.reason], ["", "", ""]);
      const again = await createAccountRequest({ kind: "DELETE", targetUid: target.uid, reason: "again", requestedByUid: requester.uid });
      assert.equal(again.id, first.id);
    } finally {
      setMailTransportForTests(null);
    }
  });
});
