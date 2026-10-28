package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MairaLogger
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

    // Log missing layer PNG files once
    LaunchedEffect(Unit) {
        MairaLogger.log(
            MairaLogger.Category.UI,
            MairaLogger.Level.INFO,
            "Missing layer PNG assets for full limb animation: maira_arm_right.png, maira_eyes_closed.png, maira_mouth_small.png, maira_mouth_open.png. Using runtime bitmap region slicing instead."
        )
    }

    // Load original bitmap and slice into 3 regions (Head, Torso, Legs)
    val slicedBitmaps = remember {
        try {
            val original = BitmapFactory.decodeResource(context.resources, R.drawable.maira_body)
            if (original != null && original.height > 10 && original.width > 10) {
                val w = original.width
                val h = original.height

                val headH = (h * 0.33f).toInt()
                val torsoStartY = (h * 0.30f).toInt()
                val torsoH = (h * 0.36f).toInt()
                val legsStartY = (h * 0.63f).toInt()
                val legsH = h - legsStartY

                val headBmp = Bitmap.createBitmap(original, 0, 0, w, headH)
                val torsoBmp = Bitmap.createBitmap(original, 0, torsoStartY, w, torsoH)
                val legsBmp = Bitmap.createBitmap(original, 0, legsStartY, w, legsH)

                Triple(headBmp.asImageBitmap(), torsoBmp.asImageBitmap(), legsBmp.asImageBitmap())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "MairaMultiPartTransition")

    // Whole body float bob
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bob"
    )

    // Torso breathing scale
    val breatheScaleY by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TorsoBreathe"
    )

    // Head subtle sway
    val headSwayAngle by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HeadSway"
    )

    // Leg weight shift
    val legShiftX by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LegShift"
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

    // Status-driven responsive transforms
    val isListening = status.contains("শুনছি")
    val isThinking = status.contains("ভাবছি")
    val isSpeaking = status.contains("বলছি")

    val targetTilt = when {
        isListening -> -4f // Lean in
        isThinking -> 6f   // Pensive tilt
        else -> 0f
    }
    val animatedTilt by animateFloatAsState(
        targetValue = targetTilt,
        animationSpec = tween(600),
        label = "StatusTilt"
    )

    // Speaking reaction for head scale
    val speechMouthScale = 1f + (speakerRms * 0.25f).coerceIn(0f, 0.35f)

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

        // Multi-part character layer box
        if (slicedBitmaps != null) {
            val (head, torso, legs) = slicedBitmaps

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .graphicsLayer {
                        translationY = bobOffset
                        rotationZ = animatedTilt
                    }
            ) {
                // Head Region with independent tilt & audio reaction scale
                Image(
                    bitmap = head,
                    contentDescription = "মাইরার মাথা",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(0.33f)
                        .graphicsLayer {
                            rotationZ = headSwayAngle
                            scaleY = if (isSpeaking) speechMouthScale else 1f
                            scaleX = if (isSpeaking) 1f + (speakerRms * 0.08f) else 1f
                        }
                )

                // Torso Region with independent breathing scale
                Image(
                    bitmap = torso,
                    contentDescription = "মাইরার শরীর",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(0.36f)
                        .graphicsLayer {
                            scaleY = breatheScaleY
                            scaleX = 1f + (breatheScaleY - 1f) * 0.5f
                        }
                )

                // Legs Region with weight shift translation
                Image(
                    bitmap = legs,
                    contentDescription = "মাইরার পা",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(0.31f)
                        .graphicsLayer {
                            translationX = legShiftX
                        }
                )
            }
        } else {
            // Fallback to whole body image
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.maira_body),
                contentDescription = "মাইরা অ্যানিমে চরিত্র",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .graphicsLayer {
                        translationY = bobOffset
                        rotationZ = animatedTilt
                    }
                    .testTag("maira_body_image")
            )
        }
    }
}
