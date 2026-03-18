package com.etologic.mahjongtournamentsuite.data.platform

actual fun providePlatformNameProvider(): PlatformNameProvider = object : PlatformNameProvider {
    override fun platformName(): String = "Web with Kotlin/Wasm"
}
