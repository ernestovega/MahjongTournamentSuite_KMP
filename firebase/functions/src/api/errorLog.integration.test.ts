import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";

import { db } from "../firebase";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { ERROR_GROUPS_COLLECTION, recordErrorReport, type ErrorReport } from "../services/errorReports";

type Group = { id: string; errorType: string; message: string; lastSeen: number; count: number; platforms: Record<string, number>; apps: Record<string, number>; lastEnv: string; lastKind: string };
type Page = { groups: Group[]; nextCursor: { at: number; id: string } | null };
type Detail = {
  id: string;
  count: number;
  stackTrace: string;
  expireAt?: unknown;
  reports: Array<{ reportId: string; createdAt: number; expireAt?: unknown }>;
  nextReportCursor: { at: number; id: string } | null;
};

/** A time far in the future, so these groups are the only ones that the tests read with `since`. */
const BASE = 4_000_000_000_000;

const report = (overrides: Partial<ErrorReport> = {}): ErrorReport => ({
  app: "admin", platform: "android", kind: "handled", env: "prod", role: "admin", appVersion: "1.0.0",
  errorType: "TestFailure", area: "Area", message: "boom", stackTrace: "at A.run(A.kt:1)", reason: "server_error",
  httpStatus: 500, language: "en", reportId: "r", sessionId: "s", ...overrides,
});

