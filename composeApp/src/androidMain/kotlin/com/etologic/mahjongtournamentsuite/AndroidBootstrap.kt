package com.etologic.mahjongtournamentsuite

import android.content.Context
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

fun initAndroidApp(context: Context) {
    AndroidPlatformContext.init(context)
}

