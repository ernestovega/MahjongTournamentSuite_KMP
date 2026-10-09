"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAuth = requireAuth;
const firebase_1 = require("../../firebase");
const httpError_1 = require("../httpError");
// The revocation check calls Firebase Auth, so a successful check is reused for this long per user.
const REVOCATION_CHECK_TTL_MS = 30000;
const MAX_CACHED_USERS = 1000;
const revocationCheckedUntil = new Map();
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
        // Check revocation so disabled users lose API access. The check is cached for a short time per user.
        let decoded = await firebase_1.auth.verifyIdToken(token, false);
        const now = Date.now();
        if ((revocationCheckedUntil.get(decoded.uid) ?? 0) <= now) {
            decoded = await firebase_1.auth.verifyIdToken(token, true);
            if (revocationCheckedUntil.size >= MAX_CACHED_USERS)
                revocationCheckedUntil.clear();
            revocationCheckedUntil.set(decoded.uid, now + REVOCATION_CHECK_TTL_MS);
        }
        res.locals.auth = decoded;
        next();
    }
    catch {
        next((0, httpError_1.unauthenticated)("Invalid or expired token"));
    }
}
//# sourceMappingURL=requireAuth.js.map