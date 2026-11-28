package com.etologic.mahjongtournamentsuite.e2e

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.etologic.mahjongtournamentsuite.data.platform.IosFiles
import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURLCache
import platform.Foundation.NSUserDefaults
import platform.Foundation.localTimeZone
import platform.posix.getenv
import platform.posix.usleep

/**
 * What the end-to-end tests of src/e2eTest need from iOS: the address of the local test backend, a clean app, a saved
 * session and the clock of the device. The workflow Build · iOS gives the address in `SIMCTL_CHILD_E2E_BACKEND_URL`:
 * the simulator passes it to the test process without the prefix. The simulator reaches the Mac at 127.0.0.1.
 */
@OptIn(ExperimentalForeignApi::class)
object E2ePlatform {
    /** Null when no backend runs, as in the normal test run: then the end-to-end tests do nothing. */
    val backendUrl: String? = getenv("E2E_BACKEND_URL")?.toKString()?.ifBlank { null }

    val waitMs: Long = getenv("E2E_WAIT_MS")?.toKString()?.toLongOrNull() ?: 30_000L

    /** The screen of the test: an iPad Pro 13" on its side (1376 x 1032 dp) or an iPhone 17 Pro (402 x 874 dp). */
    class Screen(val size: Size, val density: Density)

    fun screen(tablet: Boolean): Screen {
        val form = getenv("E2E_FORM")?.toKString()
        return if (form == "tablet" || (form != "phone" && tablet)) {
            Screen(Size(2752f, 2064f), Density(2f))
        } else {
            Screen(Size(1206f, 2622f), Density(3f))
        }
    }

    private val defaults get() = NSUserDefaults.standardUserDefaults

    /**
     * A fresh app, pointed at the local backend. All the tests run in one process, unlike Android, where each test gets a
     * fresh app (clearPackageData). So the files of the app (the session, the saved tables of the player), its settings
     * and the answers that the backend lets clients keep for a few seconds (Cache-Control) go, or they would reach the
     * next test after its fresh seed: a table with the hand that the last test saved, for example.
     */
    fun resetAppState() {
        IosPlatform.backendUrlOverride = backendUrl
        listOf(IosFiles.supportDirectory(), IosFiles.cachesDirectory()).forEach { folder ->
            NSFileManager.defaultManager.contentsOfDirectoryAtPath(folder, error = null)?.forEach { name ->
                NSFileManager.defaultManager.removeItemAtPath(IosFiles.child(folder, name.toString()), error = null)
            }
        }
        // The keys of the app. The global ones that the dictionary also lists stay, since only the app domain changes.
        defaults.dictionaryRepresentation().keys.forEach { key -> defaults.removeObjectForKey(key.toString()) }
        NSURLCache.sharedURLCache.removeAllCachedResponses()
    }

    /** The app starts already signed in with this session. */
    fun writeSession(text: String) {
        IosFiles.writeText(sessionFile(), text)
    }

    /** A build with Firebase asks for the consent of Analytics. The tests accept it before the start, like on Android. */
    fun acceptTelemetry() {
        defaults.setBool(true, "mts.telemetry.consent")
    }

    fun sleep(millis: Long) {
        usleep((millis * 1_000).toUInt())
    }

    fun timeZoneId(): String = NSTimeZone.localTimeZone.name

    private fun sessionFile() = IosFiles.child(IosFiles.supportDirectory(), "auth_session.json")
}
