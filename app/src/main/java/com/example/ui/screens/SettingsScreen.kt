package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onNavigateToSubPage: (String) -> Unit,
    onQuickActionClick: (String) -> Unit = {},
    onNavigateToQuickActions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val quickItems = listOf(
        QuickActionItem("অ্যাপ খোলো", "ইউটিউব খোলো", Icons.Filled.Apps, "open_app"),
        QuickActionItem("কল করো", "মাকে কল করো", Icons.Filled.Call, "make_call"),
        QuickActionItem("ওয়েবসাইট বানাও", "ক্যাফের ওয়েবসাইট বানাও", Icons.Filled.Code, "website_builder"),
        QuickActionItem("ছবি আঁকো", "একটা ছবি আঁকো", Icons.Filled.Palette, "sketch"),
        QuickActionItem("কথা বলো", "ভয়েস কথোপকথন", Icons.Filled.Mic, "voice_talk")
    )

    val menuItems = listOf(
        SettingsMenuItem("সংযোগ", "Gemini ও Groq API Keys যুক্ত করো", Icons.Filled.Key, "connections"),
        SettingsMenuItem("পারসোনালিটি", "মাইরার স্বভাব ও কথা বলার ধরন বেছে নাও", Icons.Filled.Face, "personality"),
        SettingsMenuItem("থিম", "হালকা / গাঢ় / সিস্টেম থিম", Icons.Filled.Palette, "theme"),
        SettingsMenuItem("ভাসমান মেয়ে", "অন্যান্য অ্যাপের উপর মাইরাকে ভাসিয়ে রাখো", Icons.Filled.FilterNone, "overlay"),
        SettingsMenuItem("মেসেজ অটোমেশন", "হোয়াটসঅ্যাপ ও মেসেঞ্জার অটোমেশন সেটিংস", Icons.Filled.AutoMode, "automation"),
        SettingsMenuItem("অনুমতি", "অ্যাপের প্রয়োজনীয় অনুমতিসমূহ দেখুন", Icons.Filled.Security, "permissions"),
        SettingsMenuItem("লগ", "অ্যাপের সিস্টেম ও কানেকশন লগ দেখুন", Icons.Filled.ReceiptLong, "logs"),
        SettingsMenuItem("ডাকনাম", "মাইরা আপনাকে যে নামে ডাকবে", Icons.Filled.Badge, "nickname"),
        SettingsMenuItem("অ্যাপ সম্পর্কে", "মাইরা ভার্সন ও তথ্য", Icons.Filled.Info, "about")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = "সেটিংস",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
        ) {
            // Quick Actions Section on Settings Screen
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "কুইক অ্যাকশন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "সব দেখো",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onNavigateToQuickActions)
                                .testTag("settings_see_all_quick_actions")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickItems) { item ->
                            Card(
                                modifier = Modifier
                                    .width(145.dp)
                                    .clickable { onQuickActionClick(item.actionKey) }
                                    .testTag("settings_quick_card_${item.actionKey}"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }

            // Divider / Section Title for Settings
            item {
                Text(
                    text = "সাধারণ সেটিংস",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Settings Menu Items
            items(menuItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSubPage(item.pageKey) }
                        .testTag("settings_item_${item.pageKey}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val actionKey: String
)

data class SettingsMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val pageKey: String
)
