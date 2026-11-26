package com.etologic.mahjongtournamentsuite.data.network

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** The first preferred language is the one of the app: the app writes its choice there (LanguagePlatform.ios.kt). */
internal actual fun currentLanguageTag(): String? = NSLocale.preferredLanguages.firstOrNull() as? String
