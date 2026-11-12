package com.etologic.mahjongtournamentsuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class TimerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidApp(applicationContext)
        setContent {
            TimerApp(
                tournamentId = intent.getStringExtra(EXTRA_TOURNAMENT_ID).orEmpty(),
                initialRound = intent.getIntExtra(EXTRA_INITIAL_ROUND, 1),
            )
        }
    }

    private companion object {
        const val EXTRA_TOURNAMENT_ID = "tournamentId"
        const val EXTRA_INITIAL_ROUND = "initialRound"
    }
}
