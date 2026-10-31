package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EncryptedPreferencesManager

data class RomanticSubStyle(
    val name: String,
    val title: String,
    val desc: String,
    val icon: ImageVector
)

@Composable
fun PersonalityScreen(
    prefsManager: EncryptedPreferencesManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSaved by prefsManager.personalityFlow.collectAsState()
    var selectedPersonality by remember(currentSaved) { mutableStateOf(currentSaved) }

    val romanticSubStyles = remember {
        listOf(
            RomanticSubStyle(
                name = "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)",
                title = "ভালো প্রেমিকা",
                desc = "মিষ্টি, অনুরাগী ও সোহাগী প্রেমিকা (ডিফল্ট) — অপার যত্নশীল ও ভালোবাসাময়",
                icon = Icons.Filled.Favorite
            ),
            RomanticSubStyle(
                name = "রোমান্টিক সঙ্গী (সন্দেহবাজ প্রেমিকা)",
                title = "সন্দেহবাজ প্রেমিকা",
                desc = "কৌতুকপূর্ণ teasing ও দুষ্টুমি করে নজরদারি — কখনো খবরদারি বা নিয়ন্ত্রণ নয়",
                icon = Icons.Filled.Search
            ),
            RomanticSubStyle(
                name = "রোমান্টিক সঙ্গী (রাগী প্রেমিকা)",
                title = "রাগী প্রেমিকা",
                desc = "ঝাঁঝালো অভিমান ও স্পষ্ট রাগের অভিনয় — কাজ শেষে রাগের মন্তব্য",
                icon = Icons.Filled.MoodBad
            ),
            RomanticSubStyle(
                name = "রোমান্টিক সঙ্গী (ফ্লার্টি প্রেমিকা)",
                title = "ফ্লার্টি প্রেমিকা",
                desc = "রহস্যময়, আকর্ষণীয় ও দুষ্টু-মিষ্টি ফ্লার্টি প্রেমিকা",
                icon = Icons.Filled.AutoAwesome
            )
        )
    }

    // Ensure selectedPersonality maps to one of the 4 romantic sub-styles, default to first (ভালো প্রেমিকা)
    val activeSub = romanticSubStyles.find { it.name == selectedPersonality }
        ?: romanticSubStyles.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("personality_screen")
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("personality_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "পারসোনালিটি",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "মাইরার কথা বলা ও আচরণের ধরন বেছে নাও",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Romantic Companion Hero Card (Always Active & Selected)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .testTag("personality_card_romantic_hero"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "রোমান্টিক সঙ্গী",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "মিষ্টি প্রেমিকা ও অনুরাগী সহচরী",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "নির্বাচিত",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "প্লেফুল সাব-স্টাইল বেছে নাও:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sub-style chips in 2x2 grid layout
                val chipRows = romanticSubStyles.chunked(2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chipRows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { subStyle ->
                                val isSubSelected = activeSub.name == subStyle.name
                                FilterChip(
                                    selected = isSubSelected,
                                    onClick = {
                                        selectedPersonality = subStyle.name
                                        prefsManager.setPersonality(subStyle.name)
                                    },
                                    label = {
                                        Text(
                                            subStyle.title,
                                            fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = if (isSubSelected) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("romantic_substyle_${subStyle.title}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selected sub-style description callout box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = activeSub.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeSub.desc,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
