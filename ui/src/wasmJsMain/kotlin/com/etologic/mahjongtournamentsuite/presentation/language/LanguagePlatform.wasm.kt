package com.etologic.mahjongtournamentsuite.presentation.language

import kotlinx.browser.window

internal actual object LanguagePreferenceStorage {
    actual fun load(): String? = runCatching { window.localStorage.getItem(KEY) }.getOrNull()

    actual fun save(value: String) {
        runCatching { window.localStorage.setItem(KEY, value) }
    }

    actual fun loadAiNoticeSeen(): Boolean = runCatching { window.localStorage.getItem(AI_NOTICE_KEY) == "true" }.getOrDefault(false)

    actual fun saveAiNoticeSeen() {
        runCatching { window.localStorage.setItem(AI_NOTICE_KEY, "true") }
    }

    private const val KEY = "languagePreference"
    private const val AI_NOTICE_KEY = "aiTranslationNoticeSeen"
}

internal actual fun applyPlatformLanguage(tag: String) {
    rememberBrowserLanguages()
    overrideBrowserLanguage(tag)
}

internal actual fun systemLanguageTags(): List<String> = runCatching { rememberBrowserLanguages().split(',').filter { it.isNotBlank() } }.getOrDefault(emptyList())

/**
 * The languages of the browser, separated by commas. The first call keeps them, before the app overrides
 * `navigator.language`, so the later calls give the languages of the browser and not the one of the app.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        if (window.__mtsBrowserLanguages === undefined) {
            const list = (navigator.languages && navigator.languages.length) ? Array.from(navigator.languages) : [navigator.language || ''];
            window.__mtsBrowserLanguages = list.join(',');
        }
        return window.__mtsBrowserLanguages;
    }""",
)
private external fun rememberBrowserLanguages(): String

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
