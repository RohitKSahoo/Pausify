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

    // 🔁 Service state
    var isRunning by remember { mutableStateOf(false) }
    var showStoppedSnackbar by remember { mutableStateOf(false) }

    // 🎙 Voice sensitivity (1–100)
    var voiceSensitivity by remember {
        mutableStateOf(Settings.getVoiceSensitivity(context))
    }

    // ⏱ Silence duration (seconds: 1–20)
    var silenceSeconds by remember {
        mutableStateOf(Settings.getSilenceSeconds(context))
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

            // 🎙 Voice Sensitivity
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Voice Sensitivity", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = voiceSensitivity.toFloat(),
                        onValueChange = {
                            voiceSensitivity = it.toInt()
                            Settings.setVoiceSensitivity(context, voiceSensitivity)
                        },
                        valueRange = 1f..100f,
                        steps = 98
                    )

                    Text(
                        text = "Sensitivity: $voiceSensitivity",
                        style = MaterialTheme.typography.bodySmall
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
                    Text("Silence Duration", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = silenceSeconds.toFloat(),
                        onValueChange = {
                            silenceSeconds = it.toInt()
                            Settings.setSilenceSeconds(context, silenceSeconds)
                        },
                        valueRange = 1f..20f,
                        steps = 18
                    )

                    Text(
                        text = "Resume after ${silenceSeconds}s of silence",
                        style = MaterialTheme.typography.bodySmall
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
                Text("Start")
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
