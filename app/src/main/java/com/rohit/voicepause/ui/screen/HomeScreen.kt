package com.rohit.voicepause.ui.screen

import android.media.AudioManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.voicepause.Settings
import com.rohit.voicepause.audio.AudioProfile
import com.rohit.voicepause.ui.components.PausifyHeader
import com.rohit.voicepause.ui.components.ScrambledText
import com.rohit.voicepause.ui.components.VolumetricSphere
import com.rohit.voicepause.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isRunning: Boolean,
    onToggleService: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    
    var serviceActive by remember { mutableStateOf(isRunning) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf(Settings.getSelectedProfile(context)) }

    LaunchedEffect(isRunning) {
        serviceActive = isRunning
    }

    LaunchedEffect(Unit) {
        while (true) {
            val actualRunning = Settings.isServiceRunning(context)
            if (serviceActive != actualRunning) {
                serviceActive = actualRunning
            }
            isMusicPlaying = audioManager.isMusicActive
            delay(200)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        PausifyHeader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // Animated Status Text
            ScrambledText(
                text = if (serviceActive) "Active" else "Inactive",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontSize = 48.sp,
                durationMillis = 1000
            )

            Text(
                text = if (serviceActive) "Monitoring for priority sound\nsignals." 
                       else "Engine is currently offline.\nTap to start monitoring.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 24.sp,
                fontSize = 18.sp
            )

            // Reactive Visualizer with 3D Volumetric Sphere (Gradual transition handled inside)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                VolumetricSphere(isActive = serviceActive, isMusicPlaying = isMusicPlaying)
            }

            // Sound Environment Profiles Section
            Column {
                Text(
                    "SOUND ENVIRONMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    letterSpacing = 1.5.sp,
                    fontSize = 12.sp
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AudioProfile.values().forEach { profile ->
                        val isSelected = selectedProfile == profile
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedProfile = profile
                                    Settings.setSelectedProfile(context, profile)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                profile.displayName,
                                color = if (isSelected) Color.White else TextDisabled,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(PausifyRed)
                                )
                            } else {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Listening Card / Toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = if (serviceActive) PausifyRed else CardBackground,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleService
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "LISTENING",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (serviceActive) Color.White else TextSecondary,
                            letterSpacing = 1.sp,
                            fontSize = 20.sp
                        )
                        Text(
                            "SIGNALS ALERT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (serviceActive) Color.White.copy(alpha = 0.8f) else TextDisabled,
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (serviceActive) Color.White.copy(alpha = 0.8f) else TextDisabled,
                                fontSize = 12.sp
                            )
                            // Animated Status Subtext
                            ScrambledText(
                                text = if (serviceActive) "ACTIVE" else "STOPPED",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (serviceActive) Color.White else TextSecondary,
                                fontSize = 16.sp,
                                durationMillis = 600
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (serviceActive) Color.White.copy(alpha = 0.2f) else BackgroundDark,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = if (serviceActive) Color.White else TextDisabled,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}
