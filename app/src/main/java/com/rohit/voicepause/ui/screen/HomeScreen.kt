package com.rohit.voicepause.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rohit.voicepause.Settings
import com.rohit.voicepause.VoiceMonitorService
import com.rohit.voicepause.ui.components.StatusCard

@Composable
fun HomeScreen(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Migrate old settings on first load
    LaunchedEffect(Unit) {
        Settings.migrateOldSettings(context)
    }

    // 🔁 Service state
    var isRunning by remember {
        mutableStateOf(Settings.isServiceRunning(context))
    }
    DisposableEffect(Unit) {
        isRunning = Settings.isServiceRunning(context)
        onDispose {}
    }
    var showStoppedSnackbar by remember { mutableStateOf(false) }

    // 🎙 VAD Aggressiveness (0-3)
    var vadAggressiveness by remember {
        mutableStateOf(Settings.getVadAggressiveness(context).toFloat())
    }

    // ⏱ Silence duration (milliseconds: 100-5000)
    var silenceDuration by remember {
        mutableStateOf(Settings.getSilenceDuration(context).toFloat())
    }

    // 🔔 Listen for service stop broadcast
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == VoiceMonitorService.ACTION_SERVICE_STOPPED) {
                    isRunning = false
                    showStoppedSnackbar = true
                }
            }
        }

        val filter = IntentFilter(VoiceMonitorService.ACTION_SERVICE_STOPPED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // 🍿 Snackbar
    LaunchedEffect(showStoppedSnackbar) {
        if (showStoppedSnackbar) {
            snackbarHostState.showSnackbar("VoicePause service stopped")
            showStoppedSnackbar = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "VoicePause",
                style = MaterialTheme.typography.headlineMedium
            )

            StatusCard(isRunning = isRunning)

            // 🎙 VAD Aggressiveness
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Voice Detection Sensitivity", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = vadAggressiveness,
                        onValueChange = {
                            vadAggressiveness = it
                            Settings.setVadAggressiveness(context, it.toInt())
                        },
                        valueRange = 0f..3f,
                        steps = 2
                    )

                    val sensitivityText = when (vadAggressiveness.toInt()) {
                        0 -> "Quality (least aggressive)"
                        1 -> "Balanced (recommended)"
                        2 -> "Aggressive (noisy environments)"
                        3 -> "Very aggressive (very noisy)"
                        else -> "Balanced"
                    }

                    Text(
                        text = "Level ${vadAggressiveness.toInt()}: $sensitivityText",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Higher levels filter out more background noise but may miss quiet speech",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ⏱ Silence Duration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resume Delay", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = silenceDuration,
                        onValueChange = {
                            silenceDuration = it
                            Settings.setSilenceDuration(context, it.toLong())
                        },
                        valueRange = 100f..5000f,
                        steps = 48 // 100ms increments
                    )

                    Text(
                        text = "Resume after ${(silenceDuration / 1000).let { 
                            if (it < 1) "${silenceDuration.toInt()}ms" 
                            else "${String.format("%.1f", it)}s" 
                        }} of silence",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "How long to wait after you stop speaking before resuming music",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ℹ️ Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "VoicePause uses advanced voice activity detection to automatically pause music when you speak and resume it when you're done. It only runs when music is playing to save battery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ▶ Start
            Button(
                onClick = {
                    onStartClick()
                    isRunning = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            ) {
                Text("Start Voice Detection")
            }

            // ⏹ Stop
            OutlinedButton(
                onClick = {
                    onStopClick()
                    isRunning = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isRunning
            ) {
                Text("Stop")
            }
        }
    }
}
