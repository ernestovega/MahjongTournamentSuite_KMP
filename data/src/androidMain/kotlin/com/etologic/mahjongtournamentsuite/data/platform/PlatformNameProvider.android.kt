package com.etologic.mahjongtournamentsuite.data.platform

import android.os.Build

actual fun providePlatformNameProvider(): PlatformNameProvider = object : PlatformNameProvider {
    override fun platformName(): String = "Android ${Build.VERSION.SDK_INT}"
}
