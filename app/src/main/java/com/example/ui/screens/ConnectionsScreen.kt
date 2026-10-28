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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GeminiLiveWebSocketClient
import com.example.api.GroqChatClient
import com.example.data.EncryptedPreferencesManager
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
    var geminiTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingGemini by remember { mutableStateOf(false) }

    var groqKeysInput by remember { mutableStateOf(prefsManager.getGroqKeys().joinToString(", ")) }
    var groqTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingGroq by remember { mutableStateOf(false) }

    val currentGeminiKey = prefsManager.getGeminiKey()
    val currentGroqKeys = prefsManager.getGroqKeys()

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
                        prefsManager.setGeminiKey(geminiKeyInput)
                        geminiTestStatus = "Gemini Key সফলভাবে সংরক্ষণ করা হয়েছে।"
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
                            isTestingGemini = true
                            geminiTestStatus = "Gemini Live সংযোগ পরীক্ষা করা হচ্ছে..."
                            val client = GeminiLiveWebSocketClient(
                                apiKey = currentGeminiKey.ifBlank { geminiKeyInput },
                                systemInstruction = "Test connection",
                                listener = object : GeminiLiveWebSocketClient.LiveEventListener {
                                    override fun onSetupComplete() {
                                        geminiTestStatus = "সফল: Gemini Live সংযোগ ঠিক আছে! ✓"
                                        isTestingGemini = false
                                    }
                                    override fun onAudioDataReceived(pcm24kBytes: ByteArray) {}
                                    override fun onInputTranscription(text: String) {}
                                    override fun onOutputTranscription(text: String) {}
                                    override fun onInterrupted() {}
                                    override fun onTurnComplete() {}
                                    override fun onToolCall(id: String, name: String, args: org.json.JSONObject) {}
                                    override fun onError(errorMessage: String) {
                                        geminiTestStatus = "ব্যর্থ: $errorMessage"
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
                        Text("সংযোগ পরীক্ষা করুন")
                    }

                    TextButton(
                        onClick = {
                            prefsManager.clearGeminiKey()
                            geminiKeyInput = ""
                            geminiTestStatus = "Gemini key মুছে ফেলা হয়েছে।"
                        }
                    ) {
                        Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (geminiTestStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(geminiTestStatus!!, fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
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

        // Card 2: Groq Keys (Chat)
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
                        Text("দ্রুত লেখা উত্তর। একাধিক key কমা দিয়ে আলাদা করে দিতে পারো।", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    val count = currentGroqKeys.size
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$count টি সংরক্ষিত", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = groqKeysInput,
                    onValueChange = { groqKeysInput = it },
                    placeholder = { Text("gsk_..., gsk_...") },
                    modifier = Modifier.fillMaxWidth().testTag("groq_keys_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        prefsManager.setGroqKeys(groqKeysInput)
                        groqTestStatus = "Groq Key(s) সংরক্ষণ করা হয়েছে।"
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_groq_keys_button"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Groq keys সংরক্ষণ করো")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            isTestingGroq = true
                            groqTestStatus = "Groq keys পরীক্ষা করা হচ্ছে..."
                            scope.launch {
                                val keys = prefsManager.getGroqKeys()
                                if (keys.isEmpty()) {
                                    groqTestStatus = "কোনো Groq Key সংরক্ষিত নাই।"
                                } else {
                                    val client = GroqChatClient(keys, "", "Test")
                                    val res = client.sendMessage(emptyList(), "Hi")
                                    if (res.errorMessage == null) {
                                        val modelName = res.modelUsed ?: "অজানা"
                                        groqTestStatus = "সফল: Groq Key সংযোগ ঠিক আছে! ✓\n(চালু মডেল: $modelName)"
                                    } else {
                                        groqTestStatus = "ব্যর্থ: ${res.errorMessage}"
                                    }
                                }
                                isTestingGroq = false
                            }
                        },
                        enabled = !isTestingGroq
                    ) {
                        Text("সংযোগ পরীক্ষা করুন")
                    }

                    TextButton(
                        onClick = {
                            prefsManager.clearGroqKeys()
                            groqKeysInput = ""
                            groqTestStatus = "Groq key(s) মুছে ফেলা হয়েছে।"
                        }
                    ) {
                        Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (groqTestStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(groqTestStatus!!, fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
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
}
