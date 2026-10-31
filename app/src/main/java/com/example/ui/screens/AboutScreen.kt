package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(40f) }

    LaunchedEffect(Unit) {
        delay(index * 90L)
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetYAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = alphaAnim.value
            translationY = offsetYAnim.value.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    val pageAlphaAnim = remember { Animatable(0f) }
    val pageSlideAnim = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        launch {
            pageAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
        launch {
            pageSlideAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .graphicsLayer {
                alpha = pageAlphaAnim.value
                translationY = pageSlideAnim.value.dp.toPx()
            }
            .testTag("about_screen")
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("about_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "পেছনে যান",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "অ্যাপ সম্পর্কে",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 0. Circular Image
            StaggeredItem(index = 0) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.maira_body),
                        contentDescription = "মাইরা",
                        modifier = Modifier.size(75.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Name
            StaggeredItem(index = 1) {
                Text(
                    text = "মাইরা (Maira)",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Version
            StaggeredItem(index = 2) {
                Text(
                    text = "ভার্সন ১.০.০",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Description Card
            StaggeredItem(index = 3, modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "মাইরা অ্যাপে স্বাগতম! আমি তোমার অনুভূতিশীল বাংলা এআই সঙ্গিনী। তোমার সব কাজে সাহায্য করার জন্য এবং গল্প করার জন্য আমি সবসময় প্রস্তুত।",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Telegram Support Button
            StaggeredItem(index = 4, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/teemcs")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("telegram_support_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "টেলিগ্রাম সাপোর্ট Group (t.me/teemcs)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
