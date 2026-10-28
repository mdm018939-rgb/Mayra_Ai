package com.example.ui.screens

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.api.GroqChatClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteBuilderScreen(
    groqClient: GroqChatClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var userPrompt by remember { mutableStateOf("") }
    var editInstruction by remember { mutableStateOf("") }
    var generatedHtml by remember { mutableStateOf("") }
    var previousHtml by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Preview, 1: Code

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(generatedHtml.toByteArray())
                }
            } catch (e: Exception) {}
        }
    }

    val generateWebsite: (String, String?) -> Unit = { prompt, existingHtml ->
        isLoading = true
        errorMessage = null

        scope.launch {
            val systemPrompt = """
You are an expert HTML/CSS/JS developer. Generate ONE complete self-contained HTML file for a web page based on the user's request.
Requirements:
1. Include inline CSS in <style> and inline JavaScript in <script> tags.
2. Ensure modern, mobile-friendly, beautiful design with smooth animations.
3. Use clean Bengali text for headings, labels, and content where appropriate.
4. Output ONLY the raw HTML code. Do NOT wrap in markdown code fences (` ```html ` or ` ``` `). No markdown explanations before or after.
"""
            val fullUserMsg = if (existingHtml.isNullOrBlank()) {
                "ওয়েবসাইট ডেসক্রিপশন: $prompt"
            } else {
                "বর্তমান ওয়েবসাইট কোড:\n$existingHtml\n\nপরিবর্তন নির্দেশাবলী: $prompt"
            }

            val res = groqClient.sendMessage(emptyList(), fullUserMsg)
            isLoading = false

            if (res.errorMessage != null) {
                errorMessage = res.errorMessage
            } else {
                var code = res.textResponse.trim()
                if (code.startsWith("```html")) code = code.substringAfter("```html")
                if (code.startsWith("```")) code = code.substringAfter("```")
                if (code.endsWith("```")) code = code.substringBeforeLast("```")
                code = code.trim()

                previousHtml = generatedHtml
                generatedHtml = code
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .testTag("website_builder_screen")
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("builder_back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে যান")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ওয়েবসাইট বানাও",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "প্রম্পট লিখো এবং মুহূর্তেই ফুল ওয়েবসাইট রেডি!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (generatedHtml.isBlank()) {
            // Initial Prompt Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    label = { Text("কেমন ওয়েবসাইট চাও? বলো...") },
                    placeholder = { Text("যেমন: একটি ক্যাফে ও কফি শপের সুন্দর ওয়েবসাইট") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("builder_prompt_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { generateWebsite(userPrompt, null) },
                    enabled = userPrompt.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("builder_generate_button"),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("তৈরি হচ্ছে...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("বানাও")
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Preview & Code Screen
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("প্রিভিউ", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("কোড", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (selectedTab == 0) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, generatedHtml, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                        Text(
                            text = generatedHtml,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(generatedHtml))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "কপি")
                }
                IconButton(onClick = { saveFileLauncher.launch("index.html") }) {
                    Icon(Icons.Filled.Save, contentDescription = "সেভ")
                }
                IconButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, generatedHtml)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "শেয়ার করুন"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "শেয়ার")
                }
                if (previousHtml.isNotBlank()) {
                    IconButton(onClick = { generatedHtml = previousHtml }) {
                        Icon(Icons.Filled.Undo, contentDescription = "পূর্বাবস্থায় ফেরাও")
                    }
                }
            }

            // Edit Instruction Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = editInstruction,
                    onValueChange = { editInstruction = it },
                    placeholder = { Text("কী বদলাতে চাও? বলো...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (editInstruction.isNotBlank()) {
                            generateWebsite(editInstruction, generatedHtml)
                            editInstruction = ""
                        }
                    },
                    shape = CircleShape
                ) {
                    Text("বদলাও")
                }
            }
        }
    }
}
