package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EncryptedPreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    prefsManager: EncryptedPreferencesManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(prefsManager.isOverlayEnabled() && Settings.canDrawOverlays(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("overlay_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("overlay_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ভাসমান মেয়ে (ওভারলে)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "অন্যান্য অ্যাপের উপরে মাইরাকে দেখা যাবে",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ভাসমান মেয়ে চালু করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "ফোনের যেকোনো স্ক্রিনে মাইরাকে ভাসিয়ে রাখুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!Settings.canDrawOverlays(context)) {
                                    isEnabled = false
                                    prefsManager.setOverlayEnabled(false)
                                    errorMessage = "অন্যান্য অ্যাপের উপরে ভাসমান মেয়ে দেখানোর জন্য প্রথমে পারমিশন চালু করতে হবে।"
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        errorMessage = "পারমিশন স্ক্রিন খুলতে সমস্যা হয়েছে: ${e.localizedMessage}"
                                    }
                                } else {
                                    try {
                                        isEnabled = true
                                        prefsManager.setOverlayEnabled(true)
                                        com.example.service.MairaOverlayService.startService(context)
                                    } catch (e: Exception) {
                                        isEnabled = false
                                        prefsManager.setOverlayEnabled(false)
                                        errorMessage = "ভাসমান মেয়ে চালু করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
                                    }
                                }
                            } else {
                                isEnabled = false
                                prefsManager.setOverlayEnabled(false)
                                try {
                                    com.example.service.MairaOverlayService.stopService(context)
                                } catch (e: Exception) {
                                    errorMessage = "ভাসমান মেয়ে বন্ধ করতে সমস্যা হয়েছে: ${e.localizedMessage}"
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.example.ui.components.MairaAvatarComposable(
                            status = "প্রস্তুত, কথা বলো...",
                            micRms = 0f,
                            speakerRms = 0f,
                            sessionState = if (isEnabled) "সংযুক্ত" else "বন্ধ",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 লাইভ লিপ-সিঙ্ক ও চোখ পলক ফেলা অ্যানিমেটেড অ্যাভাটার অন্যান্য অ্যাপের (ইউটিউব, হোয়াটসঅ্যাপ ইত্যাদি) উপরে ড্র্যাগ করে যেকোনো পাশে রাখা যাবে।",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        errorMessage?.let { msg ->
            com.example.ui.components.MairaResultDialog(
                title = "ভাসমান মেয়ে সেটিংস",
                message = msg,
                isError = true,
                onDismiss = { errorMessage = null }
            )
        }
    }
}
