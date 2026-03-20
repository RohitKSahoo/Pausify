package com.rohit.voicepause.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohit.voicepause.ui.components.PausifyHeader
import com.rohit.voicepause.ui.theme.*

@Composable
fun ActivityScreen(
    onEndSession: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Grid Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 40.dp.toPx()
            for (x in 0..size.width.toInt() step step.toInt()) {
                drawLine(
                    color = GridLineColor,
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step step.toInt()) {
                drawLine(
                    color = GridLineColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            PausifyHeader(showIcon = false)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                
                Text(
                    "LISTENING",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontSize = 48.sp, // Increased size
                    letterSpacing = 2.sp
                )
                Text(
                    "INPUT ACTIVE • HIGH FIDELITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = PausifyRed,
                    letterSpacing = 1.5.sp,
                    fontSize = 12.sp
                )
                
                Spacer(Modifier.weight(1f))
                
                // Pulsing Mic Icon (Larger)
                Box(contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    
                    Canvas(modifier = Modifier.size(240.dp)) {
                        drawCircle(
                            color = PausifyRed,
                            radius = (size.minDimension / 4) * pulseScale,
                            alpha = pulseAlpha,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp) // Increased size
                            .background(CardBackground, CircleShape)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = PausifyRed,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                Spacer(Modifier.weight(1f))
                
                // Status Section (Larger)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .padding(32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT SESSION", color = PausifyRed, fontSize = 12.sp)
                            Text("DEEP FOCUS", color = Color.White, fontSize = 28.sp)
                        }
                        Text("12:45", color = TextSecondary, fontSize = 24.sp)
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    HorizontalDivider(color = DividerColor)
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(PausifyRed, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text("SYNCING WITH MODULE: VETERAN", color = TextDisabled, fontSize = 12.sp)
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Button(
                        onClick = onEndSession,
                        modifier = Modifier.fillMaxWidth().height(64.dp), // Increased height
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                    ) {
                        Text("END SESSION —", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
