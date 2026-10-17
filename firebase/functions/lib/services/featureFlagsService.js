"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getFeatureFlags = getFeatureFlags;
const app_1 = require("firebase-admin/app");
const remote_config_1 = require("firebase-admin/remote-config");
const firebase_1 = require("../firebase");
const LEVELS_KEY = "levels_enabled";
const CACHE_MS = 60000;
const defaults = { levelsEnabled: false };
let cached = null;
/**
 * Reads the feature flags from the Firebase Remote Config server template.
 * A flag that is missing, or a failed read, gives the default value (feature off).
 */
async function getFeatureFlags() {
    if (cached && Date.now() - cached.loadedAt < CACHE_MS) {
        return cached.flags;
    }
    try {
        const template = await (0, remote_config_1.getRemoteConfig)((0, app_1.getApps)()[0]).getServerTemplate({
            defaultConfig: { [LEVELS_KEY]: defaults.levelsEnabled },
        });
        await template.load();
        const flags = {
            levelsEnabled: template.evaluate().getBoolean(LEVELS_KEY),
        };
        cached = { flags, loadedAt: Date.now() };
        return flags;
    }
    catch (error) {
        console.error(`Reading Remote Config failed for ${firebase_1.firebaseProjectId}`, error);
        return cached?.flags ?? defaults;
    }
}
//# sourceMappingURL=featureFlagsService.js.map