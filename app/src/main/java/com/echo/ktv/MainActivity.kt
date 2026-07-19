package com.echo.ktv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.echo.ktv.playback.KtvPlayerManager
import com.echo.ktv.server.KtvServerService
import com.echo.ktv.ui.KtvTheme
import com.echo.ktv.ui.MainTvScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Player Manager
        KtvPlayerManager.initialize(this)

        // 2. Start Ktor Background Service for Mobile song ordering
        val serviceIntent = Intent(this, KtvServerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = KtvTheme.Background
                ) {
                    val currentPlaying by KtvPlayerManager.currentPlaying.collectAsState()
                    
                    // Intercept back button when player is overlaying fullscreen
                    BackHandler(enabled = currentPlaying != null) {
                        // Pressing back stops the current playback and returns to dashboard
                        KtvPlayerManager.skipCurrent()
                    }

                    MainTvScreen()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle physical TV remote volume keys (independent volume logic)
        // If desired, we can intercept volume keys to change app accompaniment volume directly.
        // For standard remote control compatibility, we pass to system.
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop Ktor Service
        stopService(Intent(this, KtvServerService::class.java))
        // Release Player resources
        KtvPlayerManager.release()
    }
}
