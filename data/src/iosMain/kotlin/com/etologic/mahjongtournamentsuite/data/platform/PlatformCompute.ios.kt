package com.etologic.mahjongtournamentsuite.data.platform

import platform.Foundation.NSProcessInfo

actual fun platformCpuCount(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceAtLeast(1)
