package com.etologic.mahjongtournamentsuite.data.platform

actual fun providePlatformNameProvider(): PlatformNameProvider = object : PlatformNameProvider {
    override fun platformName(): String = "Java ${System.getProperty("java.version")}"
}
