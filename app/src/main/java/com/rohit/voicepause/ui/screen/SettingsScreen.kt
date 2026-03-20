package com.rohit.voicepause.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.voicepause.Settings
import com.rohit.voicepause.ui.components.PausifyHeader
import com.rohit.voicepause.ui.theme.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // States linked to Settings
    var sensitivity by remember { mutableStateOf(Settings.getCustomVoiceSensitivity(context)) }
    var pauseSec by remember { mutableStateOf((Settings.getCustomPauseDurationMs(context) / 1000).toInt()) }
    var backgroundProcessEnabled by remember { mutableStateOf(Settings.isServiceRunning(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(scrollState)
    ) {
        PausifyHeader()

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "SETTINGS",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontSize = 40.sp
            )
            Text(
                "ENGINE CONFIGURATION V2.4",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.5.sp,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(40.dp))

            // Sensitivity
            SettingItem(
                title = "SENSITIVITY",
                subtitle = "Sensor response threshold",
                status = "ACTIVE",
                hasSlider = true,
                sliderValue = sensitivity,
                onSliderChange = {
                    sensitivity = it
                    Settings.setCustomVoiceSensitivity(context, it)
                }
            )

            Spacer(Modifier.height(32.dp))

            // Timeout
            SettingItem(
                title = "TIMEOUT",
                subtitle = "Post-activity delay",
                value = "${pauseSec}.00s",
                hasChevron = true,
                onClick = {
                    val next = when (pauseSec) {
                        3 -> 5
                        5 -> 10
                        10 -> 15
                        else -> 3
                    }
                    pauseSec = next
                    Settings.setCustomPauseSeconds(context, next)
                }
            )

            Spacer(Modifier.height(32.dp))

            // Background Process
            SettingItem(
                title = "BACKGROUND PROCESS",
                subtitle = "Persistent execution",
                hasSwitch = true,
                switchState = backgroundProcessEnabled,
                onSwitchChange = {
                    backgroundProcessEnabled = it
                    Settings.setServiceRunning(context, it)
                }
            )

            Spacer(Modifier.height(32.dp))

            // Encryption & Privacy
            SettingItem(
                title = "ENCRYPTION & PRIVACY",
                subtitle = "Data handling protocol",
                hasChevron = true,
                onClick = {
                    // Placeholder for privacy screen
                }
            )

            Spacer(Modifier.height(32.dp))

            // Factory Reset
            SettingItem(
                title = "FACTORY RESET",
                subtitle = "Wipe all module signatures",
                hasChevron = true,
                onClick = {
                    // Reset all to defaults
                    sensitivity = 1.0f
                    pauseSec = 3
                    backgroundProcessEnabled = false
                    
                    Settings.setCustomVoiceSensitivity(context, 1.0f)
                    Settings.setCustomPauseSeconds(context, 3)
                    Settings.setServiceRunning(context, false)
                }
            )

            Spacer(Modifier.height(64.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("...", color = PausifyRed, fontSize = 32.sp)
                Text(
                    "TERMINAL BUILD // 7824",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    letterSpacing = 2.sp,
                    fontSize = 12.sp
                )
            }
            
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    status: String? = null,
    value: String? = null,
    hasSlider: Boolean = false,
    sliderValue: Float = 0f,
    onSliderChange: (Float) -> Unit = {},
    hasSwitch: Boolean = false,
    switchState: Boolean = false,
    onSwitchChange: (Boolean) -> Unit = {},
    hasChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextSecondary, fontSize = 14.sp)
                    Text(subtitle, color = TextDisabled, fontSize = 11.sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status != null) {
                        Text(status, color = PausifyRed, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (value != null) {
                        Text(value, color = Color.White, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (hasSwitch) {
                        Switch(
                            checked = switchState,
                            onCheckedChange = onSwitchChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PausifyRed,
                                checkedTrackColor = PausifyRed.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextDisabled,
                                uncheckedTrackColor = Color.Transparent,
                                uncheckedBorderColor = TextDisabled
                            )
                        )
                    }
                    if (hasChevron) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextDisabled,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            if (hasSlider) {
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    valueRange = 0.1f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PausifyRed,
                        activeTrackColor = PausifyRed,
                        inactiveTrackColor = DividerColor
                    )
                )
            }
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DividerColor, thickness = 1.dp)
        }
    }
}
