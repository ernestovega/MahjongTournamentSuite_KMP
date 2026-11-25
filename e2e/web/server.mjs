// Serves a built web app and sends /api to a backend, as Firebase Hosting does in production.
//
//   import { startSite } from "./server.mjs";
//   const site = await startSite({ root: "playerApp/build/dist/wasmJs/productionExecutable", api: "http://127.0.0.1:5055" });
//
// Unknown paths get index.html, like the rewrite of the site, so /t/<id> and /<slug>/my-table open the app.
import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";

const TYPES = {
  ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".wasm": "application/wasm",
  ".json": "application/json", ".png": "image/png", ".svg": "image/svg+xml", ".css": "text/css",
  ".ttf": "font/ttf", ".otf": "font/otf", ".woff2": "font/woff2", ".pdf": "application/pdf", ".xml": "application/xml",
};

async function body(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return Buffer.concat(chunks);
}

export async function startSite({ root, api, port = 0 }) {
  const base = path.resolve(root);
  const server = http.createServer(async (request, response) => {
    try {
      if (request.url.startsWith("/api/")) {
        const headers = Object.fromEntries(
          Object.entries(request.headers).filter(([name]) => !["host", "connection", "content-length"].includes(name)),
        );
        const upstream = await fetch(api + request.url, {
          method: request.method,
          headers,
          body: ["GET", "HEAD"].includes(request.method) ? undefined : await body(request),
        });
        response.writeHead(upstream.status, { "content-type": upstream.headers.get("content-type") ?? "application/json" });
        response.end(Buffer.from(await upstream.arrayBuffer()));
        return;
      }
      let file = decodeURIComponent(request.url.split("?")[0]);
      if (file.endsWith("/")) file += "index.html";
      const resolved = path.join(base, path.normalize(file));
      let data;
      try {
        if (!resolved.startsWith(base)) throw new Error("outside");
        data = await readFile(resolved);
      } catch {
        file = "/index.html";
        data = await readFile(path.join(base, file));
      }
      response.writeHead(200, { "content-type": TYPES[path.extname(file)] ?? "application/octet-stream" });
      response.end(data);
    } catch (error) {
      response.writeHead(502).end(String(error));
    }
  });
  await new Promise((resolve) => server.listen(port, "127.0.0.1", resolve));
  return { url: `http://127.0.0.1:${server.address().port}`, close: () => new Promise((resolve) => server.close(resolve)) };
}
