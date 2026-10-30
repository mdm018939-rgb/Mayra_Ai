package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.data.ThemeLoadingManager
import kotlinx.coroutines.delay

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightCardBg,
    outline = LightCardBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCardBg,
    outline = DarkCardBorder
)

@Composable
fun MairaTheme(
    themeMode: String = "light", // "light", "dark", "system"
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val targetColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val animSpec = tween<Color>(durationMillis = 350)

    val animatedColorScheme = ColorScheme(
        primary = animateColorAsState(targetColorScheme.primary, animationSpec = animSpec, label = "primary").value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, animationSpec = animSpec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(targetColorScheme.primaryContainer, animationSpec = animSpec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec = animSpec, label = "onPrimaryContainer").value,
        inversePrimary = animateColorAsState(targetColorScheme.inversePrimary, animationSpec = animSpec, label = "inversePrimary").value,
        secondary = animateColorAsState(targetColorScheme.secondary, animationSpec = animSpec, label = "secondary").value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, animationSpec = animSpec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(targetColorScheme.secondaryContainer, animationSpec = animSpec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec = animSpec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(targetColorScheme.tertiary, animationSpec = animSpec, label = "tertiary").value,
        onTertiary = animateColorAsState(targetColorScheme.onTertiary, animationSpec = animSpec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec = animSpec, label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec = animSpec, label = "onTertiaryContainer").value,
        background = animateColorAsState(targetColorScheme.background, animationSpec = animSpec, label = "background").value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, animationSpec = animSpec, label = "onBackground").value,
        surface = animateColorAsState(targetColorScheme.surface, animationSpec = animSpec, label = "surface").value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, animationSpec = animSpec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, animationSpec = animSpec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec = animSpec, label = "onSurfaceVariant").value,
        surfaceTint = animateColorAsState(targetColorScheme.surfaceTint, animationSpec = animSpec, label = "surfaceTint").value,
        inverseSurface = animateColorAsState(targetColorScheme.inverseSurface, animationSpec = animSpec, label = "inverseSurface").value,
        inverseOnSurface = animateColorAsState(targetColorScheme.inverseOnSurface, animationSpec = animSpec, label = "inverseOnSurface").value,
        error = animateColorAsState(targetColorScheme.error, animationSpec = animSpec, label = "error").value,
        onError = animateColorAsState(targetColorScheme.onError, animationSpec = animSpec, label = "onError").value,
        errorContainer = animateColorAsState(targetColorScheme.errorContainer, animationSpec = animSpec, label = "errorContainer").value,
        onErrorContainer = animateColorAsState(targetColorScheme.onErrorContainer, animationSpec = animSpec, label = "onErrorContainer").value,
        outline = animateColorAsState(targetColorScheme.outline, animationSpec = animSpec, label = "outline").value,
        outlineVariant = animateColorAsState(targetColorScheme.outlineVariant, animationSpec = animSpec, label = "outlineVariant").value,
        scrim = animateColorAsState(targetColorScheme.scrim, animationSpec = animSpec, label = "scrim").value
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = animatedColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography
    ) {
        ThemeLoadingOverlayContainer(themeMode = themeMode) {
            content()
        }
    }
}

@Composable
fun ThemeLoadingOverlayContainer(
    themeMode: String,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val isThemeChanging by ThemeLoadingManager.isLoading.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = isThemeChanging,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(280))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "theme_spin_transition")
                        val spinAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "spin_angle_float"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(38.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = primaryColor,
                                strokeWidth = 3.dp
                            )
                            Icon(
                                imageVector = if (isDark) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                                contentDescription = null,
                                tint = if (isDark) primaryColor else Color(0xFFFFB300),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = spinAngle }
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = if (isDark) "গাঢ় থিমে পরিবর্তন হচ্ছে..." else "হালকা থিমে পরিবর্তন হচ্ছে...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
