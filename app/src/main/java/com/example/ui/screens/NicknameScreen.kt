package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EncryptedPreferencesManager
import com.example.service.MairaVoiceService
import com.example.ui.components.MairaResultDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknameScreen(
    prefsManager: EncryptedPreferencesManager,
    onReconnectVoiceSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nicknameInput by remember { mutableStateOf(prefsManager.getNickname()) }
    var dialogData by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("nickname_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("nickname_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ডাকনাম সেটিংস",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "আমাকে যে নামে ডাকবে (ফাঁকা রাখলে 'তুমি' বলবে)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = nicknameInput,
            onValueChange = { nicknameInput = it },
            label = { Text("আপনার ডাকনাম") },
            placeholder = { Text("যেমন: রাহুল / তানভীর / অপু") },
            modifier = Modifier.fillMaxWidth().testTag("nickname_input"),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                prefsManager.setNickname(nicknameInput)
                val isVoiceActive = MairaVoiceService.isSessionActive()

                if (isVoiceActive) {
                    onReconnectVoiceSession()
                    dialogData = Pair("সফল", "ডাকনাম সংরক্ষণ করা হয়েছে! নতুন নাম কার্যকর করতে ভয়েস সেসন রি-কানেক্ট করা হচ্ছে... ✓")
                } else {
                    dialogData = Pair("সফল", "ডাকনাম সফলভাবে সংরক্ষণ করা হয়েছে ✓")
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("সংরক্ষণ করো")
        }

        dialogData?.let { (title, msg) ->
            MairaResultDialog(
                title = title,
                message = msg,
                isError = false,
                onDismiss = { dialogData = null }
            )
        }
    }
}
