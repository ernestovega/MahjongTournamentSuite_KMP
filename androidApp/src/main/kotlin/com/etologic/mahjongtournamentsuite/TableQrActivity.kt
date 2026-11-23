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
            )
        }
    }

    private companion object {
        const val EXTRA_TOURNAMENT_NAME = "tournamentName"
        const val EXTRA_LINK = "link"
    }
}
