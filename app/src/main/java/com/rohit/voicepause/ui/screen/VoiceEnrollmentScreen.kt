package com.rohit.voicepause.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.voicepause.audio.SpeakerVerifier
import com.rohit.voicepause.audio.VoiceEnrollmentManager
import com.rohit.voicepause.ui.components.PausifyHeader
import com.rohit.voicepause.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VoiceEnrollmentScreen(
    speakerVerifier: SpeakerVerifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val enrollmentManager = remember { VoiceEnrollmentManager(context, speakerVerifier) }
    val status by enrollmentManager.status.collectAsState()
    val progress by enrollmentManager.progress.collectAsState()

    // Sentences to read
    val sentences = remember {
        listOf(
            "The quick brown fox jumps over the lazy dog.",
            "Early to bed and early to rise makes a man healthy.",
            "The sun sets slowly behind the distant mountains.",
            "Technology is best when it brings people together.",
            "A journey of a thousand miles begins with a single step."
        )
    }

    var currentSentenceIndex by remember { mutableIntStateOf(0) }

    // Update sentence every 4 seconds during recording
    LaunchedEffect(status) {
        if (status is VoiceEnrollmentManager.EnrollmentStatus.Recording) {
            while (true) {
                delay(4000)
                currentSentenceIndex = (currentSentenceIndex + 1) % sentences.size
            }
        } else {
            currentSentenceIndex = 0
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        PausifyHeader(
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            onNavigationClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(20.dp))
            
            Text(
                "VOICE ENROLLMENT",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "Establish your unique voice signature for precision filtering.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDisabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(40.dp))

            // Pulse Animation Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                if (status is VoiceEnrollmentManager.EnrollmentStatus.Recording) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = PausifyRed.copy(alpha = 0.2f),
                            radius = (size.minDimension / 2) * pulseScale,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (status is VoiceEnrollmentManager.EnrollmentStatus.Recording) {
                            enrollmentManager.stopEnrollment()
                        } else if (status !is VoiceEnrollmentManager.EnrollmentStatus.Processing) {
                            enrollmentManager.startEnrollment()
                        }
                    },
                    shape = CircleShape,
                    color = if (status is VoiceEnrollmentManager.EnrollmentStatus.Recording) PausifyRed else SurfaceDark,
                    modifier = Modifier
                        .size(100.dp)
                        .border(BorderStroke(1.dp, DividerColor), CircleShape),
                    enabled = status !is VoiceEnrollmentManager.EnrollmentStatus.Processing
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (status is VoiceEnrollmentManager.EnrollmentStatus.Recording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Reading Prompt Card
            AnimatedVisibility(
                visible = status is VoiceEnrollmentManager.EnrollmentStatus.Recording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, PausifyRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "PLEASE READ ALOUD:",
                            style = MaterialTheme.typography.labelSmall,
                            color = PausifyRed,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            sentences[currentSentenceIndex],
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 28.sp
                        )
                    }
                }
            }

            if (status !is VoiceEnrollmentManager.EnrollmentStatus.Recording) {
                Spacer(Modifier.height(32.dp))
                // Status Message
                val statusText = when (status) {
                    is VoiceEnrollmentManager.EnrollmentStatus.Idle -> "READY TO INITIALIZE"
                    is VoiceEnrollmentManager.EnrollmentStatus.Processing -> "SYNTHESIZING SIGNATURE..."
                    is VoiceEnrollmentManager.EnrollmentStatus.Success -> "ENROLLMENT COMPLETE"
                    is VoiceEnrollmentManager.EnrollmentStatus.Error -> (status as VoiceEnrollmentManager.EnrollmentStatus.Error).message
                    else -> ""
                }
                
                val statusColor = when (status) {
                    is VoiceEnrollmentManager.EnrollmentStatus.Success -> Color.Green
                    is VoiceEnrollmentManager.EnrollmentStatus.Error -> PausifyRed
                    else -> Color.White
                }

                Text(
                    statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (status is VoiceEnrollmentManager.EnrollmentStatus.Recording || status is VoiceEnrollmentManager.EnrollmentStatus.Processing) {
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PausifyRed,
                    trackColor = DividerColor,
                )
            }

            Spacer(Modifier.weight(1f))

            if (status is VoiceEnrollmentManager.EnrollmentStatus.Success) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = PausifyRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                ) {
                    Text("DONE", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
            }

            if (status is VoiceEnrollmentManager.EnrollmentStatus.Error || status is VoiceEnrollmentManager.EnrollmentStatus.Idle) {
                 Text(
                    "Speak clearly at a normal volume. Reading the prompts helps the system recognize your voice faster.",
                    color = TextDisabled,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}
