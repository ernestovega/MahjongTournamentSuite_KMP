package com.etologic.mahjongtournamentsuite.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(applicationContext)
        // Phones and tablets type the score of a hand on the numpad of the app, not on the system keyboard.
        setContent { PlayerApp(numpadEntry = true) }
    }
}
