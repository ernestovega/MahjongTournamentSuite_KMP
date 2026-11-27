import assert from "node:assert/strict";
import test from "node:test";
import type { Request } from "express";

import { pathParam } from "./pathParam";

test("reads a path parameter as text, joins a wildcard and gives empty text for a missing one", () => {
  const req = (params: Record<string, unknown>) => ({ params }) as unknown as Request;
  assert.equal(pathParam(req({ id: "abc" }), "id"), "abc");
  assert.equal(pathParam(req({ path: ["a", "b"] }), "path"), "a/b");
  assert.equal(pathParam(req({}), "id"), "");
});
