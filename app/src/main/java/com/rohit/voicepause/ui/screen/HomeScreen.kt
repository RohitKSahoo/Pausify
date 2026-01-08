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

    // One-time migration
    LaunchedEffect(Unit) {
        Settings.migrate(context)
    }

    // Service state
    var isRunning by remember {
        mutableStateOf(Settings.isServiceRunning(context))
    }

    var showStoppedSnackbar by remember { mutableStateOf(false) }

    // ⏱ Resume delay in SECONDS (1–10)
    var resumeDelaySeconds by remember {
        mutableStateOf(Settings.getSilenceDurationSeconds(context).toFloat())
    }

    // Listen for service stop broadcast
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

        onDispose { context.unregisterReceiver(receiver) }
    }

    // Snackbar
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

            // ⏱ Resume Delay (1–10 seconds)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Resume Delay",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = resumeDelaySeconds,
                        onValueChange = {
                            val rounded = it.toInt().coerceIn(1, 10)
                            resumeDelaySeconds = rounded.toFloat()
                            Settings.setSilenceDurationSeconds(context, rounded)
                        },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    Text(
                        text = "Resume music after ${resumeDelaySeconds.toInt()} second(s) of silence",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Controls how long VoicePause waits after you stop speaking before resuming music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ℹ️ Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "How it works",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "VoicePause listens for your voice while music is playing. When you speak, it pauses the music. When you stop speaking, it waits for the selected delay and then resumes playback automatically.",
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
