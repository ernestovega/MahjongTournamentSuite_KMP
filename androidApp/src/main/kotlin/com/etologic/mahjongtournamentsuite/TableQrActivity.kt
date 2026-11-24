package com.etologic.mahjongtournamentsuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** The QR code of a tournament in a window of its own, to project it (docs/table-qr.md). */
class TableQrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidApp(applicationContext)
        setContent {
            TableQrApp(
                tournamentName = intent.getStringExtra(EXTRA_TOURNAMENT_NAME).orEmpty(),
                link = intent.getStringExtra(EXTRA_LINK).orEmpty(),
                logoUrl = intent.getStringExtra(EXTRA_LOGO_URL),
                organizerName = intent.getStringExtra(EXTRA_ORGANIZER_NAME).orEmpty(),
                organizerColor = intent.getStringExtra(EXTRA_ORGANIZER_COLOR).orEmpty(),
            )
        }
    }

    private companion object {
        const val EXTRA_TOURNAMENT_NAME = "tournamentName"
        const val EXTRA_LINK = "link"
        const val EXTRA_LOGO_URL = "logoUrl"
        const val EXTRA_ORGANIZER_NAME = "organizerName"
        const val EXTRA_ORGANIZER_COLOR = "organizerColor"
    }
}
