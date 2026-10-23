package com.etologic.mahjongtournamentsuite.data.network

import java.util.Locale

internal actual fun currentLanguageTag(): String? = Locale.getDefault().toLanguageTag()