describe("error log API", () => {
  let server: TestServer;
  let admin: TestAccount;
  let editor: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
    editor = await createAccount({ role: "EDITOR" });
  });
  after(() => server.close());
  beforeEach(() => db.recursiveDelete(db.collection(ERROR_GROUPS_COLLECTION)));

  const list = async (query = "", token: string | null = admin.token) => {
    const response = await server.request("GET", `/admin/error-groups?since=${BASE}${query}`, { token });
    return { status: response.status, page: response.body as Page };
  };

  /** Reads every page with the cursor of the page before. */
  async function readAll(limit: number, since = BASE) {
    const pages: Group[][] = [];
    let cursor = "";
    for (let guard = 0; guard < 20; guard += 1) {
      const response = await server.request("GET", `/admin/error-groups?limit=${limit}&since=${since}${cursor}`, { token: admin.token });
      assert.equal(response.status, 200);
      const page = response.body as Page;
      pages.push(page.groups);
      if (!page.nextCursor) return pages;
      cursor = `&cursorAt=${page.nextCursor.at}&cursorId=${encodeURIComponent(page.nextCursor.id)}`;
    }
    throw new Error("The cursor never ended");
  }

  /** One group for each error type, seen at BASE + offset. */
  async function seedGroups(offsets: number[]) {
    for (const [index, offset] of offsets.entries()) {
      await recordErrorReport(report({ errorType: `Failure${index}`, reportId: `r${index}` }), BASE + offset);
    }
  }

  it("reads the groups one page at a time, newest first, with no group lost or repeated", async () => {
    await seedGroups([1000, 2000, 3000, 4000, 5000]);

    const pages = await readAll(2);

    assert.deepEqual(pages.map((page) => page.length), [2, 2, 1]);
    const all = pages.flat();
    assert.deepEqual(all.map((group) => group.lastSeen), [5000, 4000, 3000, 2000, 1000].map((offset) => BASE + offset));
    assert.equal(new Set(all.map((group) => group.id)).size, 5);
  });

  it("ends with no empty page when the groups fill the last page exactly", async () => {
    await seedGroups([1000, 2000, 3000, 4000]);

    const pages = await readAll(2);

    assert.deepEqual(pages.map((page) => page.length), [2, 2]);
  });

  it("does not lose groups that were seen at the same time", async () => {
    await seedGroups([1000, 1000, 1000, 1000, 1000]);

    const pages = await readAll(1);

    assert.equal(pages.length, 5);
    assert.equal(new Set(pages.flat().map((group) => group.id)).size, 5);
  });

  it("keeps only the groups seen since the given time", async () => {
    await seedGroups([1000, 2000, 3000, 4000, 5000]);

    const pages = await readAll(2, BASE + 3000);

    assert.deepEqual(pages.flat().map((group) => group.lastSeen), [BASE + 5000, BASE + 4000, BASE + 3000]);
  });

  it("answers an empty list when no group matches", async () => {
    await seedGroups([1000]);

    const { status, page } = await list(`&limit=5`);
    assert.equal(status, 200);
    assert.equal(page.groups.length, 1);

    const none = await server.request("GET", `/admin/error-groups?since=${BASE + 999_999}`, { token: admin.token });
    assert.deepEqual(none.body, { groups: [], nextCursor: null });
  });

  it("counts equal errors in one group, with the totals for each platform and app", async () => {
    await recordErrorReport(report({ platform: "android", app: "admin" }), BASE + 1000);
    await recordErrorReport(report({ platform: "web", app: "player", kind: "crash", env: "dev" }), BASE + 2000);
    await recordErrorReport(report({ platform: "web", app: "player" }), BASE + 3000);

    const { page } = await list();

    assert.equal(page.groups.length, 1);
    const group = page.groups[0];
    assert.equal(group.count, 3);
    assert.deepEqual(group.platforms, { android: 1, web: 2 });
    assert.deepEqual(group.apps, { admin: 1, player: 2 });
    assert.equal(group.lastSeen, BASE + 3000);
    assert.equal(group.lastEnv, "prod");
    assert.equal(group.lastKind, "handled");
  });

  it("refuses a bad period or a cursor with one part", async () => {
    for (const query of ["since=abc", "since=-1", `since=${BASE}&cursorAt=5`, `since=${BASE}&cursorId=g`, `since=${BASE}&cursorAt=x&cursorId=g`]) {
      const response = await server.request("GET", `/admin/error-groups?${query}`, { token: admin.token });
      assert.equal(response.status, 400, query);
    }
  });

  it("is only for administrators", async () => {
    assert.equal((await list("", editor.token)).status, 403);
    assert.equal((await list("", null)).status, 401);
    const detail = await server.request("GET", "/admin/error-groups/anything", { token: editor.token });
    assert.equal(detail.status, 403);
  });

  it("gives the stack trace and the reports of a group, a page at a time, newest first", async () => {
    for (let index = 1; index <= 5; index += 1) {
      await recordErrorReport(report({ reportId: `r${index}` }), BASE + index * 1000);
    }
    const id = (await list()).page.groups[0].id;

    const seen: string[] = [];
    let cursor = "";
    let pages = 0;
    for (; pages < 10; pages += 1) {
      const response = await server.request("GET", `/admin/error-groups/${id}?limit=2${cursor}`, { token: admin.token });
      assert.equal(response.status, 200);
      const detail = response.body as Detail;
      assert.equal(detail.count, 5);
      assert.equal(detail.stackTrace, "at A.run(A.kt:1)");
      assert.equal("expireAt" in detail, false);
      detail.reports.forEach((item) => assert.equal("expireAt" in item, false));
      seen.push(...detail.reports.map((item) => item.reportId));
      if (!detail.nextReportCursor) break;
      cursor = `&cursorAt=${detail.nextReportCursor.at}&cursorId=${encodeURIComponent(detail.nextReportCursor.id)}`;
    }

    assert.equal(pages + 1, 3);
    assert.deepEqual(seen, ["r5", "r4", "r3", "r2", "r1"]);
  });

  it("answers 404 for a group that does not exist", async () => {
    const response = await server.request("GET", "/admin/error-groups/missing", { token: admin.token });
    assert.equal(response.status, 404);
  });

  it("takes a report from an app without a sign-in and shows it in the list with the text cleaned", async () => {
    const sent = await server.request("POST", "/telemetry/errors", {
      body: {
        app: "player", platform: "web", kind: "crash", env: "prod", errorType: "PublicFailure", area: "Start",
        message: "user ann@example.com failed with EMA 12345678", stackTrace: "at X.go(X.kt:1)", reportId: "public-1",
      },
    });
    assert.equal(sent.status, 204);

    const response = await server.request("GET", "/admin/error-groups?since=0", { token: admin.token });
    const group = (response.body as Page).groups.find((item) => item.errorType === "PublicFailure");
    assert.ok(group, "the report is in the list");
    assert.ok(!group.message.includes("example.com") && !group.message.includes("12345678"), group.message);
  });
});
