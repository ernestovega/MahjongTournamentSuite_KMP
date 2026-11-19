import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { CallerCheck, isOwnServiceAccount, registrationsHourHandler, registrationsHourUrl } from "./registrationHourRelay";

const OWN = "123-compute@developer.gserviceaccount.com";

function check(payload: { email?: string; email_verified?: boolean } | Error): CallerCheck {
  return {
    verify: async () => {
      if (payload instanceof Error) throw payload;
      return payload;
    },
    ownEmail: async () => OWN,
  };
}

function fakeResponse() {
  const state: { status?: number; body?: unknown } = {};
  const res = {
    status(code: number) {
      state.status = code;
      return res;
    },
    json(body: unknown) {
      state.body = body;
      return res;
    },
    end() {
      return res;
    },
  };
  return { res, state };
}

describe("registrationHourRelay", () => {
  it("builds the address of the function in Madrid", () => {
    assert.equal(
      registrationsHourUrl("mts---staging"),
      "https://europe-southwest1-mts---staging.cloudfunctions.net/registrationsHourRun",
    );
  });

  it("accepts only a verified token of its own service account", async () => {
    assert.equal(await isOwnServiceAccount("Bearer t", "a", check({ email: OWN, email_verified: true })), true);
    assert.equal(await isOwnServiceAccount("Bearer t", "a", check({ email: "x@y.iam.gserviceaccount.com", email_verified: true })), false);
    assert.equal(await isOwnServiceAccount("Bearer t", "a", check({ email: OWN, email_verified: false })), false);
    assert.equal(await isOwnServiceAccount("Bearer t", "a", check(new Error("bad audience"))), false);
    assert.equal(await isOwnServiceAccount(undefined, "a", check({ email: OWN, email_verified: true })), false);
    assert.equal(await isOwnServiceAccount("Basic t", "a", check({ email: OWN, email_verified: true })), false);
  });

  it("runs the work only for its own service account", async () => {
    let runs = 0;
    const run = async () => {
      runs += 1;
      return [{ tournamentId: "t1" }];
    };
    const denied = fakeResponse();
    await registrationsHourHandler(run, check({ email: "other@x.com", email_verified: true }))(
      { method: "POST", header: () => "Bearer t" } as never,
      denied.res as never,
    );
    assert.equal(denied.state.status, 403);
    assert.equal(runs, 0);

    const allowed = fakeResponse();
    await registrationsHourHandler(run, check({ email: OWN, email_verified: true }))(
      { method: "POST", header: () => "Bearer t" } as never,
      allowed.res as never,
    );
    assert.equal(allowed.state.status, 200);
    assert.deepEqual(allowed.state.body, { results: [{ tournamentId: "t1" }] });
    assert.equal(runs, 1);

    const get = fakeResponse();
    await registrationsHourHandler(run, check({ email: OWN, email_verified: true }))(
      { method: "GET", header: () => "Bearer t" } as never,
      get.res as never,
    );
    assert.equal(get.state.status, 405);
    assert.equal(runs, 1);
  });
});
