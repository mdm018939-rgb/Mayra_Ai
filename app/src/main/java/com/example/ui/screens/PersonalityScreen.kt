package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class PersonalityItem(
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
                desc = "মিষ্টি অভিমান (playful abhiman) ও আদুরে খুনসুটি — কাজের পর কপট রাগ দেখাবে",
                icon = Icons.Filled.MoodBad
            )
        )
    }

    val isRomanticSelected = selectedPersonality.startsWith("রোমান্টিক সঙ্গী")

    val otherPersonalities = remember {
        listOf(
            PersonalityItem("সাধারণ", "স্পষ্ট, মার্জিত ও নিরপেক্ষ এআই সহকারী", Icons.Filled.Face),
            PersonalityItem("বন্ধুসুলভ", "উৎফুল্ল, বন্ধুভাবাপন্ন ও ক্যাজুয়াল সঙ্গিনী", Icons.Filled.People),
            PersonalityItem("পেশাদার", "সংক্ষিপ্ত, ফরমাল ও গোছানো ব্যক্তিত্ব", Icons.Filled.Work),
            PersonalityItem("মজার", "হালকা রসিকতা ও চমৎকার সেন্স অব হিউমার", Icons.Filled.SentimentVerySatisfied),
            PersonalityItem("সঙ্গী", "উষ্ণ, যত্নশীল ও সবসময় পাশে থাকার মতো", Icons.Filled.Handshake),
            PersonalityItem("শিক্ষক", "ধৈর্যশীল ও সুন্দর ব্যাখ্যাকারী শিক্ষক", Icons.Filled.School),
            PersonalityItem("ডেভেলপার", "টেকনিক্যাল, লজিক্যাল ও কোডিং বিশেষজ্ঞ", Icons.Filled.Code),
            PersonalityItem("ক্ষুধার্ত", "ভোজনরসিক, সবসময় মজার খাবারের ভক্ত", Icons.Filled.Restaurant),
            PersonalityItem("নাটকি", "নাটকীয়, ফিলমি ফ্ল্যায়ার ও রোমাঞ্চকর ব্যক্তিত্ব", Icons.Filled.Movie)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("personality_screen")
    ) {
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

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp)
        ) {
            // Romantic Companion Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clickable {
                            if (!isRomanticSelected) {
                                val target = "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)"
                                selectedPersonality = target
                                prefsManager.setPersonality(target)
                            }
                        }
                        .testTag("personality_card_romantic_hero"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRomanticSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(if (isRomanticSelected) 4.dp else 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isRomanticSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = if (isRomanticSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "রোমান্টিক সঙ্গী",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "মিষ্টি প্রেমিকা ও অনুরাগী সহচরী",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            if (isRomanticSelected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "নির্বাচিত",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "প্লেফুল সাব-স্টাইল বেছে নাও:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sub-style chips / buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            romanticSubStyles.forEach { subStyle ->
                                val isSubSelected = selectedPersonality == subStyle.name
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("romantic_substyle_${subStyle.title}")
                                )
                            }
                        }

                        // Selected sub-style description callout
                        val activeSub = romanticSubStyles.find { it.name == selectedPersonality }
                            ?: romanticSubStyles.first()

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "✨ ${activeSub.desc}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Other Personalities Header
            item {
                Text(
                    text = "অন্যান্য পারসোনালিটি",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Other Personalities Items
            items(otherPersonalities) { item ->
                val isSelected = item.title == selectedPersonality
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPersonality = item.title
                            prefsManager.setPersonality(item.title)
                        }
                        .testTag("personality_card_${item.title}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(item.desc, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "নির্বাচিত", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
