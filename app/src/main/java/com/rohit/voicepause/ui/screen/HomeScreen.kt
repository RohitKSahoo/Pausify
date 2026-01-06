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

    // 🎛 Slider state (persisted)
    var voiceThreshold by remember {
        mutableStateOf(Settings.getVoiceThreshold(context).toFloat())
    }

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
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
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

            // 🎙️ Voice Sensitivity
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Sensitivity",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = voiceThreshold,
                        onValueChange = {
                            voiceThreshold = it
                            Settings.setVoiceThreshold(context, it.toInt())
                        },
                        valueRange = 1000f..6000f,
                        steps = 10
                    )

                    Text(
                        text = "Threshold: ${voiceThreshold.toInt()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ⏱️ Silence Duration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Silence Duration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = silenceDuration,
                        onValueChange = {
                            silenceDuration = it
                            Settings.setSilenceDuration(context, it.toLong())
                        },
                        valueRange = 500f..5000f,
                        steps = 9
                    )

                    Text(
                        text = "Resume after ${(silenceDuration / 1000).toInt()}s silence",
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
