"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAuth = requireAuth;
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
function extractBearerToken(headerValue) {
    if (!headerValue)
        return null;
    const match = headerValue.match(/^Bearer\s+(.+)$/i);
    return match?.[1] ?? null;
}
async function requireAuth(req, res, next) {
    const token = extractBearerToken(req.header("authorization"));
    if (!token) {
        next((0, httpError_1.unauthenticated)("Missing Authorization bearer token"));
        return;
    }
    try {
        const decoded = await firebase_1.auth.verifyIdToken(token);
        res.locals.auth = decoded;
        next();
    }
    catch {
        next((0, httpError_1.unauthenticated)("Invalid or expired token"));
    }
}
//# sourceMappingURL=requireAuth.js.map