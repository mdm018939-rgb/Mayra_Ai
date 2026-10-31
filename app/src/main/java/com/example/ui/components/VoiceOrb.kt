package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    SUCCESS,
    ERROR
}

@Composable
fun VoiceOrb(
    status: String,
    micRms: Float,
    speakerRms: Float,
    isSessionActive: Boolean,
    lastActionResult: Pair<String, Long>?,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 160.dp
) {
    val currentTime = System.currentTimeMillis()
    val isRecentResult = lastActionResult != null && (currentTime - lastActionResult.second < 2200L)

    val orbState = remember(status, micRms, speakerRms, isSessionActive, isRecentResult, lastActionResult) {
        when {
            isRecentResult && lastActionResult?.first == "SUCCESS" -> OrbState.SUCCESS
            isRecentResult && lastActionResult?.first == "ERROR" -> OrbState.ERROR
            status.contains("ভাবছি") || status.contains("সংযুক্ত হচ্ছে") -> OrbState.THINKING
            speakerRms > 0.02f || status.contains("বলছি") -> OrbState.SPEAKING
            micRms > 0.02f || (isSessionActive && (status.contains("শুনছি") || status.contains("বলো"))) -> OrbState.LISTENING
            status.contains("বিচ্ছিন্ন") || status.contains("ব্যর্থ") || status.contains("ভুল") -> OrbState.ERROR
            else -> OrbState.IDLE
        }
    }

    // Colors per state
    val targetPrimaryColor = when (orbState) {
        OrbState.IDLE -> Color(0xFF7C4DFF)       // Purple
        OrbState.LISTENING -> Color(0xFF00E5FF)  // Bright Cyan
        OrbState.THINKING -> Color(0xFFD500F9)   // Violet / Deep Magenta
        OrbState.SPEAKING -> Color(0xFFFF4081)   // Hot Pink / Magenta
        OrbState.SUCCESS -> Color(0xFF00E676)    // Emerald Green
        OrbState.ERROR -> Color(0xFFFF3D00)      // Flame Red / Orange
    }

    val targetSecondaryColor = when (orbState) {
        OrbState.IDLE -> Color(0xFF40C4FF)       // Light Blue
        OrbState.LISTENING -> Color(0xFF2979FF)  // Bright Blue
        OrbState.THINKING -> Color(0xFF651FFF)   // Deep Purple
        OrbState.SPEAKING -> Color(0xFFE040FB)   // Purple Magenta
        OrbState.SUCCESS -> Color(0xFF00B0FF)    // Teal Cyan
        OrbState.ERROR -> Color(0xFFFF9100)      // Amber Orange
    }

    val targetBaseColor = when (orbState) {
        OrbState.IDLE -> Color(0xFF1E003E)
        OrbState.LISTENING -> Color(0xFF001A4D)
        OrbState.THINKING -> Color(0xFF14002E)
        OrbState.SPEAKING -> Color(0xFF4A0033)
        OrbState.SUCCESS -> Color(0xFF003314)
        OrbState.ERROR -> Color(0xFF3E0000)
    }

    val primaryColor by animateColorAsState(targetPrimaryColor, tween(400), label = "orb_primary")
    val secondaryColor by animateColorAsState(targetSecondaryColor, tween(400), label = "orb_secondary")
    val baseColor by animateColorAsState(targetBaseColor, tween(400), label = "orb_base")

    // Rotation speed based on state
    val orbitDuration = when (orbState) {
        OrbState.THINKING -> 1200
        OrbState.SPEAKING -> 2400
        OrbState.LISTENING -> 3200
        OrbState.SUCCESS, OrbState.ERROR -> 3000
        OrbState.IDLE -> 5000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb_transition")

    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(orbitDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    val swirlRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((orbitDuration * 1.4f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swirl_rotation"
    )

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // Dynamic Scale matching Audio RMS
    val dynamicTargetScale = when (orbState) {
        OrbState.LISTENING -> (1.0f + (micRms * 0.75f)).coerceIn(1.0f, 1.6f)
        OrbState.SPEAKING -> (1.0f + (speakerRms * 0.85f)).coerceIn(1.0f, 1.7f)
        OrbState.THINKING -> 1.08f
        OrbState.SUCCESS -> 1.15f
        OrbState.ERROR -> 1.12f
        OrbState.IDLE -> idlePulse
    }

    val animatedScale by animateFloatAsState(
        targetValue = dynamicTargetScale,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "animated_scale"
    )

    // Icon animation for SUCCESS / ERROR overlay
    val iconAlpha by animateFloatAsState(
        targetValue = if (orbState == OrbState.SUCCESS || orbState == OrbState.ERROR) 1f else 0f,
        animationSpec = tween(300),
        label = "icon_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(orbSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOrbClick
            )
            .testTag("voice_orb")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPt = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 3.4f) * animatedScale

            // 1. Wide Outer Luminous Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.45f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = centerPt,
                    radius = baseRadius * 1.85f
                ),
                center = centerPt,
                radius = baseRadius * 1.85f
            )

            // 2. Floating Luminous Particles
            val numParticles = 12
            for (i in 0 until numParticles) {
                val particleAngle = Math.toRadians((orbitRotation * 1.2 + i * (360 / numParticles)).toDouble())
                val particleDistance = baseRadius * (1.25f + 0.15f * sin(particleAngle * 2.0).toFloat())
                val px = centerPt.x + particleDistance * cos(particleAngle).toFloat()
                val py = centerPt.y + particleDistance * sin(particleAngle).toFloat()
                val pRadius = 2.5.dp.toPx() * (0.8f + 0.4f * sin(particleAngle).toFloat())

                drawCircle(
                    color = if (i % 2 == 0) primaryColor.copy(alpha = 0.85f) else secondaryColor.copy(alpha = 0.85f),
                    center = Offset(px, py),
                    radius = pRadius
                )
            }

            // 3. Tilted Orbit Ring with Glowing Nodes
            rotate(degrees = orbitRotation, pivot = centerPt) {
                val ringRect = Size(baseRadius * 2.6f, baseRadius * 1.5f)
                val ringOffset = Offset(centerPt.x - ringRect.width / 2f, centerPt.y - ringRect.height / 2f)

                // Outer Orbit Ring Line
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.9f),
                            secondaryColor.copy(alpha = 0.2f),
                            primaryColor.copy(alpha = 0.8f),
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.9f)
                        ),
                        center = centerPt
                    ),
                    topLeft = ringOffset,
                    size = ringRect,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Satellite Glowing Nodes on Orbit Ring
                val nodeAngle1 = Math.toRadians(0.0)
                val nodeX1 = centerPt.x + (ringRect.width / 2f) * cos(nodeAngle1).toFloat()
                val nodeY1 = centerPt.y + (ringRect.height / 2f) * sin(nodeAngle1).toFloat()
                drawCircle(
                    color = Color.White,
                    center = Offset(nodeX1, nodeY1),
                    radius = 4.dp.toPx()
                )
                drawCircle(
                    color = primaryColor,
                    center = Offset(nodeX1, nodeY1),
                    radius = 7.dp.toPx()
                )

                val nodeAngle2 = Math.toRadians(180.0)
                val nodeX2 = centerPt.x + (ringRect.width / 2f) * cos(nodeAngle2).toFloat()
                val nodeY2 = centerPt.y + (ringRect.height / 2f) * sin(nodeAngle2).toFloat()
                drawCircle(
                    color = secondaryColor,
                    center = Offset(nodeX2, nodeY2),
                    radius = 4.5.dp.toPx()
                )
            }

            // 4. 3D Glass Sphere Core
            val highlightOffset = Offset(centerPt.x - baseRadius * 0.3f, centerPt.y - baseRadius * 0.3f)

            // Deep Base Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryColor,
                        secondaryColor,
                        baseColor
                    ),
                    center = highlightOffset,
                    radius = baseRadius * 1.3f
                ),
                center = centerPt,
                radius = baseRadius
            )

            // 5. Internal Swirling Light Waves inside Glass Sphere
            rotate(degrees = swirlRotation, pivot = centerPt) {
                val swirlPath = Path().apply {
                    moveTo(centerPt.x - baseRadius * 0.7f, centerPt.y)
                    cubicTo(
                        centerPt.x - baseRadius * 0.3f, centerPt.y - baseRadius * 0.8f,
                        centerPt.x + baseRadius * 0.3f, centerPt.y + baseRadius * 0.8f,
                        centerPt.x + baseRadius * 0.7f, centerPt.y
                    )
                }

                drawPath(
                    path = swirlPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.7f),
                            primaryColor.copy(alpha = 0.8f),
                            secondaryColor.copy(alpha = 0.4f)
                        )
                    ),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Secondary inner energy arc
                val swirlPath2 = Path().apply {
                    moveTo(centerPt.x, centerPt.y - baseRadius * 0.6f)
                    cubicTo(
                        centerPt.x + baseRadius * 0.7f, centerPt.y - baseRadius * 0.2f,
                        centerPt.x - baseRadius * 0.7f, centerPt.y + baseRadius * 0.2f,
                        centerPt.x, centerPt.y + baseRadius * 0.6f
                    )
                }

                drawPath(
                    path = swirlPath2,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.6f),
                            primaryColor.copy(alpha = 0.3f)
                        )
                    ),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 6. 3D Glass Crescent Specular Highlight (Lens Reflection)
            val glassShineRect = Size(baseRadius * 1.4f, baseRadius * 0.7f)
            val glassShineOffset = Offset(centerPt.x - baseRadius * 0.7f, centerPt.y - baseRadius * 0.85f)
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = glassShineOffset,
                size = glassShineRect,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 7. Overlay Check / Warning Icon on Success or Error
            if (iconAlpha > 0.01f) {
                val iconRadius = baseRadius * 0.45f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f * iconAlpha),
                    center = centerPt,
                    radius = iconRadius
                )

                if (orbState == OrbState.SUCCESS) {
                    // Draw Checkmark Path
                    val checkPath = Path().apply {
                        moveTo(centerPt.x - iconRadius * 0.4f, centerPt.y)
                        lineTo(centerPt.x - iconRadius * 0.1f, centerPt.y + iconRadius * 0.35f)
                        lineTo(centerPt.x + iconRadius * 0.45f, centerPt.y - iconRadius * 0.3f)
                    }
                    drawPath(
                        path = checkPath,
                        color = Color.White.copy(alpha = iconAlpha),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else if (orbState == OrbState.ERROR) {
                    // Draw Exclamation Warning Icon
                    drawLine(
                        color = Color.White.copy(alpha = iconAlpha),
                        start = Offset(centerPt.x, centerPt.y - iconRadius * 0.4f),
                        end = Offset(centerPt.x, centerPt.y + iconRadius * 0.1f),
                        strokeWidth = 4.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = iconAlpha),
                        center = Offset(centerPt.x, centerPt.y + iconRadius * 0.35f),
                        radius = 2.5.dp.toPx()
                    )
                }
            }
        }
    }
}
