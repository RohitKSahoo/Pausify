package com.rohit.voicepause

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rohit.voicepause.ui.components.PausifyBottomNav
import com.rohit.voicepause.ui.screen.*
import com.rohit.voicepause.ui.theme.VoicePauseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Settings.migrate(this)

        setContent {
            VoicePauseTheme {
                val navController = rememberNavController()
                
                // Collect isRunning from the Service's Flow for real-time updates
                val isRunning by VoiceMonitorService.isRunningFlow.collectAsState()

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            when (intent?.action) {
                                VoiceMonitorService.ACTION_SERVICE_STARTED -> {
                                    navController.navigate(Screen.Activity.route) {
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                    }
                                }
                                VoiceMonitorService.ACTION_SERVICE_STOPPED -> {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(0)
                                    }
                                }
                            }
                        }
                    }
                    val filter = IntentFilter().apply {
                        addAction(VoiceMonitorService.ACTION_SERVICE_STARTED)
                        addAction(VoiceMonitorService.ACTION_SERVICE_STOPPED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(receiver, filter)
                    }
                    onDispose { unregisterReceiver(receiver) }
                }

                Scaffold(
                    bottomBar = { PausifyBottomNav(navController) }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(navController, startDestination = Screen.Home.route) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    isRunning = isRunning,
                                    onToggleService = {
                                        if (isRunning) stopVoicePause()
                                        else requestPermissionsAndStart()
                                    }
                                )
                            }
                            composable(Screen.Activity.route) {
                                ActivityScreen(
                                    onEndSession = { stopVoicePause() }
                                )
                            }
                            composable(Screen.Controls.route) {
                                ControlsScreen()
                            }
                            composable(Screen.Settings.route) {
                                SettingsScreen()
                            }
                        }
                    }
                }
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
            val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
            val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    permissions[Manifest.permission.POST_NOTIFICATIONS] == true

            if (micGranted && notificationGranted) {
                startVoicePauseInternal()
            }
        }

    private fun requestPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissionsToRequest.isEmpty()) {
            startVoicePauseInternal()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startVoicePauseInternal() {
        val intent = Intent(this, VoiceMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVoicePause() {
        stopService(Intent(this, VoiceMonitorService::class.java))
    }
}
