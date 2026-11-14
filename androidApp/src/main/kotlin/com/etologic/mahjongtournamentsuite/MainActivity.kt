package com.etologic.mahjongtournamentsuite

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

/**
 * The one Android app of the suite. It opens the player part, which everyone uses. Admins and editors switch to the
 * admin part with the button of the top bar, and back. See [SuiteRoot].
 */
class MainActivity : ComponentActivity() {
    /** A start request that came with a new intent: the part to show and whether the player part opens its sign-in. */
    private val startRequest = mutableStateOf<SuiteStart?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initAndroidApp(applicationContext)
        // A new activity starts where the intent says. A restored one keeps the part that was open.
        val initial = if (savedInstanceState == null) intent.suiteStart() else null

        setContent {
            SuiteRoot(
                initial = initial,
                startRequest = startRequest.value,
                onStartHandled = { startRequest.value = null },
                post = { action -> Handler(Looper.getMainLooper()).post(action) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.suiteStart()?.let { startRequest.value = it }
    }

    companion object {
        /** `admin` opens the admin part. The UI tests of the admin part start the app with it. */
        const val EXTRA_SECTION = "mts.section"
    }
}

private fun Intent.suiteStart(): SuiteStart? = when (getStringExtra(MainActivity.EXTRA_SECTION)) {
    "admin" -> SuiteStart(SuiteSection.ADMIN)
    "player" -> SuiteStart(SuiteSection.PLAYER)
    else -> null
}
