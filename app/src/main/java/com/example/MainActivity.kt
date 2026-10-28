package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.api.GeminiImageClient
import com.example.api.GroqChatClient
import com.example.data.EncryptedPreferencesManager
import com.example.data.MairaLogger
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.TriggerEntity
import com.example.personality.PersonalityPrompts
import com.example.service.MairaVoiceService
import com.example.tools.DeviceToolExecutor
import com.example.ui.components.MairaBottomBar
import com.example.ui.screens.*
import com.example.ui.theme.MairaTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: EncryptedPreferencesManager
    private lateinit var database: AppDatabase
    private lateinit var toolExecutor: DeviceToolExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsManager = EncryptedPreferencesManager(this)
        database = AppDatabase.getInstance(this)
        toolExecutor = DeviceToolExecutor(this)

        setContent {
            val themeMode by remember { mutableStateOf(prefsManager.getThemeMode()) }

            MairaTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MairaAppContent(
                        prefsManager = prefsManager,
                        database = database,
                        toolExecutor = toolExecutor,
                        onStartVoiceService = { startVoiceService() },
                        onStopVoiceService = { stopVoiceService() }
                    )
                }
            }
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, MairaVoiceService::class.java).apply {
            action = MairaVoiceService.ACTION_START_SESSION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVoiceService() {
        val intent = Intent(this, MairaVoiceService::class.java).apply {
            action = MairaVoiceService.ACTION_STOP_SESSION
        }
        startService(intent)
    }
}

