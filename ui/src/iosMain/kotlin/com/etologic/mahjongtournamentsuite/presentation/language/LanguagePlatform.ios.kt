package com.etologic.mahjongtournamentsuite.presentation.language

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

internal actual object LanguagePreferenceStorage {
    actual fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey(KEY)

    actual fun save(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, KEY)
    }

    actual fun loadAiNoticeSeen(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(AI_NOTICE_KEY)

    actual fun saveAiNoticeSeen() {
        NSUserDefaults.standardUserDefaults.setBool(true, AI_NOTICE_KEY)
    }

    private const val KEY = "languagePreference"
    private const val AI_NOTICE_KEY = "aiTranslationNoticeSeen"
}

/**
 * The resources of Compose read the language from the preferred languages of the app, which come from
 * `AppleLanguages` in the defaults of the app. Writing the choice there changes the texts at once, and
 * [ProvideAppLanguage] draws the screens again.
 */
internal actual fun applyPlatformLanguage(tag: String) {
    IosLanguage.rememberSystemLanguages()
    IosLanguage.current = tag
    NSUserDefaults.standardUserDefaults.setObject(listOf(tag), APPLE_LANGUAGES)
}

internal actual fun systemLanguageTags(): List<String> = IosLanguage.rememberSystemLanguages()

/** The language of the app, for the dates. */
object IosLanguage {
    var current: String? = null
        internal set

    private var system: List<String>? = null

    /**
     * The languages of the device. The app keeps its choice in its own `AppleLanguages`, which hides the ones of the
     * device, so the first call removes it for a moment to read them.
     */
    internal fun rememberSystemLanguages(): List<String> {
        system?.let { return it }
        val defaults = NSUserDefaults.standardUserDefaults
        val own = defaults.objectForKey(APPLE_LANGUAGES)
        defaults.removeObjectForKey(APPLE_LANGUAGES)
        val tags = NSLocale.preferredLanguages.mapNotNull { it as? String }
        if (own != null) defaults.setObject(own, APPLE_LANGUAGES)
        system = tags
        return tags
    }
}

private const val APPLE_LANGUAGES = "AppleLanguages"
