package com.etologic.mahjongtournamentsuite.data.network

/** The browser language. The app overrides it when the user chooses a language. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun browserLanguage(): String = js("navigator.language || ''")

internal actual fun currentLanguageTag(): String? = runCatching { browserLanguage() }.getOrNull()
