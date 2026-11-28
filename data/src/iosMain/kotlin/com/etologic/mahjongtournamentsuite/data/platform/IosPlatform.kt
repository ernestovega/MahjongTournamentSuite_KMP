package com.etologic.mahjongtournamentsuite.data.platform

import platform.Foundation.NSBundle

/**
 * What the iOS app tells the shared code. The Swift side of `iosApp` fills it in before the first screen, with the
 * Firebase SDKs that only exist in Swift (App Check and Analytics). Without them the app works and sends no token
 * and no Analytics. See docs/ios-release.md.
 */
object IosPlatform {
    /** `admin` or `player`: the part of the app on the screen, for the telemetry. Like the Android app. */
    var currentApp: String? = null

    /** Asks Firebase App Check for a token. Null while App Check is not set up. */
    var appCheckToken: (suspend () -> String?)? = null

    /** What happened with App Check, for the logs of the server: `off`, `ok`, `debug` or `failed:<kind>`. */
    var appCheckState: String = "off"

    /** Firebase Analytics. Null while the app has no `GoogleService-Info.plist`. */
    var analytics: IosAnalytics? = null

    /** True in a debug build of Xcode. */
    val isDebug: Boolean get() = infoString("MTSEnvironment") == "debug"

    /**
     * The backend of this build, from `MTSBackendUrl` in the Info.plist. The staging build names the staging project.
     * Without the key the app uses the production backend.
     */
    val backendUrl: String? get() = backendUrlOverride ?: infoString("MTSBackendUrl")?.ifBlank { null }

    /** The end-to-end tests set this before the first request to use a local backend. The app never sets it. */
    var backendUrlOverride: String? = null

    /** The version that the user sees, from the Info.plist. */
    val versionName: String? get() = infoString("CFBundleShortVersionString")

    private fun infoString(key: String): String? = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String
}

/** Firebase Analytics, implemented in Swift. */
interface IosAnalytics {
    fun setCollectionEnabled(enabled: Boolean)

    fun logEvent(name: String, params: Map<String, Any>)

    fun setUserProperty(name: String, value: String?)
}
