import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { HttpError } from "./httpError";
import { cursorQuery, limitQuery, MAX_PAGE_SIZE, numberQuery } from "./pageQuery";

const badRequest = (error: unknown) => error instanceof HttpError && error.status === 400;

describe("page query", () => {
  it("uses the fallback when the page size is missing or not a number", () => {
    assert.equal(limitQuery(undefined, 50), 50);
    assert.equal(limitQuery("abc", 50), 50);
    assert.equal(limitQuery("0", 50), 50);
    assert.equal(limitQuery("", 50), 50);
  });

  it("keeps the page size between 1 and the maximum", () => {
    assert.equal(limitQuery("10", 50), 10);
    assert.equal(limitQuery("-5", 50), 1);
    assert.equal(limitQuery("999", 50), MAX_PAGE_SIZE);
    assert.equal(limitQuery("2.9", 50), 2);
  });

  it("reads a number that is not negative and refuses the rest", () => {
    assert.equal(numberQuery("0", "since"), 0);
    assert.equal(numberQuery("1700000000000", "since"), 1_700_000_000_000);
    for (const value of ["abc", "-1", "", "Infinity", "NaN"]) {
      assert.throws(() => numberQuery(value, "since"), badRequest, value);
    }
  });

  it("starts at the first page when the request has no cursor", () => {
    assert.equal(cursorQuery({}), undefined);
    assert.equal(cursorQuery({ limit: "5" }), undefined);
  });

  it("reads a cursor that has both parts", () => {
    assert.deepEqual(cursorQuery({ cursorAt: "4000", cursorId: "g1" }), { at: 4000, id: "g1" });
  });

  it("refuses a cursor that has only one part or a bad part", () => {
    assert.throws(() => cursorQuery({ cursorAt: "4000" }), badRequest);
    assert.throws(() => cursorQuery({ cursorId: "g1" }), badRequest);
    assert.throws(() => cursorQuery({ cursorAt: "4000", cursorId: "" }), badRequest);
    assert.throws(() => cursorQuery({ cursorAt: "x", cursorId: "g1" }), badRequest);
    assert.throws(() => cursorQuery({ cursorAt: ["1", "2"], cursorId: "g1" }), badRequest);
  });
});
