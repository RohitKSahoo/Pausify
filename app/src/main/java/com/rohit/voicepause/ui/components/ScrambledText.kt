package com.rohit.voicepause.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun ScrambledText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    durationMillis: Int = 800
) {
    var displayText by remember { mutableStateOf(text) }
    val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%"

    LaunchedEffect(text) {
        val targetText = text
        val frames = 15
        val delayPerFrame = (durationMillis / frames).toLong()

        repeat(frames) { frame ->
            val scrambled = StringBuilder()
            for (i in targetText.indices) {
                // As we progress through frames, more characters become correct
                val threshold = frame.toFloat() / frames
                if (Random.nextFloat() < threshold) {
                    scrambled.append(targetText[i])
                } else {
                    scrambled.append(characters[Random.nextInt(characters.length)])
                }
            }
            displayText = scrambled.toString()
            delay(delayPerFrame)
        }
        displayText = targetText
    }

    Text(
        text = displayText,
        modifier = modifier,
        style = style,
        color = color,
        fontSize = fontSize
    )
}