@Composable
fun MairaAppContent(
    prefsManager: EncryptedPreferencesManager,
    database: AppDatabase,
    toolExecutor: DeviceToolExecutor,
    onStartVoiceService: () -> Unit,
    onStopVoiceService: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf("home") }
    var currentConvoId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    // Service reactive states
    val sessionState by MairaVoiceService.sessionState.collectAsState()
    val statusMessage by MairaVoiceService.statusMessage.collectAsState()
    val micRms by MairaVoiceService.micRmsFlow.collectAsState()
    val speakerRms by MairaVoiceService.speakerRmsFlow.collectAsState()

    val isSessionActive = MairaVoiceService.isSessionActive()

    // Room flows
    val chatMessages by database.chatMessageDao().getMessagesForConversation(currentConvoId)
        .collectAsState(initial = emptyList())
    val conversationIds by database.chatMessageDao().getAllConversationIds()
        .collectAsState(initial = emptyList())
    val triggersList by database.triggerDao().getAllTriggers()
        .collectAsState(initial = emptyList())

    val userNickname = prefsManager.getNickname()
    val geminiKey = prefsManager.getGeminiKey()
    val groqKeys = prefsManager.getGroqKeys()

    val hasKeys = geminiKey.isNotBlank() || groqKeys.isNotEmpty()

    val groqClient = remember(groqKeys, geminiKey, prefsManager.getPersonality()) {
        val personality = prefsManager.getPersonality()
        val nickname = prefsManager.getNickname()
        val sysPrompt = PersonalityPrompts.getSystemInstruction(personality, nickname)
        GroqChatClient(groqKeys, geminiKey, sysPrompt)
    }

    val imageClient = remember(geminiKey) {
        GeminiImageClient(context, geminiKey)
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordGranted) {
            if (geminiKey.isNotBlank()) {
                onStartVoiceService()
            } else {
                currentScreen = "connections"
                Toast.makeText(context, "প্রথমে সেটিংসে আপনার Gemini key দিন", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "ভয়েস সেসনের জন্য মাইক্রোফোন অনুমতি প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    val handleMicButtonClick = {
        if (isSessionActive) {
            onStopVoiceService()
        } else {
            if (geminiKey.isBlank()) {
                currentScreen = "connections"
                Toast.makeText(context, "প্রথমে সেটিংসে আপনার Gemini key দিন", Toast.LENGTH_LONG).show()
            } else {
                val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMicPerm) {
                    onStartVoiceService()
                } else {
                    val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                }
            }
        }
    }

    // Secondary screen back handler
    if (currentScreen != "home") {
        BackHandler {
            currentScreen = when (currentScreen) {
                "connections", "personality", "theme", "overlay", "automation", "permissions", "logs", "nickname", "about" -> "settings"
                "quick_actions", "website_builder", "sketch" -> "home"
                else -> "home"
            }
        }
    }

    val handleSendMessage: (String) -> Unit = { text ->
        scope.launch {
            // Save user message in Room
            val userMsg = ChatMessageEntity(conversationId = currentConvoId, sender = "user", message = text)
            database.chatMessageDao().insertMessage(userMsg)

            // Send via Groq/Gemini
            val history = chatMessages.map { Pair(it.sender, it.message) }
            val res = groqClient.sendMessage(history, text)

            if (res.errorMessage != null) {
                val errorMsg = ChatMessageEntity(conversationId = currentConvoId, sender = "maira", message = res.errorMessage)
                database.chatMessageDao().insertMessage(errorMsg)
            } else {
                val mairaMsg = ChatMessageEntity(conversationId = currentConvoId, sender = "maira", message = res.textResponse)
                database.chatMessageDao().insertMessage(mairaMsg)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentScreen in listOf("home", "chat", "triggers", "settings")) {
                MairaBottomBar(
                    currentScreen = currentScreen,
                    onNavigate = { currentScreen = it },
                    isSessionActive = isSessionActive,
                    onMicClick = { handleMicButtonClick() }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                "home" -> HomeScreen(
                    status = statusMessage,
                    micRms = micRms,
                    speakerRms = speakerRms,
                    userNickname = userNickname,
                    isSessionActive = isSessionActive,
                    onSendMessage = { handleSendMessage(it) },
                    onQuickActionClick = { key ->
                        when (key) {
                            "open_app" -> currentScreen = "quick_actions"
                            "make_call" -> currentScreen = "quick_actions"
                            "website_builder" -> currentScreen = "website_builder"
                            "sketch" -> currentScreen = "sketch"
                            "voice_talk" -> handleMicButtonClick()
                            else -> currentScreen = "chat"
                        }
                    },
                    onNavigateToQuickActions = { currentScreen = "quick_actions" },
                    onNavigateToConnections = { currentScreen = "connections" },
                    hasKeys = hasKeys
                )

                "chat" -> ChatScreen(
                    messages = chatMessages,
                    onSendMessage = { handleSendMessage(it) },
                    onNewChat = { currentConvoId = UUID.randomUUID().toString() },
                    onClearAll = {
                        scope.launch { database.chatMessageDao().clearAllMessages() }
                    },
                    conversationList = conversationIds,
                    onSelectConversation = { currentConvoId = it }
                )

                "triggers" -> TriggersScreen(
                    triggers = triggersList,
                    onAddTrigger = { trigger ->
                        scope.launch { database.triggerDao().insertTrigger(trigger) }
                    },
                    onToggleTrigger = { trigger ->
                        scope.launch { database.triggerDao().updateTrigger(trigger.copy(enabled = !trigger.enabled)) }
                    },
                    onDeleteTrigger = { trigger ->
                        scope.launch { database.triggerDao().deleteTrigger(trigger) }
                    }
                )

                "settings" -> SettingsScreen(
                    onNavigateToSubPage = { page -> currentScreen = page }
                )

                "quick_actions" -> QuickActionsScreen(
                    onActionClick = { key ->
                        when (key) {
                            "website_builder" -> currentScreen = "website_builder"
                            "sketch" -> currentScreen = "sketch"
                            "voice_talk" -> handleMicButtonClick()
                            "text_chat" -> currentScreen = "chat"
                            else -> handleMicButtonClick()
                        }
                    },
                    onBack = { currentScreen = "home" }
                )

                "website_builder" -> WebsiteBuilderScreen(
                    groqClient = groqClient,
                    onBack = { currentScreen = "home" }
                )

                "sketch" -> SketchScreen(
                    imageClient = imageClient,
                    onBack = { currentScreen = "home" }
                )

                "connections" -> ConnectionsScreen(
                    prefsManager = prefsManager,
                    onBack = { currentScreen = "settings" }
                )

                "personality" -> PersonalityScreen(
                    prefsManager = prefsManager,
                    onBack = { currentScreen = "settings" }
                )

                "theme" -> ThemeScreen(
                    prefsManager = prefsManager,
                    onBack = { currentScreen = "settings" }
                )

                "overlay" -> OverlaySettingsScreen(
                    prefsManager = prefsManager,
                    onBack = { currentScreen = "settings" }
                )

                "automation" -> MessageAutomationSettingsScreen(
                    prefsManager = prefsManager,
                    onBack = { currentScreen = "settings" }
                )

                "permissions" -> PermissionsScreen(
                    onBack = { currentScreen = "settings" }
                )

                "logs" -> LogsScreen(
                    onBack = { currentScreen = "settings" }
                )

                "nickname" -> NicknameScreen(
                    prefsManager = prefsManager,
                    onBack = { currentScreen = "settings" }
                )

                "about" -> AboutScreen(
                    onBack = { currentScreen = "settings" }
                )
            }
        }
    }
}
