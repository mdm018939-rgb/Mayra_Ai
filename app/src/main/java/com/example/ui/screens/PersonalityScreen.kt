package com.example.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EncryptedPreferencesManager

@Composable
fun PersonalityScreen(
    prefsManager: EncryptedPreferencesManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPersonality by remember { mutableStateOf(prefsManager.getPersonality()) }

    val personalities = listOf(
        PersonalityItem("রোমান্টিক সঙ্গী (বউ মোড)", "মিষ্টি, অনুরাগী ও সোহাগী বাঙালি বউ মোড (ডিফল্ট)", Icons.Filled.Favorite),
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
        ) {
            items(personalities) { item ->
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

data class PersonalityItem(val title: String, val desc: String, val icon: ImageVector)
