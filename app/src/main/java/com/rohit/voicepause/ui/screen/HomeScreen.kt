package com.rohit.voicepause.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
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

    // ===== STATE =====
    var isRunning by remember { mutableStateOf(false) }
    var showStoppedSnackbar by remember { mutableStateOf(false) }

    var selectedProfile by remember {
        mutableStateOf(Settings.getSelectedProfile(context))
    }

    // ===== CUSTOM VALUES =====

    var customPauseSec by remember {
        mutableStateOf(
            (Settings.getCustomPauseDurationMs(context) / 1000).toFloat()
        )
    }

    var customSensitivity by remember {
        mutableStateOf(Settings.getCustomVoiceSensitivity(context))
    }

    var customMinSpeech by remember {
        mutableStateOf(Settings.getCustomMinSpeechMs(context).toFloat())
    }

    var customMinEnergy by remember {
        mutableStateOf(Settings.getCustomMinEnergy(context).toFloat())
    }

    var customVadMode by remember {
        mutableStateOf(Settings.getCustomVadMode(context).toFloat())
    }

    // ======================
    // INIT
    // ======================

    LaunchedEffect(Unit) {
        Settings.migrate(context)
        isRunning = Settings.isServiceRunning(context)
    }

    // ======================
    // SERVICE RECEIVER
    // ======================

    DisposableEffect(Unit) {

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    VoiceMonitorService.ACTION_SERVICE_STARTED ->
                        isRunning = true

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

        onDispose { context.unregisterReceiver(receiver) }
    }

    // ======================
    // SNACKBAR
    // ======================

    LaunchedEffect(showStoppedSnackbar) {
        if (showStoppedSnackbar) {
            snackbarHostState.showSnackbar("Pausify service stopped")
            showStoppedSnackbar = false
        }
    }

    // ======================
    // UI
    // ======================

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),

            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Pausify", style = MaterialTheme.typography.headlineMedium)

            Text(
                if (isRunning) "Status: Listening"
                else "Status: Stopped"
            )

            // ======================
            // PROFILE
            // ======================

            Card(Modifier.fillMaxWidth()) {

                Column(Modifier.padding(16.dp)) {

                    Text("Audio Profile",
                        style = MaterialTheme.typography.titleMedium)

                    Spacer(Modifier.height(8.dp))

                    AudioProfile.values().forEach { profile ->

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            RadioButton(
                                selected = selectedProfile == profile,
                                onClick = {

                                    selectedProfile = profile

                                    Log.i(
                                        "UI_PROFILE",
                                        "Selected → ${profile.displayName}"
                                    )

                                    Settings.setSelectedProfile(context, profile)

                                    context.sendBroadcast(
                                        Intent(ProfileIntent.ACTION_PROFILE_CHANGED)
                                            .putExtra(
                                                ProfileIntent.EXTRA_PROFILE_NAME,
                                                profile.name
                                            )
                                    )
                                }
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(profile.displayName)
                        }
                    }
                }
            }

            // ======================
            // CUSTOM CONTROLS
            // ======================

            if (selectedProfile.isCustom) {

                CustomSlider(
                    "Pause Duration (sec)",
                    customPauseSec,
                    1f..15f,
                    13
                ) {

                    customPauseSec = it
                    Settings.setCustomPauseSeconds(context, it.toInt())
                }

                CustomSlider(
                    "Sensitivity",
                    customSensitivity,
                    0.8f..1.8f,
                    4
                ) {

                    customSensitivity = it
                    Settings.setCustomVoiceSensitivity(context, it)
                }

                CustomSlider(
                    "Min Speech (ms)",
                    customMinSpeech,
                    100f..1000f,
                    9
                ) {

                    customMinSpeech = it
                    Settings.setCustomMinSpeechMs(context, it.toLong())
                }

                CustomSlider(
                    "Min Energy",
                    customMinEnergy,
                    200f..1500f,
                    13
                ) {

                    customMinEnergy = it
                    Settings.setCustomMinEnergy(context, it.toInt())
                }

                CustomSlider(
                    "Noise Filter (VAD)",
                    customVadMode,
                    0f..3f,
                    2
                ) {

                    customVadMode = it
                    Settings.setCustomVadMode(context, it.toInt())
                }
            }

            // ======================
            // START / STOP
            // ======================

            Button(
                onClick = onStartClick,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start")
            }

            OutlinedButton(
                onClick = onStopClick,
                enabled = isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }
        }
    }

    // ======================
    // SAFETY RESYNC
    // ======================

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            isRunning = Settings.isServiceRunning(context)
        }
    }
}

// ======================
// SLIDER COMPONENT
// ======================

@Composable
private fun CustomSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {

    Card(Modifier.fillMaxWidth()) {

        Column(Modifier.padding(16.dp)) {

            Text(title, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(6.dp))

            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                steps = steps
            )

            Text(
                "Value: ${value.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
