package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MairaCharacterView(
    status: String,
    micRms: Float,
    speakerRms: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MairaIdleAnimation")

    // Side-to-side gentle sway
    val idleSway by infiniteTransition.animateFloat(
        initialValue = -2.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdleSway"
    )

    // Floating vertical bob
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatBob"
    )

    // Breathing rhythm
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathe"
    )

    // Orbit angle for background particles
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Orbit"
    )

    // States: listening, speaking, thinking
    val isListening = status.contains("শুনছি")
    val isThinking = status.contains("ভাবছি")
    val isSpeaking = status.contains("বলছি") || speakerRms > 0.05f

    // Lean in while listening
    val targetLeanAngle = when {
        isListening -> -3.8f
        isThinking -> 4.5f
        else -> 0f
    }
    val animatedLeanAngle by animateFloatAsState(
        targetValue = targetLeanAngle,
        animationSpec = tween(500),
        label = "LeanInAngle"
    )

    val listenScale by animateFloatAsState(
        targetValue = if (isListening) 1.03f else 1.0f,
        animationSpec = tween(500),
        label = "ListenScale"
    )

    // Real audio playback energy enhancement (stronger while speaking)
    val speechScaleY = if (isSpeaking) (speakerRms * 0.16f).coerceIn(0f, 0.18f) else 0f
    val speechScaleX = if (isSpeaking) (speakerRms * 0.06f).coerceIn(0f, 0.08f) else 0f
    val speechSwayMultiplier = if (isSpeaking) 1f + (speakerRms * 1.5f).coerceIn(0f, 1.4f) else 1f
    val activeSway = idleSway * speechSwayMultiplier

    val isDark = isSystemInDarkTheme()
    val waveColor1 = if (isDark) Color(0x339B6BFF) else Color(0x33E53956)
    val waveColor2 = if (isDark) Color(0x226C38FF) else Color(0x22FF758C)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("maira_character_container"),
        contentAlignment = Alignment.Center
    ) {
        // Soft Watercolor Background Waves Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val path1 = Path().apply {
                moveTo(0f, height * 0.42f)
                cubicTo(
                    width * 0.25f, height * (0.32f + 0.04f * sin(orbitAngle * Math.PI / 180).toFloat()),
                    width * 0.75f, height * (0.50f + 0.04f * cos(orbitAngle * Math.PI / 180).toFloat()),
                    width, height * 0.42f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path1, brush = Brush.verticalGradient(listOf(waveColor1, Color.Transparent)))

            val path2 = Path().apply {
                moveTo(0f, height * 0.50f)
                cubicTo(
                    width * 0.3f, height * (0.56f + 0.03f * cos(orbitAngle * Math.PI / 180).toFloat()),
                    width * 0.7f, height * (0.40f + 0.03f * sin(orbitAngle * Math.PI / 180).toFloat()),
                    width, height * 0.50f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path2, brush = Brush.verticalGradient(listOf(waveColor2, Color.Transparent)))

            // Floating soft particles
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = width * 0.35f

            val dot1X = centerX + radius * cos(orbitAngle * Math.PI / 180).toFloat()
            val dot1Y = centerY + (radius * 0.3f) * sin(orbitAngle * Math.PI / 180).toFloat()
            drawCircle(waveColor1.copy(alpha = 0.6f), radius = 12f, center = Offset(dot1X, dot1Y))

            val dot2X = centerX - radius * cos((orbitAngle + 120) * Math.PI / 180).toFloat()
            val dot2Y = centerY - (radius * 0.3f) * sin((orbitAngle + 120) * Math.PI / 180).toFloat()
            drawCircle(waveColor2.copy(alpha = 0.5f), radius = 8f, center = Offset(dot2X, dot2Y))
        }

        // Whole Character drawn as ONE SINGLE LAYER with physics animations
        Image(
            painter = painterResource(id = R.drawable.maira_body),
            contentDescription = "মাইরা অ্যানিমে চরিত্র",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxHeight(0.95f)
                .graphicsLayer {
                    // Pivot at the feet!
                    transformOrigin = TransformOrigin(0.5f, 1.0f)
                    rotationZ = activeSway + animatedLeanAngle
                    translationY = bobOffset
                    scaleX = (breatheScale + speechScaleX) * listenScale
                    scaleY = (breatheScale + speechScaleY) * listenScale
                }
                .testTag("maira_body_image")
        )
    }
}
