import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { describe, it } from "node:test";

import { excelJs, jsZip, nodemailer, pdfKit, sharp } from "./lazyModules";

const HEAVY_MODULES = ["exceljs", "jszip", "sharp", "pdfkit", "nodemailer"];

describe("heavy libraries", () => {
  it("each loader gives the working library", async () => {
    assert.equal(typeof new (excelJs().Workbook)().addWorksheet, "function");
    assert.equal(typeof jsZip().loadAsync, "function");
    assert.equal(typeof nodemailer().createTransport, "function");
    const png = await sharp()(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="4" height="3"/>')).png().toBuffer();
    assert.equal(png.subarray(1, 4).toString("ascii"), "PNG");
    const doc = new (pdfKit())({ autoFirstPage: false });
    assert.equal(typeof doc.addPage, "function");
    doc.end();
  });

  it("are not loaded when the API starts", () => {
    // A new process, so the libraries that the other tests load do not count.
    const script = `
      require(${JSON.stringify(resolve(__dirname, "../index.js"))});
      const loaded = Object.keys(require.cache);
      const heavy = ${JSON.stringify(HEAVY_MODULES)}.filter((name) =>
        loaded.some((path) => path.includes("/node_modules/" + name + "/")));
      process.stdout.write(JSON.stringify(heavy));
    `;
    const result = spawnSync(process.execPath, ["-e", script], {
      encoding: "utf8",
      env: { ...process.env, GCLOUD_PROJECT: process.env.GCLOUD_PROJECT ?? "demo-mts-tests" },
    });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), []);
  });
});

describe("web hosting cache headers", () => {
  const config = JSON.parse(readFileSync(join(__dirname, "../../../../firebase.json"), "utf8")) as {
    hosting: Array<{ site: string; headers: Array<{ source: string; headers: Array<{ key: string; value: string }> }> }>;
  };

  for (const site of config.hosting) {
    it(`${site.site} asks the server for every file, so a deploy is seen at once without downloading the app again`, () => {
      const cacheControl = (source: string) => site.headers
        .find((entry) => entry.source === source)?.headers
        .find((header) => header.key === "Cache-Control")?.value;
      for (const source of ["**/*.js", "**/*.css", "/index.html", "/"]) {
        const value = cacheControl(source);
        assert.ok(value != null, `${source} has no Cache-Control`);
        assert.ok(value.includes("no-cache"), `${source} must be revalidated: ${value}`);
      }
      for (const source of ["**/*.js", "**/*.css"]) {
        assert.ok(!cacheControl(source)?.includes("no-store"), `${source} must allow a 304 answer`);
      }
    });

    // The name of every .wasm file is the hash of its content (scripts/assemble-web-hosting.sh checks it), and the
    // revalidated .js file names the .wasm files of its build. A deploy is still seen at once, and a returning
    // browser does not download or even ask for megabytes it already has.
    it(`${site.site} lets the browser keep the .wasm files, whose names change with every build`, () => {
      const value = site.headers
        .find((entry) => entry.source === "**/*.wasm")?.headers
        .find((header) => header.key === "Cache-Control")?.value;
      assert.equal(value, "public, max-age=31536000, immutable");
    });
  }
});
