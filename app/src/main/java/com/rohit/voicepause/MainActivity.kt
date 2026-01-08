package com.rohit.voicepause

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rohit.voicepause.ui.screen.HomeScreen
import com.rohit.voicepause.ui.theme.VoicePauseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Migrate old settings to new VAD-based system
        Settings.migrateOldSettings(this)

        setContent {
            VoicePauseTheme {
                HomeScreen(
                    onStartClick = { startVoicePause() },
                    onStopClick = { stopVoicePause() }
                )
            }
        }
    }

    private fun startVoicePause() {
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
            return
        }

        // Microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1002
            )
            return
        }

        startForegroundService(
            Intent(this, VoiceMonitorService::class.java)
        )
    }

    private fun stopVoicePause() {
        stopService(
            Intent(this, VoiceMonitorService::class.java)
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoicePause()
        }
    }
}
