"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const httpError_1 = require("../httpError");
const rateLimit_1 = require("./rateLimit");
function call(limit, ip) {
    let result = "unset";
    limit({ ip }, {}, (error) => { result = error; });
    return result;
}
(0, node_test_1.describe)("rateLimit", () => {
    (0, node_test_1.it)("lets requests pass until the maximum and then answers 429", () => {
        const limit = (0, rateLimit_1.rateLimit)({ windowMs: 1000, max: 2, now: () => 0 });
        strict_1.default.equal(call(limit, "1.1.1.1"), undefined);
        strict_1.default.equal(call(limit, "1.1.1.1"), undefined);
        const blocked = call(limit, "1.1.1.1");
        strict_1.default.ok(blocked instanceof httpError_1.HttpError && blocked.status === 429);
    });
    (0, node_test_1.it)("counts each address on its own and starts a new window later", () => {
        let time = 0;
        const limit = (0, rateLimit_1.rateLimit)({ windowMs: 1000, max: 1, now: () => time });
        strict_1.default.equal(call(limit, "1.1.1.1"), undefined);
        strict_1.default.equal(call(limit, "2.2.2.2"), undefined);
        strict_1.default.ok(call(limit, "1.1.1.1") instanceof httpError_1.HttpError);
        time = 1001;
        strict_1.default.equal(call(limit, "1.1.1.1"), undefined);
    });
});
//# sourceMappingURL=rateLimit.test.js.map