import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { auth } from "../firebase";
import { deleteAccountChecked } from "./accountDeletes";
import * as usersService from "./usersService";

/** Account work when Firebase Auth fails for a reason other than a missing account. */
describe("account lookups that fail", () => {
  it("passes on a failed lookup when deleting an account", async () => {
    const failing = mock.method(usersService, "getManagedUser", async () => {
      throw Object.assign(new Error("Auth is down"), { code: "auth/internal-error" });
    });
    try {
      await assert.rejects(deleteAccountChecked("actor", "someone"), /Auth is down/);
    } finally {
      failing.mock.restore();
    }
  });

  it("counts a password reset whose account cannot be read as a failed email", async () => {
    const quiet = mock.method(console, "error", () => {});
    const failing = mock.method(auth, "getUser", async () => {
      throw Object.assign(new Error("Auth is down"), { code: "auth/internal-error" });
    });
    try {
      assert.deepEqual(await usersService.sendPasswordResets(["someone"]), [{ uid: "someone", outcome: "FAILED", reason: "EMAIL_FAILED" }]);
    } finally {
      failing.mock.restore();
      quiet.mock.restore();
    }
  });
});
