package com.etologic.mahjongtournamentsuite.presentation.platform

import kotlinx.browser.window

/** Stores the texts in the `sessionStorage` of the browser tab. [prefix] keeps the apps apart. */
class BrowserSessionStorage(private val prefix: String) : SessionStateStorage {
    override fun read(name: String): String? = runCatching { window.sessionStorage.getItem("$prefix.$name") }.getOrNull()

    override fun write(name: String, value: String) {
        runCatching { window.sessionStorage.setItem("$prefix.$name", value) }
    }
}
