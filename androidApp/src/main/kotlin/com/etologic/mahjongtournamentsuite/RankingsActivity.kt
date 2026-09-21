package com.etologic.mahjongtournamentsuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class RankingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidApp(applicationContext)
        setContent {
            RankingApp(
                tournamentId = intent.getStringExtra(EXTRA_TOURNAMENT_ID).orEmpty(),
                tournamentName = intent.getStringExtra(EXTRA_TOURNAMENT_NAME),
            )
        }
    }

    private companion object {
        const val EXTRA_TOURNAMENT_ID = "tournamentId"
        const val EXTRA_TOURNAMENT_NAME = "tournamentName"
    }
}
