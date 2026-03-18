package com.etologic.mahjongtournamentsuite.data.platform

interface PlatformNameProvider {
    fun platformName(): String
}

expect fun providePlatformNameProvider(): PlatformNameProvider
