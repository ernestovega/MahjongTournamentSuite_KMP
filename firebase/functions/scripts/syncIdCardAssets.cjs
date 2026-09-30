const { cpSync, mkdirSync } = require("node:fs");
const { dirname, resolve } = require("node:path");

const source = resolve(__dirname, "../../../composeApp/src/commonMain/composeResources/font/go3v2.ttf");
const target = resolve(__dirname, "../lib/assets/go3v2.ttf");

mkdirSync(dirname(target), { recursive: true });
cpSync(source, target);
