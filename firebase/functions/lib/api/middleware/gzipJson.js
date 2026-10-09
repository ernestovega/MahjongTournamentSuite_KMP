"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.gzipJson = gzipJson;
const node_zlib_1 = require("node:zlib");
const node_util_1 = require("node:util");
const gzipAsync = (0, node_util_1.promisify)(node_zlib_1.gzip);
// Small bodies cost more to compress than to send.
const MIN_COMPRESS_BYTES = 1024;
/** Compresses JSON responses with gzip when the client accepts it. Express has no built-in compression. */
function gzipJson(req, res, next) {
    const acceptsGzip = /\bgzip\b/i.test(req.header("accept-encoding") ?? "");
    if (!acceptsGzip) {
        next();
        return;
    }
    const send = res.json.bind(res);
    res.json = (body) => {
        const text = JSON.stringify(body);
        if (text === undefined || Buffer.byteLength(text) < MIN_COMPRESS_BYTES || res.headersSent) {
            return send(body);
        }
        gzipAsync(text).then((compressed) => {
            res.vary("Accept-Encoding");
            res.set("Content-Encoding", "gzip");
            res.type("application/json");
            res.send(compressed);
        }, () => send(body));
        return res;
    };
    next();
}
//# sourceMappingURL=gzipJson.js.map