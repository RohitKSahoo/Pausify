package com.rohit.voicepause.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A custom pixelated ripple indication that creates a "Google Pixel" style sparkly effect.
 */
object PixelRipple : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return PixelRippleNode(interactionSource)
    }

    override fun hashCode(): Int = -1
    override fun equals(other: Any?) = other === this
}

private class PixelRippleNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {
    
    private var pressPosition: Offset = Offset.Zero
    private val alpha = Animatable(0f)
    private val progress = Animatable(0f)
    
    // Sparkle particles state
    private val sparkles = List(30) { 
        Sparkle(
            angle = Random.nextFloat() * 360f,
            distanceMultiplier = 0.2f + Random.nextFloat() * 0.8f,
            size = 2f + Random.nextFloat() * 4f
        )
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressPosition = interaction.pressPosition
                        launch {
                            progress.snapTo(0f)
                            alpha.snapTo(0.6f)
                            progress.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        alpha.animateTo(0f, tween(300))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        
        val currentAlpha = alpha.value
        val currentProgress = progress.value
        
        if (currentAlpha > 0f) {
            val maxRadius = size.maxDimension / 2f
            
            sparkles.forEach { sparkle ->
                val r = currentProgress * maxRadius * sparkle.distanceMultiplier
                val x = pressPosition.x + r * kotlin.math.cos(Math.toRadians(sparkle.angle.toDouble())).toFloat()
                val y = pressPosition.y + r * kotlin.math.sin(Math.toRadians(sparkle.angle.toDouble())).toFloat()
                
                // Pixelated look: draw small rects
                val sparkleAlpha = currentAlpha * (1f - currentProgress)
                drawRect(
                    color = Color.White.copy(alpha = sparkleAlpha),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(sparkle.size, sparkle.size)
                )
            }
        }
    }
}

private data class Sparkle(
    val angle: Float,
    val distanceMultiplier: Float,
    val size: Float
)
