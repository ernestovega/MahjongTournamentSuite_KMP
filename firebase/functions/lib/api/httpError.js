"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.HttpError = void 0;
exports.badRequest = badRequest;
exports.unauthenticated = unauthenticated;
exports.forbidden = forbidden;
exports.notFound = notFound;
exports.conflict = conflict;
class HttpError extends Error {
    constructor(status, code, message, details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
exports.HttpError = HttpError;
function badRequest(message, details) {
    return new HttpError(400, "bad_request", message, details);
}
function unauthenticated(message = "Unauthenticated") {
    return new HttpError(401, "unauthenticated", message);
}
function forbidden(message = "Forbidden") {
    return new HttpError(403, "forbidden", message);
}
function notFound(message = "Not found") {
    return new HttpError(404, "not_found", message);
}
function conflict(message) {
    return new HttpError(409, "conflict", message);
}
//# sourceMappingURL=httpError.js.map