"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildApp = buildApp;
const express_1 = __importDefault(require("express"));
const errorHandler_1 = require("./middleware/errorHandler");
const admin_routes_1 = require("./routes/admin.routes");
const auth_routes_1 = require("./routes/auth.routes");
const tournaments_routes_1 = require("./routes/tournaments.routes");
function buildApp() {
    const app = (0, express_1.default)();
    app.use(express_1.default.json({ limit: "1mb" }));
    app.get("/health", (_req, res) => {
        res.status(200).json({ ok: true });
    });
    app.get("/version", (_req, res) => {
        res.status(200).json({
            ok: true,
            project: process.env.GCLOUD_PROJECT ?? null,
            service: process.env.K_SERVICE ?? null,
            revision: process.env.K_REVISION ?? null,
            target: process.env.FUNCTION_TARGET ?? null,
            node: process.version,
        });
    });
    app.use("/auth", (0, auth_routes_1.authRouter)());
    app.use("/admin", (0, admin_routes_1.adminRouter)());
    app.use("/tournaments", (0, tournaments_routes_1.tournamentsRouter)());
    app.use(errorHandler_1.errorHandler);
    return app;
}
//# sourceMappingURL=app.js.map