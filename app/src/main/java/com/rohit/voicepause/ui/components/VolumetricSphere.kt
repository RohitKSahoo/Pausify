package com.rohit.voicepause.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rohit.voicepause.ui.theme.PausifyRed
import kotlin.math.*

@Composable
fun VolumetricSphere(
    isActive: Boolean,
    isMusicPlaying: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sphere")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        // Increased radius from 0.3f to 0.45f
        val baseRadius = min(size.width, size.height) * 0.45f
        
        val pointCount = 200 // Increased point count for better density at larger size
        val phi = PI * (3.0 - sqrt(5.0)) 

        for (i in 0 until pointCount) {
            val y = 1f - (i / (pointCount - 1f)) * 2f
            val currentRadius = sqrt(1f - y * y)

            val theta = (phi * i + (rotation * PI / 180f)).toDouble()

            val x = cos(theta).toFloat() * currentRadius
            val z = sin(theta).toFloat() * currentRadius

            val depthScale = (z + 2f) / 3f
            
            val reaction = if (isActive && isMusicPlaying) {
                (sin(theta * 5.0 + rotation * 0.1).toFloat() * 0.25f)
            } else {
                (sin(theta * 2.0 + bounce * 5.0).toFloat() * 0.05f)
            }
            
            val finalRadius = baseRadius * (1f + reaction)

            val drawX = centerX + x * finalRadius * depthScale
            val drawY = centerY + y * finalRadius * depthScale

            drawCircle(
                color = if (i % 3 == 0) PausifyRed else Color.White,
                radius = 2.5.dp.toPx() * depthScale, // Slightly larger dots
                center = Offset(drawX, drawY),
                alpha = (0.3f + (depthScale * 0.7f)).coerceIn(0f, 1f)
            )
        }
    }
}
