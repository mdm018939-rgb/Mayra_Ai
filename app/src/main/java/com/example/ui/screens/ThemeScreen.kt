package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EncryptedPreferencesManager
import com.example.ui.theme.ThemeTouchManager

@Composable
fun ThemeScreen(
    prefsManager: EncryptedPreferencesManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTheme by prefsManager.themeModeFlow.collectAsState()

    val isDark = when (selectedTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "theme_rotation_angle"
    )

    val options = listOf(
        Triple("light", "হালকা (সাদা ও গোলাপী থিম)", Icons.Filled.WbSunny),
        Triple("dark", "গাঢ় (ডার্ক নেভি ও ভায়োলেট থিম)", Icons.Filled.NightsStay),
        Triple("system", "সিস্টেম অনুযায়ী (অটো)", Icons.Filled.SettingsSuggest)
    )

    var quickToggleOffset by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("theme_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("theme_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "থিম সেটিংস",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "অ্যাপের ভিজ্যুয়াল থিম কাস্টমাইজ করো",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // Quick Toggle Button with Animated Sun/Moon Icon
            IconButton(
                onClick = {
                    ThemeTouchManager.lastTouchPoint = quickToggleOffset
                    val next = if (isDark) "light" else "dark"
                    prefsManager.setThemeMode(next)
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        quickToggleOffset = Offset(
                            pos.x + coords.size.width / 2f,
                            pos.y + coords.size.height / 2f
                        )
                    }
                    .testTag("theme_quick_toggle")
            ) {
                AnimatedContent(
                    targetState = isDark,
                    transitionSpec = {
                        (fadeIn(tween(350)) + scaleIn(tween(350), 0.5f))
                            .togetherWith(fadeOut(tween(350)) + scaleOut(tween(350), 0.5f))
                    },
                    label = "theme_icon_animated"
                ) { targetDark ->
                    Icon(
                        imageVector = if (targetDark) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                        contentDescription = "থিম পরিবর্তন",
                        tint = if (targetDark) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                rotationZ = rotationAngle
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        options.forEach { (key, label, icon) ->
            val isSelected = selectedTheme == key
            var cardOffset by remember { mutableStateOf<Offset?>(null) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        cardOffset = Offset(
                            pos.x + coords.size.width / 2f,
                            pos.y + coords.size.height / 2f
                        )
                    }
                    .clickable {
                        ThemeTouchManager.lastTouchPoint = cardOffset
                        prefsManager.setThemeMode(key)
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            ThemeTouchManager.lastTouchPoint = cardOffset
                            prefsManager.setThemeMode(key)
                        }
                    )
                }
            }
        }
    }
}
