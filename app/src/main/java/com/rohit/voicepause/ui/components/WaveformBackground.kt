package com.rohit.voicepause.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rohit.voicepause.ui.theme.*

@Composable
fun AnimatedWaveformBackground(
    isActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 3000 else 6000,
                easing = LinearEasing
            )
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height * 0.5f

        val amplitude = if (isActive) 120f else 60f
        val frequency = 1.5f

        val path = Path().apply {
            moveTo(0f, centerY)
            for (x in 0..width.toInt() step 12) {
                val y = centerY +
                        amplitude *
                        kotlin.math.sin(
                            (x / width) * frequency * Math.PI * 2 + phase
                        ).toFloat()
                lineTo(x.toFloat(), y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(
                    NeonGreen,
                    NeonTeal,
                    NeonBlue,
                    NeonIndigo
                )
            ),
            style = Stroke(width = 6f)
        )
    }
}
