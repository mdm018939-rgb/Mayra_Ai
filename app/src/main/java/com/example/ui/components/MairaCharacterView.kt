package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "MairaCharacterTransition")

    // Breathing and Floating Bob
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bob"
    )

    // Breathing scale
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathe"
    )

    // Orbiting dot angle
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Orbit"
    )

    // Responsive tilts based on status
    val isListening = status.contains("শুনছি")
    val isThinking = status.contains("ভাবছি")
    val isSpeaking = status.contains("বলছি")

    val targetTilt = when {
        isListening -> -4f // Lean in
        isThinking -> 5f  // Head tilt
        else -> 0f
    }
    val animatedTilt by animateFloatAsState(
        targetValue = targetTilt,
        animationSpec = tween(600),
        label = "Tilt"
    )

    val mouthScale = 1f + (speakerRms * 0.4f).coerceIn(0f, 0.5f)

    val isDark = isSystemInDarkTheme()
    val waveColor1 = if (isDark) Color(0x339B6BFF) else Color(0x33E53956)
    val waveColor2 = if (isDark) Color(0x226C38FF) else Color(0x22FF758C)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("maira_character_container"),
        contentAlignment = Alignment.Center
    ) {
        // Background Watercolor Wave Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val path1 = Path().apply {
                moveTo(0f, height * 0.4f)
                cubicTo(
                    width * 0.25f, height * (0.3f + 0.05f * sin(orbitAngle * Math.PI / 180).toFloat()),
                    width * 0.75f, height * (0.5f + 0.05f * cos(orbitAngle * Math.PI / 180).toFloat()),
                    width, height * 0.4f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path1, brush = Brush.verticalGradient(listOf(waveColor1, Color.Transparent)))

            val path2 = Path().apply {
                moveTo(0f, height * 0.48f)
                cubicTo(
                    width * 0.3f, height * (0.55f + 0.04f * cos(orbitAngle * Math.PI / 180).toFloat()),
                    width * 0.7f, height * (0.38f + 0.04f * sin(orbitAngle * Math.PI / 180).toFloat()),
                    width, height * 0.48f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path2, brush = Brush.verticalGradient(listOf(waveColor2, Color.Transparent)))

            // Orbiting dots
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

        // Full Body Character Image
        Image(
            painter = painterResource(id = R.drawable.maira_body),
            contentDescription = "মাইরা অ্যানিমে চরিত্র",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .graphicsLayer {
                    translationY = bobOffset
                    scaleX = breatheScale
                    scaleY = breatheScale * if (isSpeaking) mouthScale else 1f
                    rotationZ = animatedTilt
                }
                .testTag("maira_body_image")
        )
    }
}
