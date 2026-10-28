package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MairaBottomBar(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    isSessionActive: Boolean,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlowTransition")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSessionActive) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Glow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .testTag("maira_bottom_bar"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. হোম (Home)
            NavTabItem(
                label = "হোম",
                selected = currentScreen == "home",
                activeIcon = Icons.Filled.Home,
                inactiveIcon = Icons.Outlined.Home,
                testTag = "nav_home",
                onClick = { onNavigate("home") }
            )

            // 2. চ্যাট (Chat)
            NavTabItem(
                label = "চ্যাট",
                selected = currentScreen == "chat",
                activeIcon = Icons.Filled.ChatBubble,
                inactiveIcon = Icons.Outlined.ChatBubbleOutline,
                testTag = "nav_chat",
                onClick = { onNavigate("chat") }
            )

            // 3. Center Raised Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .testTag("mic_center_button")
            ) {
                if (isSessionActive) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer {
                                scaleX = glowScale
                                scaleY = glowScale
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    )
                }

                FloatingActionButton(
                    onClick = onMicClick,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isSessionActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isSessionActive) "ভয়েস সেসন বন্ধ করুন" else "ভয়েস সেসন শুরু করুন",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 4. ট্রিগারস (Triggers)
            NavTabItem(
                label = "ট্রিগারস",
                selected = currentScreen == "triggers",
                activeIcon = Icons.Filled.Bolt,
                inactiveIcon = Icons.Outlined.Bolt,
                testTag = "nav_triggers",
                onClick = { onNavigate("triggers") }
            )

            // 5. সেটিংস (Settings)
            NavTabItem(
                label = "সেটিংস",
                selected = currentScreen == "settings",
                activeIcon = Icons.Filled.Settings,
                inactiveIcon = Icons.Outlined.Settings,
                testTag = "nav_settings",
                onClick = { onNavigate("settings") }
            )
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    selected: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = if (selected) activeIcon else inactiveIcon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
