package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GeminiLiveWebSocketClient
import com.example.api.GroqChatClient
import com.example.api.GroqModelResolver
import com.example.data.EncryptedPreferencesManager
import com.example.ui.components.MairaResultDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    prefsManager: EncryptedPreferencesManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddGeminiDialog by remember { mutableStateOf(false) }
    var newGeminiKeyInput by remember { mutableStateOf("") }
    var isTestingGeminiKey by remember { mutableStateOf<String?>(null) }

    var showAddGroqDialog by remember { mutableStateOf(false) }
    var newGroqKeyInput by remember { mutableStateOf("") }
    var isTestingGroqKey by remember { mutableStateOf<String?>(null) }

    // Dialog State
    var resultDialogData by remember { mutableStateOf<ResultDialogData?>(null) }

    val geminiKeysList by prefsManager.geminiKeysFlow.collectAsState()
    val groqKeysList by prefsManager.groqKeysFlow.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("connections_screen")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("connections_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "সংযোজন",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "মাইরা তোমার নিজের key দিয়ে কথা বলে ও চ্যাট করে। key শুধু এই ফোনেই থাকে।",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Gemini Keys (Voice) List
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ভয়েস - Gemini keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("লাইভ ভয়েস কথোপকথন চালায়। একটির কোটা শেষ হলে পরেরটি পালা করে চলবে।", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    SuggestionChip(
                        onClick = {},
                        label = { Text("${geminiKeysList.size} টি সংরক্ষণ", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showAddGeminiDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("add_gemini_key_button"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ key যোগ করো")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (geminiKeysList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("কোনো Gemini Key যোগ করা হয়নি।", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    geminiKeysList.forEachIndexed { index, key ->
                        val maskedKey = if (key.length >= 4) "AIza...${key.takeLast(4)}" else "AIza...XXXX"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Key #${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(maskedKey, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            isTestingGeminiKey = key
                                            val client = GeminiLiveWebSocketClient(
                                                apiKey = key,
                                                systemInstruction = "Test connection",
                                                listener = object : GeminiLiveWebSocketClient.LiveEventListener {
                                                    override fun onSetupComplete() {
                                                        resultDialogData = ResultDialogData("সংযোগ সফল", "Gemini Key #${index + 1} ঠিকমতো কাজ করছে! ✓", false)
                                                        isTestingGeminiKey = null
                                                    }
                                                    override fun onAudioDataReceived(pcm24kBytes: ByteArray) {}
                                                    override fun onInputTranscription(text: String) {}
                                                    override fun onOutputTranscription(text: String) {}
                                                    override fun onInterrupted() {}
                                                    override fun onTurnComplete() {}
                                                    override fun onToolCall(id: String, name: String, args: org.json.JSONObject) {}
                                                    override fun onError(errorMessage: String) {
                                                        resultDialogData = ResultDialogData("সংযোগ ব্যর্থ", "Gemini Key #${index + 1} সংযোগ ব্যর্থ: $errorMessage", true)
                                                        isTestingGeminiKey = null
                                                    }
                                                    override fun onClosed(code: Int, reason: String) {
                                                        isTestingGeminiKey = null
                                                    }
                                                }
                                            )
                                            client.connect()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp),
                                        enabled = isTestingGeminiKey != key
                                    ) {
                                        if (isTestingGeminiKey == key) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("পরীক্ষা করো", fontSize = 11.sp)
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            prefsManager.removeGeminiKey(key)
                                            resultDialogData = ResultDialogData("মুছে ফেলা হয়েছে", "Gemini Key মুছে ফেলা হয়েছে।", false)
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Google AI Studio থেকে ফ্রি key নাও", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Groq Keys (Chat) List
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("চ্যাট - Groq keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("একটির রেট লিমিট শেষ হলে পরেরটি পালা করে চলবে।", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    SuggestionChip(
                        onClick = {},
                        label = { Text("${groqKeysList.size} টি সংরক্ষণ", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showAddGroqDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ key যোগ করো")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (groqKeysList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("কোনো Groq Key যোগ করা হয়নি।", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    groqKeysList.forEachIndexed { index, key ->
                        val maskedKey = if (key.length >= 4) "gsk_...${key.takeLast(4)}" else "gsk_...XXXX"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Key #${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(maskedKey, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            isTestingGroqKey = key
                                            scope.launch {
                                                val client = GroqChatClient(listOf(key), "", "Test")
                                                val res = client.sendMessage(emptyList(), "Hi")
                                                isTestingGroqKey = null
                                                if (res.errorMessage == null) {
                                                    val model = res.modelUsed ?: "অজানা"
                                                    resultDialogData = ResultDialogData("পরীক্ষা সফল", "Groq Key সংযোগ ঠিকমতো কাজ করছে! ✓\n(চালু মডেল: $model)", false)
                                                } else {
                                                    resultDialogData = ResultDialogData("পরীক্ষা ব্যর্থ", res.errorMessage, true)
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp),
                                        enabled = isTestingGroqKey != key
                                    ) {
                                        if (isTestingGroqKey == key) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("পরীক্ষা করো", fontSize = 11.sp)
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            prefsManager.removeGroqKey(key)
                                            resultDialogData = ResultDialogData("মুছে ফেলা হয়েছে", "Groq Key মুছে ফেলা হয়েছে।", false)
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Groq থেকে ফ্রি key নাও", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Add Gemini Key Dialog
    if (showAddGeminiDialog) {
        AlertDialog(
            onDismissRequest = { showAddGeminiDialog = false },
            title = { Text("নতুন Gemini Key যোগ করো") },
            text = {
                OutlinedTextField(
                    value = newGeminiKeyInput,
                    onValueChange = { newGeminiKeyInput = it },
                    placeholder = { Text("AIza...") },
                    modifier = Modifier.fillMaxWidth().testTag("new_gemini_key_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newGeminiKeyInput.isNotBlank()) {
                        val added = prefsManager.addGeminiKey(newGeminiKeyInput)
                        showAddGeminiDialog = false
                        newGeminiKeyInput = ""
                        if (added) {
                            resultDialogData = ResultDialogData("সফল", "Gemini Key সফলভাবে তালিকায় যোগ করা হয়েছে।", false)
                        } else {
                            resultDialogData = ResultDialogData("তথ্য", "এই Key-টি আগেই যোগ করা রয়েছে।", true)
                        }
                    }
                }) {
                    Text("যোগ করো")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGeminiDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Add Groq Key Dialog
    if (showAddGroqDialog) {
        AlertDialog(
            onDismissRequest = { showAddGroqDialog = false },
            title = { Text("নতুন Groq Key যোগ করো") },
            text = {
                OutlinedTextField(
                    value = newGroqKeyInput,
                    onValueChange = { newGroqKeyInput = it },
                    placeholder = { Text("gsk_...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newGroqKeyInput.isNotBlank()) {
                        val added = prefsManager.addGroqKey(newGroqKeyInput)
                        showAddGroqDialog = false
                        newGroqKeyInput = ""
                        if (added) {
                            resultDialogData = ResultDialogData("সফল", "Groq Key সফলভাবে তালিকায় যোগ করা হয়েছে।", false)
                        } else {
                            resultDialogData = ResultDialogData("তথ্য", "এই Key-টি আগেই যোগ করা রয়েছে।", true)
                        }
                    }
                }) {
                    Text("যোগ করো")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGroqDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Result Popup Dialog
    resultDialogData?.let { dialog ->
        MairaResultDialog(
            title = dialog.title,
            message = dialog.message,
            isError = dialog.isError,
            onDismiss = { resultDialogData = null }
        )
    }
}

private data class ResultDialogData(val title: String, val message: String, val isError: Boolean)
