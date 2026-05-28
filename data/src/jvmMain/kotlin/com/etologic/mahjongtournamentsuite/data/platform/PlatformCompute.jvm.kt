package com.etologic.mahjongtournamentsuite.data.platform

actual fun platformCpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

