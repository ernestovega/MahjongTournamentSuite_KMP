package com.etologic.mahjongtournamentsuite.data.platform

import android.content.Context

object AndroidPlatformContext {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        appContext ?: error("AndroidPlatformContext not initialized. Call AndroidPlatformContext.init(context) in androidApp.")
}

