import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { GoogleAuth, OAuth2Client } from "google-auth-library";

import { callRegistrationsHour, CallerCheck, isOwnServiceAccount, registrationsHourHandler, registrationsHourUrl } from "./registrationHourRelay";

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

describe("the Google side of the hourly relay, faked", () => {
  it("calls Madrid with an ID token client, checks a token with Google and reads the own account from the metadata server", async () => {
    const asked: Array<{ url: string; method?: string }> = [];
    const idClient = mock.method(GoogleAuth.prototype, "getIdTokenClient", async () => ({
      request: async (options: { url: string; method?: string }) => {
        asked.push(options);
        return { data: { ok: true } };
      },
    }) as never);
    const payloads = [{ email: "functions@example.test", email_verified: true }];
    const verify = mock.method(OAuth2Client.prototype, "verifyIdToken", async () => ({ getPayload: () => payloads.shift() }) as never);
    const metadata = [new Response(" functions@example.test\n", { status: 200 }), new Response("no", { status: 500 })];
    const fetching = mock.method(globalThis, "fetch", async () => metadata.shift() as Response);
    try {
      assert.deepEqual(await callRegistrationsHour(), { ok: true });
      assert.equal(asked[0].method, "POST");
      assert.match(asked[0].url, /registrationsHourRun$/);
      assert.equal(await isOwnServiceAccount("Bearer token", "audience"), true);
      payloads.push({ email: "functions@example.test", email_verified: true });
      assert.equal(await isOwnServiceAccount("Bearer token", "audience"), false, "the metadata server failed");
    } finally {
      idClient.mock.restore();
      verify.mock.restore();
      fetching.mock.restore();
    }
  });
});
