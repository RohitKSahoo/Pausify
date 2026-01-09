package com.rohit.voicepause

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rohit.voicepause.ui.screen.HomeScreen
import com.rohit.voicepause.ui.theme.VoicePauseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // One-time migration
        Settings.migrate(this)

        setContent {
            VoicePauseTheme {
                HomeScreen(
                    onStartClick = { requestPermissionsAndStart() },
                    onStopClick = { stopVoicePause() }
                )
            }
        }
    }

    // ======================
    // PERMISSION HANDLING
    // ======================

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val micGranted =
                permissions[Manifest.permission.RECORD_AUDIO] == true

            val notificationGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        permissions[Manifest.permission.POST_NOTIFICATIONS] == true

            if (micGranted && notificationGranted) {
                startVoicePauseInternal()
            }
        }

    private fun requestPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.RECORD_AUDIO
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissionsToRequest.isEmpty()) {
            startVoicePauseInternal()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // ======================
    // SERVICE CONTROL
    // ======================

    private fun startVoicePauseInternal() {
        val intent = Intent(this, VoiceMonitorService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVoicePause() {
        stopService(
            Intent(this, VoiceMonitorService::class.java)
        )
    }
}
