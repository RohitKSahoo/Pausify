package com.rohit.voicepause.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.voicepause.Settings
import com.rohit.voicepause.ui.components.PausifyHeader
import com.rohit.voicepause.ui.theme.*

@Composable
fun ControlsScreen(onOpenActivity: () -> Unit) {
    val context = LocalContext.current
    
    // States linked directly to persistent Settings
    var sensitivity by remember { mutableStateOf(Settings.getCustomVoiceSensitivity(context)) }
    var isDuckVolumeEnabled by remember { mutableStateOf(Settings.isDuckVolumeEnabled(context)) }
    var isAutoResumeEnabled by remember { mutableStateOf(Settings.isAutoResumeEnabled(context)) }
    var selectedMode by remember { mutableIntStateOf(Settings.getEngineMode(context)) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
    ) {
        PausifyHeader()
        
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "CONTROLS",
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

            // Quick Access to Activity/Live Monitor
            ControlCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenActivity() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(PausifyRed.copy(alpha = 0.1f), PausifyShapes.small),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PausifyRed)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("LIVE MONITOR", color = Color.White, fontSize = 16.sp)
                            Text("VIEW ACTIVE SIGNALS", color = TextDisabled, fontSize = 11.sp)
                        }
                    }
                    Text("OPEN —", color = PausifyRed, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            
            // Sensitivity Section (Moved from Settings)
            ControlCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SENSITIVITY", color = TextSecondary, fontSize = 14.sp)
                        Text("INPUT GAIN THRESHOLD", color = TextDisabled, fontSize = 11.sp)
                    }
                    Text("${(sensitivity * 100).toInt()}", color = PausifyRed, fontSize = 28.sp)
                }
                
                Spacer(Modifier.height(20.dp))
                
                Slider(
                    value = sensitivity,
                    onValueChange = { 
                        sensitivity = it
                        Settings.setCustomVoiceSensitivity(context, it)
                    },
                    valueRange = 0.1f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PausifyRed,
                        activeTrackColor = PausifyRed,
                        inactiveTrackColor = DividerColor
                    )
                )
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Duck Volume
            ControlCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("DUCK VOLUME", color = TextSecondary, fontSize = 14.sp)
                        Text("STREAM ATTENUATION", color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isDuckVolumeEnabled,
                        onCheckedChange = { 
                            isDuckVolumeEnabled = it
                            Settings.setDuckVolumeEnabled(context, it)
                        },
                        modifier = Modifier.scale(1.1f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PausifyRed,
                            checkedTrackColor = PausifyRed.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = DividerColor
                        )
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Auto Resume
            ControlCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AUTO RESUME", color = TextSecondary, fontSize = 14.sp)
                        Text("PLAYBACK STATE RECOVERY", color = TextDisabled, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isAutoResumeEnabled,
                        onCheckedChange = { 
                            isAutoResumeEnabled = it
                            Settings.setAutoResumeEnabled(context, it)
                        },
                        modifier = Modifier.scale(1.1f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PausifyRed,
                            checkedTrackColor = PausifyRed.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextDisabled,
                            uncheckedTrackColor = DividerColor
                        )
                    )
                }
            }
            
            Spacer(Modifier.height(40.dp))
            
            Text("ENGINE MODES", color = TextDisabled, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(20.dp))
            
            ModeItem(
                number = "01", 
                title = "ADAPTIVE FOCUS", 
                subtitle = "REAL-TIME VAD TUNING", 
                active = selectedMode == 1,
                onClick = { 
                    selectedMode = 1
                    Settings.setEngineMode(context, 1)
                }
            )
            ModeItem(
                number = "02", 
                title = "STRICT SILENCE", 
                subtitle = "FULL SOP SUPPRESSION", 
                active = selectedMode == 2,
                onClick = { 
                    selectedMode = 2
                    Settings.setEngineMode(context, 2)
                }
            )
            
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun ControlCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = PausifyShapes.medium
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

@Composable
fun ModeItem(number: String, title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                number,
                color = if (active) PausifyRed else TextDisabled,
                fontSize = 16.sp,
                modifier = Modifier.width(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (active) Color.White else TextSecondary,
                    fontSize = 16.sp
                )
                Text(
                    subtitle,
                    color = TextDisabled,
                    fontSize = 11.sp
                )
            }
            if (active) {
                Box(Modifier.size(6.dp).background(PausifyRed))
            }
        }
    }
}
