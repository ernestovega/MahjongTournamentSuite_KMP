"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.errorHandler = errorHandler;
const httpError_1 = require("../httpError");
function errorHandler(err, _req, res, _next) {
    if (err instanceof httpError_1.HttpError) {
        res.status(err.status).json({
            error: err.code,
            message: err.message,
            details: err.details ?? null,
        });
        return;
    }
    console.error(err);
    res.status(500).json({
        error: "internal",
        message: "Internal server error",
    });
}
//# sourceMappingURL=errorHandler.js.map