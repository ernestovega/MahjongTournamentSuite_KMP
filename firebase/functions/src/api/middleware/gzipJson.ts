import { gzip } from "node:zlib";
import { promisify } from "node:util";
import type { NextFunction, Request, Response } from "express";

const gzipAsync = promisify(gzip);
// Small bodies cost more to compress than to send.
const MIN_COMPRESS_BYTES = 1024;

/** Compresses JSON responses with gzip when the client accepts it. Express has no built-in compression. */
export function gzipJson(req: Request, res: Response, next: NextFunction): void {
  const acceptsGzip = /\bgzip\b/i.test(req.header("accept-encoding") ?? "");
  if (!acceptsGzip) {
    next();
    return;
  }
  const send = res.json.bind(res);
  res.json = (body: unknown): Response => {
    const text = JSON.stringify(body);
    if (text === undefined || Buffer.byteLength(text) < MIN_COMPRESS_BYTES || res.headersSent) {
      return send(body);
    }
    gzipAsync(text).then(
      (compressed) => {
        res.vary("Accept-Encoding");
        res.set("Content-Encoding", "gzip");
        res.type("application/json");
        res.send(compressed);
      },
      () => send(body),
    );
    return res;
  };
  next();
}
