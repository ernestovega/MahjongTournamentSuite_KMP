package com.etologic.mahjongtournamentsuite.data.platform

import platform.UIKit.UIDevice

actual fun providePlatformNameProvider(): PlatformNameProvider = object : PlatformNameProvider {
    override fun platformName(): String = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
}
