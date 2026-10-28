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

    var geminiKeyInput by remember { mutableStateOf(prefsManager.getGeminiKey()) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var isTestingGemini by remember { mutableStateOf(false) }

    var showAddGroqDialog by remember { mutableStateOf(false) }
    var newGroqKeyInput by remember { mutableStateOf("") }
    var isTestingGroqKey by remember { mutableStateOf<String?>(null) }

    // Dialog State
    var resultDialogData by remember { mutableStateOf<ResultDialogData?>(null) }

    val currentGeminiKey = prefsManager.getGeminiKey()
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

        // Card 1: Gemini Key (Voice)
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
                        Text("ভয়েস - Gemini key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("লাইভ ভয়েস কথোপকথন চালায়।", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    val isSaved = currentGeminiKey.isNotBlank()
                    val chipText = if (isSaved) {
                        val last4 = if (currentGeminiKey.length >= 4) currentGeminiKey.takeLast(4) else "XXXX"
                        "সংরক্ষিত ✓ ...$last4"
                    } else "key দাও"

                    SuggestionChip(
                        onClick = {},
                        label = { Text(chipText, fontSize = 11.sp, color = if (isSaved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSaved) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = geminiKeyInput,
                    onValueChange = { geminiKeyInput = it },
                    placeholder = { Text("আপনার Gemini API key পেস্ট করো") },
                    modifier = Modifier.fillMaxWidth().testTag("gemini_key_input"),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                            Icon(if (showGeminiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (geminiKeyInput.isNotBlank()) {
                            prefsManager.setGeminiKey(geminiKeyInput)
                            resultDialogData = ResultDialogData("সফল", "Gemini Key সফলভাবে সংরক্ষণ করা হয়েছে।", false)
                        } else {
                            resultDialogData = ResultDialogData("ভুল ইনপুট", "দয়া করে একটি সঠিক Gemini API Key ইনপুট দিন।", true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_gemini_key_button"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Gemini key সংরক্ষণ করো")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            val keyToTest = currentGeminiKey.ifBlank { geminiKeyInput }
                            if (keyToTest.isBlank()) {
                                resultDialogData = ResultDialogData("পরীক্ষা ব্যর্থ", "পরীক্ষা করার জন্য প্রথমে একটি Gemini Key দিন।", true)
                                return@OutlinedButton
                            }
                            isTestingGemini = true
                            val client = GeminiLiveWebSocketClient(
                                apiKey = keyToTest,
                                systemInstruction = "Test connection",
                                listener = object : GeminiLiveWebSocketClient.LiveEventListener {
                                    override fun onSetupComplete() {
                                        resultDialogData = ResultDialogData("সংযোগ সফল", "Gemini Live API সংযোগ ঠিকমতো কাজ করছে! ✓", false)
                                        isTestingGemini = false
                                    }
                                    override fun onAudioDataReceived(pcm24kBytes: ByteArray) {}
                                    override fun onInputTranscription(text: String) {}
                                    override fun onOutputTranscription(text: String) {}
                                    override fun onInterrupted() {}
                                    override fun onTurnComplete() {}
                                    override fun onToolCall(id: String, name: String, args: org.json.JSONObject) {}
                                    override fun onError(errorMessage: String) {
                                        resultDialogData = ResultDialogData("সংযোগ ব্যর্থ", "Gemini Live সংযোগ ব্যর্থ: $errorMessage", true)
                                        isTestingGemini = false
                                    }
                                    override fun onClosed(code: Int, reason: String) {
                                        isTestingGemini = false
                                    }
                                }
                            )
                            client.connect()
                        },
                        enabled = !isTestingGemini
                    ) {
                        if (isTestingGemini) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("সংযোগ পরীক্ষা করুন")
                    }

                    TextButton(
                        onClick = {
                            prefsManager.clearGeminiKey()
                            geminiKeyInput = ""
                            resultDialogData = ResultDialogData("মুছে ফেলা হয়েছে", "Gemini key সফলভাবে মুছে ফেলা হয়েছে।", false)
                        }
                    ) {
                        Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error)
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
