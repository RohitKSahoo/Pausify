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
import com.rohit.voicepause.ProfileIntent
import com.rohit.voicepause.Settings
import com.rohit.voicepause.VoiceMonitorService
import com.rohit.voicepause.audio.AudioProfile
import kotlinx.coroutines.delay

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

    // ===== STATE =====
    var isRunning by remember { mutableStateOf(false) }
    var showStoppedSnackbar by remember { mutableStateOf(false) }

    var selectedProfile by remember {
        mutableStateOf(AudioProfile.BUSY)
    }

    var resumeDelaySeconds by remember {
        mutableStateOf(Settings.getSilenceDurationSeconds(context).toFloat())
    }

    // Initial sync
    LaunchedEffect(Unit) {
        isRunning = Settings.isServiceRunning(context)
    }

    // Service broadcast listener
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    VoiceMonitorService.ACTION_SERVICE_STARTED -> {
                        isRunning = true
                    }
                    VoiceMonitorService.ACTION_SERVICE_STOPPED -> {
                        isRunning = false
                        showStoppedSnackbar = true
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(VoiceMonitorService.ACTION_SERVICE_STARTED)
            addAction(VoiceMonitorService.ACTION_SERVICE_STOPPED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Snackbar
    LaunchedEffect(showStoppedSnackbar) {
        if (showStoppedSnackbar) {
            snackbarHostState.showSnackbar("Pausify service stopped")
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
                text = "Pausify",
                style = MaterialTheme.typography.headlineMedium
            )

            // ===== STATUS =====
            Text(
                text = if (isRunning) "Status: Listening" else "Status: Stopped",
                style = MaterialTheme.typography.bodyMedium
            )

            // ===== AUDIO PROFILE =====
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audio Profile",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AudioProfile.values().forEach { profile ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedProfile == profile,
                                onClick = {
                                    selectedProfile = profile
                                    context.sendBroadcast(
                                        Intent(ProfileIntent.ACTION_PROFILE_CHANGED).apply {
                                            putExtra(
                                                ProfileIntent.EXTRA_PROFILE_NAME,
                                                profile.name
                                            )
                                        }
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.displayName)
                        }
                    }
                }
            }

            // ===== RESUME DELAY =====
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Resume Delay",
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
                        text = "Resume after ${resumeDelaySeconds.toInt()} second(s) of silence",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ===== START / STOP =====
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            ) {
                Text("Start")
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = isRunning
            ) {
                Text("Stop")
            }
        }
    }

    // Safety re-sync (prevents UI desync)
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val actual = Settings.isServiceRunning(context)
            if (actual != isRunning) {
                isRunning = actual
            }
        }
    }
}
