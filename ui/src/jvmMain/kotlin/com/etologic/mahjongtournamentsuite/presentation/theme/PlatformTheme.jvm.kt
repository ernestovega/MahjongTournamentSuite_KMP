package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal actual fun isPlatformDarkTheme(): Boolean {
    if (!isMacOs()) return isSystemInDarkTheme()

    var isDark by remember { mutableStateOf(readMacOsDarkMode()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(APPEARANCE_POLL_INTERVAL_MILLIS)
            val current = withContext(Dispatchers.IO) { readMacOsDarkMode() }
            if (current != isDark) isDark = current
        }
    }
    return isDark
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

private fun readMacOsDarkMode(): Boolean = runCatching {
    val process = ProcessBuilder(
        "/usr/bin/defaults",
        "read",
        "-g",
        "AppleInterfaceStyle",
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor() == 0 && output.trim().equals("Dark", ignoreCase = true)
}.getOrDefault(false)

private const val APPEARANCE_POLL_INTERVAL_MILLIS = 1_000L
