package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MairaCharacterView
import java.util.Calendar

@Composable
fun HomeScreen(
    status: String,
    micRms: Float,
    speakerRms: Float,
    userNickname: String,
    isSessionActive: Boolean,
    onNavigateToConnections: () -> Unit,
    hasKeys: Boolean,
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "শুভ সকাল"
            in 12..15 -> "শুভ দুপুর"
            in 16..17 -> "শুভ বিকেল"
            in 18..21 -> "শুভ সন্ধ্যা"
            else -> "শুভ রাত্রি"
        }
    }

    val nameText = if (userNickname.isNotBlank()) "$userNickname ❤️" else "❤️"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("home_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "মাইরা",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "তোমার ব্যক্তিগত এআই",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (!hasKeys) {
                Button(
                    onClick = onNavigateToConnections,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("home_key_button")
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("key দাও", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Character View Avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            MairaCharacterView(
                status = status,
                micRms = micRms,
                speakerRms = speakerRms,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Greeting & Real-time Voice Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$greeting, $nameText",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Voice Orb (Replacing 15 bar lines)
        VoiceOrb(
            status = status,
            micRms = micRms,
            speakerRms = speakerRms,
            isSessionActive = isSessionActive,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom bar
    }
}

@Composable
fun VoiceOrb(
    status: String,
    micRms: Float,
    speakerRms: Float,
    isSessionActive: Boolean,
    modifier: Modifier = Modifier
) {
    val isMairaSpeaking = speakerRms > 0.05f || status.contains("বলছি") || status.contains("কথা")
    val isUserSpeaking = micRms > 0.03f || (isSessionActive && !isMairaSpeaking && (status.contains("শুনছি") || status.contains("বলো")))

    val infiniteTransition = rememberInfiniteTransition(label = "orb_infinite")

    // Idle breathing (slow, gentle expansion)
    val idleScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )

    // Speaking pulse (rhythmic, higher frequency)
    val speakingPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_pulse"
    )

    // Listening pulse
    val listeningPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listening_pulse"
    )

    val targetScale = when {
        isMairaSpeaking -> speakingPulse
        isUserSpeaking -> {
            val micDynamic = (1.0f + micRms * 1.5f).coerceIn(1.0f, 1.6f)
            micDynamic * listeningPulse
        }
        else -> idleScale
    }

    val targetGlowAlpha = when {
        isMairaSpeaking -> 0.70f
        isUserSpeaking -> (0.40f + (micRms * 0.6f)).coerceIn(0.40f, 0.85f)
        else -> 0.28f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "animated_orb_scale"
    )

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = targetGlowAlpha,
        animationSpec = tween(300),
        label = "animated_orb_glow"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val coreGradientColors = remember(isMairaSpeaking, isUserSpeaking, primaryColor) {
        when {
            isUserSpeaking -> listOf(
                Color.White.copy(alpha = 0.95f),
                secondaryColor,
                primaryColor
            )
            isMairaSpeaking -> listOf(
                Color.White.copy(alpha = 0.95f),
                primaryColor,
                tertiaryColor
            )
            else -> listOf(
                Color.White.copy(alpha = 0.9f),
                primaryColor.copy(alpha = 0.85f),
                primaryColor
            )
        }
    }

    Canvas(
        modifier = modifier
            .size(150.dp)
            .testTag("voice_orb")
    ) {
        val centerPt = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.minDimension / 3.4f) * animatedScale

        // Outer Glow 2 (Wide transparent aura)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = animatedGlowAlpha * 0.35f),
                    primaryColor.copy(alpha = animatedGlowAlpha * 0.08f),
                    Color.Transparent
                ),
                center = centerPt,
                radius = baseRadius * 1.85f
            ),
            center = centerPt,
            radius = baseRadius * 1.85f
        )

        // Outer Glow 1 (Intense inner aura)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = animatedGlowAlpha * 0.75f),
                    Color.Transparent
                ),
                center = centerPt,
                radius = baseRadius * 1.35f
            ),
            center = centerPt,
            radius = baseRadius * 1.35f
        )

        // Core Glowing Orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = coreGradientColors,
                center = Offset(centerPt.x - baseRadius * 0.22f, centerPt.y - baseRadius * 0.22f),
                radius = baseRadius * 1.25f
            ),
            center = centerPt,
            radius = baseRadius
        )
    }
}
