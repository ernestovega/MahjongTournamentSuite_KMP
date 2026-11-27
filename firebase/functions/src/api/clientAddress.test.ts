import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { Request } from "express";

import { clientAddress, isGoogleProxy } from "./clientAddress";

const request = (forwardedFor: string | undefined, remote = "127.0.0.1") =>
  ({ header: (name: string) => (name.toLowerCase() === "x-forwarded-for" ? forwardedFor : undefined), socket: { remoteAddress: remote } }) as unknown as Request;

// The headers below are the ones staging received on 2026-11-24 from a runner at 13.71.226.146.
describe("clientAddress", () => {
  it("takes the address that Google adds on a direct call", () => {
    assert.equal(clientAddress(request("13.71.226.146")), "13.71.226.146");
  });

  it("ignores the addresses that a direct caller forges", () => {
    assert.equal(clientAddress(request("9.9.9.9, 8.8.8.8,13.71.226.146")), "13.71.226.146");
  });

  it("takes the client before the Google proxy of Firebase Hosting", () => {
    assert.equal(clientAddress(request("13.71.226.146,64.233.172.44")), "13.71.226.146");
    assert.equal(clientAddress(request("13.71.226.146,66.102.6.33")), "13.71.226.146");
    assert.equal(clientAddress(request("172.206.119.228,192.178.11.1")), "172.206.119.228");
  });

  it("uses the socket without the header (tests and the emulator)", () => {
    assert.equal(clientAddress(request(undefined, "10.0.0.7")), "10.0.0.7");
  });

  it("knows the ranges of the Google proxies and nothing else", () => {
    assert.equal(isGoogleProxy("64.233.172.44"), true);
    assert.equal(isGoogleProxy("66.102.6.33"), true);
    assert.equal(isGoogleProxy("192.178.11.4"), true);
    assert.equal(isGoogleProxy("193.0.0.1"), false);
    assert.equal(isGoogleProxy("13.71.226.146"), false);
    assert.equal(isGoogleProxy("8.8.8.8"), false);
    assert.equal(isGoogleProxy("not an address"), false);
  });
});

describe("addresses that are no IPv4 address", () => {
  it("are no Google proxy", () => {
    assert.equal(isGoogleProxy("64.233.a.1"), false);
    assert.equal(isGoogleProxy("64.233.300.1"), false);
    assert.equal(isGoogleProxy("64.233.172"), false);
  });

  it("gives unknown for a request without the header and without a socket", () => {
    assert.equal(clientAddress({ header: () => undefined } as unknown as Request), "unknown");
  });
});
