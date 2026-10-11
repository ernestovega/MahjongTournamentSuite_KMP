"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const dataVersionsService_1 = require("./dataVersionsService");
(0, node_test_1.default)("table version key joins round and table", () => {
    strict_1.default.equal((0, dataVersionsService_1.tableVersionKey)(2, 14), "2_14");
});
(0, node_test_1.default)("table versions ignore missing or malformed data", () => {
    strict_1.default.deepEqual((0, dataVersionsService_1.readTableVersions)(undefined), {});
    strict_1.default.deepEqual((0, dataVersionsService_1.readTableVersions)(null), {});
    strict_1.default.deepEqual((0, dataVersionsService_1.readTableVersions)("text"), {});
    strict_1.default.deepEqual((0, dataVersionsService_1.readTableVersions)({}), {});
});
(0, node_test_1.default)("table versions keep only non-negative whole numbers", () => {
    strict_1.default.deepEqual((0, dataVersionsService_1.readTableVersions)({ "1_1": 3, "1_2": -1, "1_3": 1.5, "1_4": "7", "1_5": 0, "1_6": null }), { "1_1": 3, "1_5": 0 });
});
(0, node_test_1.default)("table version update bumps the coarse revision and sets the exact table version", () => {
    const update = (0, dataVersionsService_1.tableVersionUpdate)(3, 5, 12);
    strict_1.default.equal(update["dataVersions.tableVersions.3_5"], 12);
    strict_1.default.ok("dataVersions.tables.revision" in update);
    strict_1.default.ok("dataVersions.tables.changedAt" in update);
});
//# sourceMappingURL=dataVersionsService.test.js.map