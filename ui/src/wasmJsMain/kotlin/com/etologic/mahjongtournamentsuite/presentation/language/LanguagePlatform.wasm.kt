package com.etologic.mahjongtournamentsuite.presentation.language

import kotlinx.browser.window

internal actual object LanguagePreferenceStorage {
    actual fun load(): String? = runCatching { window.localStorage.getItem(KEY) }.getOrNull()

    actual fun save(value: String) {
        runCatching { window.localStorage.setItem(KEY, value) }
    }

    private const val KEY = "languagePreference"
}

internal actual fun applyPlatformLanguage(tag: String) {
    overrideBrowserLanguage(tag)
}

/** The resources read the language from the browser. This sets the language that the app uses. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(tag) => {
        if (!tag) {
            delete navigator.language;
            delete navigator.languages;
            document.documentElement.lang = navigator.language || 'en';
            return;
        }
        Object.defineProperty(navigator, 'language', { get: () => tag, configurable: true });
        Object.defineProperty(navigator, 'languages', { get: () => [tag], configurable: true });
        document.documentElement.lang = tag;
    }""",
)
private external fun overrideBrowserLanguage(tag: String)
