package com.rohit.voicepause.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.hypot

/**
 * A modifier that applies a smooth, slow gradient ripple transition in both directions.
 * Optimized to prevent the "stuck" feeling at the edges by using a dynamic hardening gradient.
 */
fun Modifier.gradientRippleBackground(
    targetState: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    interactionSource: InteractionSource,
    shape: Shape = RectangleShape
) = this.composed {
    var lastPressPosition by remember { mutableStateOf(Offset.Unspecified) }
    
    var baseColor by remember { mutableStateOf(if (targetState) activeColor else inactiveColor) }
    var rippleColor by remember { mutableStateOf(baseColor) }
    
    val progress = remember { Animatable(0f) }
    var isFirstRun by remember { mutableStateOf(true) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                lastPressPosition = interaction.pressPosition
            }
        }
    }

    LaunchedEffect(targetState) {
        val nextTargetColor = if (targetState) activeColor else inactiveColor
        
        if (isFirstRun) {
            baseColor = nextTargetColor
            rippleColor = nextTargetColor
            isFirstRun = false
            return@LaunchedEffect
        }

        if (nextTargetColor != rippleColor) {
            baseColor = rippleColor 
            rippleColor = nextTargetColor
            
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1200, // Increased speed from 2000ms to 1200ms
                    easing = FastOutSlowInEasing // More dynamic feel for faster speed
                )
            )
            
            baseColor = rippleColor
            progress.snapTo(0f)
        }
    }

    this.drawWithCache {
        val path = Path()
        onDrawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            path.reset()
            path.addOutline(outline)

            clipPath(path) {
                drawRect(baseColor)

                if (progress.value > 0f) {
                    val center = if (lastPressPosition.isSpecified) lastPressPosition else Offset(size.width / 2f, size.height / 2f)
                    
                    // Controlled max radius to prevent the tail from lingering too long
                    val maxRadius = hypot(size.width, size.height) * 1.6f
                    val currentRadius = maxRadius * progress.value

                    // DYNAMIC HARDENING: 
                    // The "solid" core of the ripple grows as it expands.
                    val solidFraction = 0.1f + (progress.value * 0.65f)
                    
                    val brush = Brush.radialGradient(
                        0.00f to rippleColor,
                        solidFraction to rippleColor, 
                        (solidFraction + 0.2f).coerceAtMost(1.0f) to rippleColor.copy(alpha = 0.5f),
                        1.00f to Color.Transparent,
                        center = center,
                        radius = currentRadius.coerceAtLeast(1f)
                    )
                    
                    drawCircle(
                        brush = brush,
                        radius = currentRadius,
                        center = center
                    )
                    
                    // Leading edge aura that fades out
                    drawCircle(
                        color = Color.White.copy(alpha = 0.02f * (1f - progress.value)),
                        radius = currentRadius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
            drawContent()
        }
    }
}
