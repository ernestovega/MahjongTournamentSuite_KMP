import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { HttpError } from "../httpError";
import { signInError } from "./auth.routes";

describe("signInError", () => {
  it("answers 401 for a wrong password or an unknown email", () => {
    for (const code of ["INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND"]) {
      const error = signInError(new Error(code)) as HttpError;
      assert.equal(error.status, 401);
      assert.equal(error.message, "INVALID_LOGIN_CREDENTIALS");
    }
  });

  it("answers 400 for an email that is not valid", () => {
    const error = signInError(new Error("INVALID_EMAIL")) as HttpError;
    assert.equal(error.status, 400);
  });

  it("answers 403 for a disabled account, with the words that the apps look for", () => {
    const error = signInError(new Error("USER_DISABLED")) as HttpError;
    assert.equal(error.status, 403);
    assert.match(error.message, /account is disabled/);
  });

  it("answers 429 when Firebase blocks too many attempts", () => {
    const error = signInError(new Error("TOO_MANY_ATTEMPTS_TRY_LATER : Access to this account has been temporarily disabled")) as HttpError;
    assert.equal(error.status, 429);
  });

  it("keeps other errors unchanged", () => {
    const original = new Error("socket hang up");
    assert.equal(signInError(original), original);
  });
});
