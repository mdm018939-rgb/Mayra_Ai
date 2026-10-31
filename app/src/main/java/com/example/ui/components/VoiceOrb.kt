package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        OrbState.SPEAKING -> 2200
        OrbState.LISTENING -> 3000
        OrbState.SUCCESS, OrbState.ERROR -> 2800
        OrbState.IDLE -> 4500
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

            // 1. Wide Outer Luminous Background Aura
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

            // 2. Floating Outer Luminous Particles
            val numParticles = 12
            for (i in 0 until numParticles) {
                val particleAngle = Math.toRadians((orbitRotation * 1.2 + i * (360 / numParticles)).toDouble())
                val particleDistance = baseRadius * (1.28f + 0.12f * sin(particleAngle * 2.0).toFloat())
                val px = centerPt.x + particleDistance * cos(particleAngle).toFloat()
                val py = centerPt.y + particleDistance * sin(particleAngle).toFloat()
                val pRadius = 2.5.dp.toPx() * (0.8f + 0.4f * sin(particleAngle).toFloat())

                drawCircle(
                    color = if (i % 2 == 0) primaryColor.copy(alpha = 0.85f) else secondaryColor.copy(alpha = 0.85f),
                    center = Offset(px, py),
                    radius = pRadius
                )
            }

            // --- 3D PLANETARY ORBIT SYSTEM ---
            // Ring 1 Configuration: Tilted at -28 degrees
            val tiltAngle1 = -28f
            val ringSize1 = Size(baseRadius * 2.70f, baseRadius * 1.40f)
            val ringOffset1 = Offset(centerPt.x - ringSize1.width / 2f, centerPt.y - ringSize1.height / 2f)
            val phi1 = orbitRotation % 360f
            val radPhi1 = Math.toRadians(phi1.toDouble())
            val radTilt1 = Math.toRadians(tiltAngle1.toDouble())
            val x0_1 = (ringSize1.width / 2f) * cos(radPhi1).toFloat()
            val y0_1 = (ringSize1.height / 2f) * sin(radPhi1).toFloat()
            val planetPos1 = Offset(
                centerPt.x + x0_1 * cos(radTilt1).toFloat() - y0_1 * sin(radTilt1).toFloat(),
                centerPt.y + x0_1 * sin(radTilt1).toFloat() + y0_1 * cos(radTilt1).toFloat()
            )
            val isPlanet1Front = sin(radPhi1) > 0

            // Ring 2 Configuration: Tilted at +42 degrees
            val tiltAngle2 = 42f
            val ringSize2 = Size(baseRadius * 2.50f, baseRadius * 1.25f)
            val ringOffset2 = Offset(centerPt.x - ringSize2.width / 2f, centerPt.y - ringSize2.height / 2f)
            val phi2 = (orbitRotation * 1.25f + 120f) % 360f
            val radPhi2 = Math.toRadians(phi2.toDouble())
            val radTilt2 = Math.toRadians(tiltAngle2.toDouble())
            val x0_2 = (ringSize2.width / 2f) * cos(radPhi2).toFloat()
            val y0_2 = (ringSize2.height / 2f) * sin(radPhi2).toFloat()
            val planetPos2 = Offset(
                centerPt.x + x0_2 * cos(radTilt2).toFloat() - y0_2 * sin(radTilt2).toFloat(),
                centerPt.y + x0_2 * sin(radTilt2).toFloat() + y0_2 * cos(radTilt2).toFloat()
            )
            val isPlanet2Front = sin(radPhi2) > 0

            val ringBrush1 = Brush.sweepGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.95f),
                    secondaryColor.copy(alpha = 0.35f),
                    primaryColor.copy(alpha = 0.85f),
                    secondaryColor.copy(alpha = 0.25f),
                    primaryColor.copy(alpha = 0.95f)
                ),
                center = centerPt
            )

            val ringBrush2 = Brush.sweepGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.90f),
                    primaryColor.copy(alpha = 0.30f),
                    secondaryColor.copy(alpha = 0.80f),
                    primaryColor.copy(alpha = 0.20f),
                    secondaryColor.copy(alpha = 0.90f)
                ),
                center = centerPt
            )

            // LAYER 3A: Draw REAR 3D Orbit Arcs (Passing BEHIND the Core Sphere)
            rotate(degrees = tiltAngle1, pivot = centerPt) {
                drawArc(
                    brush = ringBrush1,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ringOffset1,
                    size = ringSize1,
                    style = Stroke(width = 2.4.dp.toPx())
                )
            }

            rotate(degrees = tiltAngle2, pivot = centerPt) {
                drawArc(
                    brush = ringBrush2,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ringOffset2,
                    size = ringSize2,
                    style = Stroke(width = 2.0.dp.toPx())
                )
            }

            // Draw Planet 1 if on rear arc
            if (!isPlanet1Front) {
                drawCircle(color = primaryColor.copy(alpha = 0.5f), center = planetPos1, radius = 5.5.dp.toPx())
                drawCircle(color = Color.White.copy(alpha = 0.7f), center = planetPos1, radius = 3.0.dp.toPx())
            }

            // Draw Planet 2 if on rear arc
            if (!isPlanet2Front) {
                drawCircle(color = secondaryColor.copy(alpha = 0.5f), center = planetPos2, radius = 5.0.dp.toPx())
                drawCircle(color = Color.White.copy(alpha = 0.7f), center = planetPos2, radius = 2.5.dp.toPx())
            }

            // LAYER 3B: Pure 3D Volumetric Glass Core Sphere (Occludes Rear Arcs)
            val highlightOffset = Offset(centerPt.x - baseRadius * 0.28f, centerPt.y - baseRadius * 0.28f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        primaryColor,
                        secondaryColor,
                        baseColor
                    ),
                    center = highlightOffset,
                    radius = baseRadius * 1.35f
                ),
                center = centerPt,
                radius = baseRadius
            )

            // LAYER 3C: Draw FRONT 3D Orbit Arcs (Passing IN FRONT OF the Core Sphere)
            rotate(degrees = tiltAngle1, pivot = centerPt) {
                drawArc(
                    brush = ringBrush1,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ringOffset1,
                    size = ringSize1,
                    style = Stroke(width = 2.4.dp.toPx())
                )
            }

            rotate(degrees = tiltAngle2, pivot = centerPt) {
                drawArc(
                    brush = ringBrush2,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = ringOffset2,
                    size = ringSize2,
                    style = Stroke(width = 2.0.dp.toPx())
                )
            }

            // Draw Planet 1 if on front arc
            if (isPlanet1Front) {
                drawCircle(color = primaryColor, center = planetPos1, radius = 7.5.dp.toPx())
                drawCircle(color = Color.White, center = planetPos1, radius = 4.0.dp.toPx())
            }

            // Draw Planet 2 if on front arc
            if (isPlanet2Front) {
                drawCircle(color = secondaryColor, center = planetPos2, radius = 6.5.dp.toPx())
                drawCircle(color = Color.White, center = planetPos2, radius = 3.5.dp.toPx())
            }
        }
    }
}
