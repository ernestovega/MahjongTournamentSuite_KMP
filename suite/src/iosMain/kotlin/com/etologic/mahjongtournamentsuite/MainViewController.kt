package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.etologic.mahjongtournamentsuite.data.platform.IosAnalytics
import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import com.etologic.mahjongtournamentsuite.data.session.storedSessionUid
import com.etologic.mahjongtournamentsuite.player.siteLinkOf
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/*
 * What the Swift side of iosApp calls. Swift sees this file as the `Shared` framework:
 * - `MainViewControllerKt.MainViewController(url:)` is the app.
 * - `IosSuite.shared` takes the Firebase SDKs and the links that open the app.
 */

/** A link that opened the running app. SuiteRoot shows it and clears it. */
private var pendingStart by mutableStateOf<SuiteStart?>(null)

/** The app: the player part and the admin part, like the Android app. [url] is the link that started it, if any. */
@Suppress("FunctionName", "unused")
fun MainViewController(url: String?): UIViewController {
    val initial = url?.let(::suiteStartOfUrl)
    return ComposeUIViewController {
        SuiteRoot(
            initial = initial,
            startRequest = pendingStart,
            onStartHandled = { pendingStart = null },
            post = { action -> dispatch_async(dispatch_get_main_queue()) { action() } },
            storedSessionUid = ::storedSessionUid,
            onPartShown = { part -> IosPlatform.currentApp = part },
        )
    }
}

/** The Firebase SDKs of the Swift side and the links of the site. */
@Suppress("unused")
object IosSuite {
    /** App Check with App Attest. [provider] answers with a token, or null and the kind of error. */
    fun configureAppCheck(provider: MtsAppCheck, debug: Boolean) {
        IosPlatform.appCheckState = if (debug) "debug" else "ok"
        IosPlatform.appCheckToken = {
            suspendCancellableCoroutine { continuation ->
                provider.token { token, error ->
                    IosPlatform.appCheckState = when {
                        token != null -> if (debug) "debug" else "ok"
                        else -> "failed:${error.orEmpty().take(60)}"
                    }
                    if (continuation.isActive) continuation.resume(token)
                }
            }
        }
    }

    /** Firebase Analytics. It stays off until the user accepts it in the app. */
    fun configureAnalytics(analytics: MtsAnalytics) {
        IosPlatform.analytics = object : IosAnalytics {
            override fun setCollectionEnabled(enabled: Boolean) = analytics.setCollectionEnabled(enabled)

            override fun logEvent(name: String, params: Map<String, Any>) = analytics.logEvent(name, params)

            override fun setUserProperty(name: String, value: String?) = analytics.setUserProperty(name, value)
        }
    }

    /**
     * A link of the site that opened the running app (a universal link, the QR of a table). Returns false for a page
     * that is not the app, which Swift then opens in Safari.
     */
    fun openUrl(url: String): Boolean {
        val start = suiteStartOfUrl(url) ?: return false
        pendingStart = start
        return true
    }
}

/** App Check of Firebase, in Swift. */
interface MtsAppCheck {
    /** Calls [completion] with a token, or with null and the kind of error. */
    fun token(completion: (token: String?, error: String?) -> Unit)
}

/** Firebase Analytics, in Swift. */
interface MtsAnalytics {
    fun setCollectionEnabled(enabled: Boolean)

    fun logEvent(name: String, params: Map<String, Any>)

    fun setUserProperty(name: String, value: String?)
}

internal fun suiteStartOfUrl(url: String): SuiteStart? {
    val components = NSURLComponents(string = url)
    val query = components.queryItems.orEmpty()
        .mapNotNull { it as? NSURLQueryItem }
        .associate { it.name to it.value.orEmpty() }
    return suiteStartOf(siteLinkOf(components.path.orEmpty(), query))
}
