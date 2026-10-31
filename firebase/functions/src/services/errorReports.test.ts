import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { fingerprint, parseErrorReport, redact } from "./errorReports";

const valid = { app: "admin", platform: "android", kind: "handled", errorType: "IllegalStateException" };

describe("error reports", () => {
  it("removes e-mail addresses, long numbers and tokens", () => {
    const clean = redact("user a.b@example.com has EMA 12345678 token Bearer abc.def-ghi and eyJhbGciOiJIUzI1.eyJzdWIiOiIxMjM0.c2lnbmF0dXJl");
    assert.ok(!clean.includes("example.com"));
    assert.ok(!clean.includes("12345678"));
    assert.ok(!clean.includes("abc.def-ghi"));
    assert.ok(!clean.includes("eyJhbGci"));
  });

  it("keeps only known values and cuts long text", () => {
    const report = parseErrorReport({ ...valid, message: "x".repeat(2000), env: "bad", role: "root", httpStatus: 99, extra: "no" });
    assert.equal(report.message.length, 500);
    assert.equal(report.env, "unknown");
    assert.equal(report.role, null);
    assert.equal(report.httpStatus, null);
    assert.equal("extra" in report, false);
  });

  it("keeps the report id that joins the log with Analytics", () => {
    assert.equal(parseErrorReport({ ...valid, reportId: "r-123" }).reportId, "r-123");
  });

  it("refuses a body with an unknown app, platform or kind", () => {
    assert.throws(() => parseErrorReport({ ...valid, app: "other" }));
    assert.throws(() => parseErrorReport({ ...valid, platform: "ios" }));
    assert.throws(() => parseErrorReport({ ...valid, kind: "info" }));
    assert.throws(() => parseErrorReport({ ...valid, errorType: "" }));
    assert.throws(() => parseErrorReport(null));
  });

  it("gives equal errors the same group even when line numbers differ", () => {
    const a = fingerprint({ errorType: "E", area: "Sign in failed.", stackTrace: "at A.run(A.kt:10)\nat B.go(B.kt:22)" });
    const b = fingerprint({ errorType: "E", area: "Sign in failed.", stackTrace: "at A.run(A.kt:99)\nat B.go(B.kt:5)" });
    const c = fingerprint({ errorType: "E", area: "Sign in failed.", stackTrace: "at Other.run(O.kt:1)" });
    assert.equal(a, b);
    assert.notEqual(a, c);
  });
});
