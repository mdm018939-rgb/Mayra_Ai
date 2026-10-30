package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.WindowCompat
import kotlin.math.hypot

object ThemeTouchManager {
    var lastTouchPoint: Offset? = null
}

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
        CircularRevealThemeContainer(themeMode = themeMode) {
            content()
        }
    }
}

@Composable
fun CircularRevealThemeContainer(
    themeMode: String,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    var rootSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    var previousDarkState by remember { mutableStateOf<Boolean?>(null) }
    var animatingPreviousBg by remember { mutableStateOf<Color?>(null) }
    var animTouchPoint by remember { mutableStateOf<Offset?>(null) }

    val animProgress = remember { Animatable(1f) }

    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isDark) {
        if (previousDarkState != null && previousDarkState != isDark) {
            val touch = ThemeTouchManager.lastTouchPoint
                ?: Offset(rootSize.width / 2f, rootSize.height / 2f)
            animTouchPoint = touch
            animatingPreviousBg = if (previousDarkState == true) DarkBackground else LightBackground

            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
        previousDarkState = isDark
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootSize = coordinates.size.toSize()
            }
            .drawWithContent {
                val progress = animProgress.value
                val prevBg = animatingPreviousBg

                if (progress < 1f && prevBg != null && rootSize.width > 0f) {
                    val center = animTouchPoint ?: Offset(rootSize.width / 2f, rootSize.height / 2f)

                    val dx = maxOf(center.x, rootSize.width - center.x)
                    val dy = maxOf(center.y, rootSize.height - center.y)
                    val maxRadius = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val currentRadius = maxRadius * progress

                    // 1. Draw base layer with previous theme background color
                    drawRect(color = prevBg)

                    // 2. Clip canvas to expanding circle and draw new theme content
                    val circlePath = Path().apply {
                        addOval(
                            Rect(
                                left = center.x - currentRadius,
                                top = center.y - currentRadius,
                                right = center.x + currentRadius,
                                bottom = center.y + currentRadius
                            )
                        )
                    }

                    clipPath(circlePath) {
                        this@drawWithContent.drawContent()
                    }

                    // 3. Draw a subtle glowing edge ring around the expanding circle
                    drawCircle(
                        color = primaryColor.copy(alpha = (1f - progress).coerceIn(0f, 0.6f)),
                        radius = currentRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                } else {
                    drawContent()
                }
            }
    ) {
        content()
    }
}
