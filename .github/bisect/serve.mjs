// TEMPORARY (bisect of the web slowdown): serves a built player app and sends /api to production.
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize } from "node:path";

const [root, port = "8080", api = "https://mts.mahjongmadrid.com"] = process.argv.slice(2);
const types = { ".html": "text/html", ".js": "application/javascript", ".mjs": "application/javascript", ".wasm": "application/wasm", ".css": "text/css", ".json": "application/json", ".png": "image/png", ".svg": "image/svg+xml", ".ttf": "font/ttf", ".otf": "font/otf", ".webp": "image/webp", ".xml": "application/xml" };

createServer(async (req, res) => {
  try {
    if (req.url.startsWith("/api/") || req.url === "/api") {
      const headers = { accept: req.headers.accept ?? "*/*" };
      if (req.headers["content-type"]) headers["content-type"] = req.headers["content-type"];
      if (req.headers["accept-language"]) headers["accept-language"] = req.headers["accept-language"];
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const upstream = await fetch(api + req.url, { method: req.method, headers, body: chunks.length ? Buffer.concat(chunks) : undefined });
      const body = Buffer.from(await upstream.arrayBuffer());
      res.writeHead(upstream.status, { "content-type": upstream.headers.get("content-type") ?? "application/json", "cache-control": "no-store" });
      res.end(body);
      return;
    }
    let path = normalize(decodeURIComponent(req.url.split("?")[0])).replace(/^(\.\.[/\\])+/, "");
    let file = join(root, path);
    const info = await stat(file).catch(() => null);
    if (info?.isDirectory()) file = join(file, "index.html");
    else if (!info) file = join(root, "index.html");
    const data = await readFile(file);
    res.writeHead(200, { "content-type": types[extname(file)] ?? "application/octet-stream", "cache-control": file.endsWith(".wasm") ? "max-age=31536000" : "no-cache" });
    res.end(data);
  } catch (error) {
    res.writeHead(500); res.end(String(error));
  }
}).listen(Number(port), () => console.log(`serving ${root} on ${port}, api ${api}`));
