import { getApps } from "firebase-admin/app";
import { getRemoteConfig } from "firebase-admin/remote-config";

import { firebaseProjectId } from "../firebase";

export interface FeatureFlags {
  levelsEnabled: boolean;
}

const LEVELS_KEY = "levels_enabled";
const CACHE_MS = 60_000;

const defaults: FeatureFlags = { levelsEnabled: false };

let cached: { flags: FeatureFlags; loadedAt: number } | null = null;

/**
 * Reads the feature flags from the Firebase Remote Config server template.
 * A flag that is missing, or a failed read, gives the default value (feature off).
 */
export async function getFeatureFlags(): Promise<FeatureFlags> {
  if (cached && Date.now() - cached.loadedAt < CACHE_MS) {
    return cached.flags;
  }
  try {
    const template = await getRemoteConfig(getApps()[0]).getServerTemplate({
      defaultConfig: { [LEVELS_KEY]: defaults.levelsEnabled },
    });
    await template.load();
    const flags: FeatureFlags = {
      levelsEnabled: template.evaluate().getBoolean(LEVELS_KEY),
    };
    cached = { flags, loadedAt: Date.now() };
    return flags;
  } catch (error) {
    console.error(`Reading Remote Config failed for ${firebaseProjectId}`, error);
    return cached?.flags ?? defaults;
  }
}
