"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.rateLimit = rateLimit;
const httpError_1 = require("../httpError");
const MAX_TRACKED_CLIENTS = 5000;
/**
 * Limits the requests of one client address in a time window. The counters live in the memory of one function
 * instance, so this is a brake against bulk use and not an exact limit.
 */
function rateLimit(options) {
    const now = options.now ?? Date.now;
    const windows = new Map();
    return (req, _res, next) => {
        const time = now();
        const key = req.ip ?? "unknown";
        let entry = windows.get(key);
        if (!entry || entry.resetAt <= time) {
            if (windows.size >= MAX_TRACKED_CLIENTS)
                windows.clear();
            entry = { resetAt: time + options.windowMs, count: 0 };
            windows.set(key, entry);
        }
        entry.count += 1;
        if (entry.count > options.max) {
            next(new httpError_1.HttpError(429, "too_many_requests", "Too many requests. Try again later."));
            return;
        }
        next();
    };
}
//# sourceMappingURL=rateLimit.js.map