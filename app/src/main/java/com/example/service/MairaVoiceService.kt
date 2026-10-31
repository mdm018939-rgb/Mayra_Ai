package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.*
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.api.GeminiLiveWebSocketClient
import com.example.data.EncryptedPreferencesManager
import com.example.data.MairaLogger
import com.example.personality.PersonalityPrompts
import com.example.tools.DeviceToolExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class MairaVoiceService : Service() {

    companion object {
        const val ACTION_START_SESSION = "ACTION_START_SESSION"
        const val ACTION_STOP_SESSION = "ACTION_STOP_SESSION"
        const val CHANNEL_ID = "maira_voice_channel"
        const val NOTIFICATION_ID = 1001

        private val _micRmsFlow = MutableStateFlow(0f)
        val micRmsFlow: StateFlow<Float> = _micRmsFlow.asStateFlow()

        private val _speakerRmsFlow = MutableStateFlow(0f)
        val speakerRmsFlow: StateFlow<Float> = _speakerRmsFlow.asStateFlow()

        private val _sessionState = MutableStateFlow("বন্ধ")
        val sessionState: StateFlow<String> = _sessionState.asStateFlow()

        private val _statusMessage = MutableStateFlow("প্রস্তুত, বলো...")
        val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

        private val _lastInputTranscription = MutableStateFlow("")
        val lastInputTranscription: StateFlow<String> = _lastInputTranscription.asStateFlow()

        private val _lastOutputTranscription = MutableStateFlow("")
        val lastOutputTranscription: StateFlow<String> = _lastOutputTranscription.asStateFlow()

        private val _lastActionResult = MutableStateFlow<Pair<String, Long>?>(null)
        val lastActionResult: StateFlow<Pair<String, Long>?> = _lastActionResult.asStateFlow()

        fun setActionResult(status: String) {
            _lastActionResult.value = Pair(status, System.currentTimeMillis())
        }

        fun isSessionActive(): Boolean = _sessionState.value == "সংযুক্ত" || _sessionState.value == "সংযুক্ত হচ্ছে..."
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefsManager: EncryptedPreferencesManager
    private lateinit var toolExecutor: DeviceToolExecutor
    private var liveClient: GeminiLiveWebSocketClient? = null

    // Audio Record
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordThread: Thread? = null

    // Audio FX References
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    // Audio Routing Saved States
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsSpeakerphoneOn: Boolean = false
    private var isAudioModeSaved: Boolean = false

    // Audio Track
    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlaying = false
    private var playbackThread: Thread? = null
    @Volatile private var isServerTurnComplete = false

    private var wakeLock: PowerManager.WakeLock? = null

    private val explicitStopCommands = setOf("থামো", "থাম", "থামুন", "চুপ", "চুপ করো", "স্টপ", "stop")

    private fun isExplicitStopCommand(text: String): Boolean {
        val clean = text.trim().lowercase().replace("[.,?!।\"']".toRegex(), "")
        if (clean in explicitStopCommands) return true
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return words.size in 1..3 && words.any { it in explicitStopCommands }
    }

    private var personalityCollectorJob: Job? = null

    @OptIn(FlowPreview::class)
    private fun observePersonalityChanges() {
        personalityCollectorJob?.cancel()
        personalityCollectorJob = serviceScope.launch {
            var currentPersonality = prefsManager.getPersonality()
            prefsManager.personalityFlow
                .debounce(1000L)
                .collect { newPersonality ->
                    if (!isSessionActive()) {
                        currentPersonality = newPersonality
                        return@collect
                    }

                    if (newPersonality == currentPersonality) return@collect

                    MairaLogger.log(
                        MairaLogger.Category.UI,
                        MairaLogger.Level.INFO,
                        "চলমান ভয়েস সেসনে পারসোনালিটি পরিবর্তন সনাক্ত: $newPersonality"
                    )

                    // Wait if Maira is currently speaking or processing audio/tools
                    while (isSessionActive() && (isPlaying || audioQueue.isNotEmpty() || _statusMessage.value == "বলছি..." || _statusMessage.value == "ভাবছি...")) {
                        delay(300L)
                    }

                    if (isSessionActive()) {
                        currentPersonality = newPersonality
                        val geminiKey = prefsManager.getGeminiKeys().firstOrNull() ?: prefsManager.getGeminiKey()
                        if (geminiKey.isNotBlank()) {
                            MairaLogger.log(
                                MairaLogger.Category.CONNECTION,
                                MairaLogger.Level.INFO,
                                "নতুন পারসোনালিটি নিয়ে অটো-রিকানেক্ট করা হচ্ছে..."
                            )
                            connectLiveClient(geminiKey)
                        }
                    }
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = EncryptedPreferencesManager(this)
        toolExecutor = DeviceToolExecutor(this)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Maira:VoiceWakeLock")

        observePersonalityChanges()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> startLiveSession()
            ACTION_STOP_SESSION -> stopLiveSession()
        }
        return START_NOT_STICKY
    }

    private var voiceGeminiAttempts = 0

    private fun startLiveSession() {
        if (isSessionActive() || liveClient != null) {
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "ভয়েস সেসন ইতিমধ্যেই সক্রিয় রয়েছে (Idempotent call)")
            return
        }

        voiceGeminiAttempts = 0
        val geminiKey = prefsManager.getNextGeminiKey().ifBlank { prefsManager.getGeminiKey() }
        if (geminiKey.isBlank()) {
            _statusMessage.value = "প্রথমে সেটিংসে আপনার Gemini key দিন"
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Gemini Key খালি থাকার কারণে সেসন শুরু করা যায়নি")
            return
        }

        startForegroundServiceNotification()
        wakeLock?.acquire(30 * 60 * 1000L) // 30 mins max wake lock

        initAudioTrack()
        connectLiveClient(geminiKey)

        if (prefsManager.isOverlayEnabled() && android.provider.Settings.canDrawOverlays(this)) {
            try {
                MairaOverlayService.startService(this)
            } catch (e: Exception) {
                MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.WARN, "ভয়েস সেসনের সাথে ভাসমান ওভারলে চালু করতে সমস্যা: ${e.message}")
            }
        }
    }

    private fun connectLiveClient(apiKey: String) {
        _sessionState.value = "সংযুক্ত হচ্ছে..."
        _statusMessage.value = "সংযুক্ত হচ্ছে..."

        val personality = prefsManager.getPersonality()
        val nickname = prefsManager.getNickname()
        val systemPrompt = PersonalityPrompts.getSystemInstruction(personality, nickname)

        try {
            liveClient?.disconnect()
        } catch (_: Exception) {}

        liveClient = GeminiLiveWebSocketClient(
            apiKey = apiKey,
            systemInstruction = systemPrompt,
            listener = createLiveListener()
        ).apply {
            connect()
        }
    }

    private var singleKeyReconnectCount = 0
    private var thinkingWatchdogJob: Job? = null

    private fun tryNextKeyAndReconnect(triggerReason: String) {
        val keys = prefsManager.getGeminiKeys()
        val currentKey = prefsManager.getGeminiKey()

        if (keys.size > 1) {
            if (voiceGeminiAttempts >= keys.size) {
                MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.ERROR, "সকল Gemini Live Key চেষ্টা করা হয়েছে, কোনোটি কাজ করছে না")
                _statusMessage.value = "সংযোগ বিচ্ছিন্ন হয়েছে, আবার চেষ্টা করুন"
                voiceGeminiAttempts = 0
                return
            }
            voiceGeminiAttempts++
            val nextKey = prefsManager.getNextGeminiKey()
            val last4 = if (nextKey.length >= 4) nextKey.takeLast(4) else "XXXX"
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Gemini Live কী সমস্যা ($triggerReason)। পরবর্তী কী দিয়ে পুনঃচেষ্টা করা হচ্ছে (...$last4)...")
            _statusMessage.value = "পরবর্তী Gemini key চেষ্টা করা হচ্ছে..."

            serviceScope.launch {
                delay(2000L)
                connectLiveClient(nextKey)
            }
        } else {
            // Single key mode
            if (singleKeyReconnectCount >= 3) {
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "একই Key দিয়ে ৩ বার পুনঃসংযোগের চেষ্টা ফেইল করেছে")
                _statusMessage.value = "সংযোগ বিচ্ছিন্ন হয়েছে, আবার চেষ্টা করুন"
                singleKeyReconnectCount = 0
                return
            }
            singleKeyReconnectCount++
            val delayMs = (1000L + singleKeyReconnectCount * 1000L).coerceIn(2000L, 4000L)
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.WARN, "Gemini Live পুনঃসংযোগ চেষ্টা ($singleKeyReconnectCount/3) ($triggerReason)...")
            _statusMessage.value = "পুনঃসংযোগ করা হচ্ছে ($singleKeyReconnectCount/3)..."

            serviceScope.launch {
                delay(delayMs)
                if (currentKey.isNotBlank()) {
                    connectLiveClient(currentKey)
                } else {
                    _statusMessage.value = "সংযোগ বিচ্ছিন্ন হয়েছে, আবার চেষ্টা করুন"
                }
            }
        }
    }

    private fun reconnectSameKey(triggerReason: String) {
        val currentKey = prefsManager.getGeminiKey()
        if (singleKeyReconnectCount >= 3) {
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "সার্ভার এররে ৩ বার পুনঃসংযোগের চেষ্টা ফেইল করেছে")
            _statusMessage.value = "সংযোগ বিচ্ছিন্ন হয়েছে, আবার চেষ্টা করুন"
            singleKeyReconnectCount = 0
            return
        }
        singleKeyReconnectCount++
        val delayMs = (1000L + singleKeyReconnectCount * 1000L).coerceIn(2000L, 4000L)
        MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.WARN, "সার্ভার এরর/ক্লোজ পুনঃসংযোগ ($singleKeyReconnectCount/3) ($triggerReason)...")
        _statusMessage.value = "পুনঃসংযোগ করা হচ্ছে ($singleKeyReconnectCount/3)..."

        serviceScope.launch {
            delay(delayMs)
            if (currentKey.isNotBlank()) {
                connectLiveClient(currentKey)
            } else {
                _statusMessage.value = "সংযোগ বিচ্ছিন্ন হয়েছে, আবার চেষ্টা করুন"
            }
        }
    }

    private fun createLiveListener() = object : GeminiLiveWebSocketClient.LiveEventListener {
        override fun onSetupComplete() {
            thinkingWatchdogJob?.cancel()
            voiceGeminiAttempts = 0
            singleKeyReconnectCount = 0
            _sessionState.value = "সংযুক্ত"
            _statusMessage.value = "শুনছি..."
            isServerTurnComplete = false
            startAudioRecording()
        }

        override fun onAudioDataReceived(pcm24kBytes: ByteArray) {
            thinkingWatchdogJob?.cancel()
            isServerTurnComplete = false
            _statusMessage.value = "বলছি..."
            enqueueAudioPlayback(pcm24kBytes)
        }

        override fun onInputTranscription(text: String) {
            _lastInputTranscription.value = text
            MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.INFO, "শুনেছি: $text")

            // Only stop if user explicitly uttered an intentional stop command
            if (isExplicitStopCommand(text)) {
                MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.INFO, "স্পষ্ট স্টপ কমান্ড পাওয়া গেছে: প্লেব্যাক বন্ধ করা হচ্ছে")
                flushAudioPlayback()
                isServerTurnComplete = true
                _statusMessage.value = "শুনছি..."
            }
        }

        override fun onOutputTranscription(text: String) {
            _lastOutputTranscription.value = text
        }

        override fun onInterrupted() {
            thinkingWatchdogJob?.cancel()
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.INFO, "Gemini Live Interrupted event গ্রহণ করা হয়েছে (বর্তমান বাফার সমাপ্ত হবে)")
            isServerTurnComplete = true
        }

        override fun onTurnComplete() {
            thinkingWatchdogJob?.cancel()
            isServerTurnComplete = true
            if (audioQueue.isEmpty()) {
                _statusMessage.value = "শুনছি..."
            }
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            _statusMessage.value = "ভাবছি..."
            thinkingWatchdogJob?.cancel()

            serviceScope.launch(Dispatchers.IO) {
                val responseObj = try {
                    withTimeout(8000L) {
                        executeToolCallInternal(name, args)
                    }
                } catch (e: TimeoutCancellationException) {
                    MairaLogger.log(MairaLogger.Category.TOOL, MairaLogger.Level.ERROR, "টুল কল টাইমআউট (8 সে.): $name")
                    JSONObject().apply {
                        put("status", DeviceToolExecutor.ToolStatus.UNKNOWN.name)
                        put("message", "টুল পরিচালনায় সময় পার হয়ে গেছে।")
                    }
                } catch (e: Exception) {
                    MairaLogger.log(MairaLogger.Category.TOOL, MairaLogger.Level.ERROR, "টুল কলের ত্রুটি: ${e.message}")
                    JSONObject().apply {
                        put("status", DeviceToolExecutor.ToolStatus.FAILED.name)
                        put("message", "টুল পরিচালনায় সমস্যা হয়েছে: ${e.message}")
                    }
                }

                liveClient?.sendToolResponse(id, name, responseObj)

                // Start 10-second watchdog after sending tool response
                thinkingWatchdogJob?.cancel()
                thinkingWatchdogJob = serviceScope.launch {
                    delay(10000L)
                    if (_statusMessage.value == "ভাবছি...") {
                        _statusMessage.value = "শুনছি..."
                        MairaLogger.log(MairaLogger.Category.TOOL, MairaLogger.Level.WARN, "'ভাবছি...' ওয়াচডগ টাইমআউট (১০ সে.), 'শুনছি...' স্টেটে ফিরে যাওয়া হয়েছে")
                    }
                }
            }
        }

        override fun onError(errorMessage: String) {
            _statusMessage.value = errorMessage
            val isQuotaOrAuth = errorMessage.contains("429") ||
                    errorMessage.contains("403") ||
                    errorMessage.contains("401") ||
                    errorMessage.contains("quota", ignoreCase = true) ||
                    errorMessage.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    errorMessage.contains("API_KEY_INVALID", ignoreCase = true) ||
                    errorMessage.contains("API key not valid", ignoreCase = true)
            val isServerError = errorMessage.contains("503") ||
                    errorMessage.contains("1011") ||
                    errorMessage.contains("500")

            if (isQuotaOrAuth) {
                tryNextKeyAndReconnect(errorMessage)
            } else if (isServerError) {
                reconnectSameKey(errorMessage)
            }
        }

        override fun onClosed(code: Int, reason: String) {
            if (_sessionState.value != "বন্ধ") {
                _statusMessage.value = "সংযোগ বিচ্ছিন্ন ($code)"
                val isQuotaOrAuth = code == 4003 || code == 4001 || code == 4429 ||
                        reason.contains("quota", ignoreCase = true) ||
                        reason.contains("exhausted", ignoreCase = true)
                val isServerErrorOrNormalClose = code == 503 || code == 1011 || code == 1006 || code == 1000 || reason.contains("goAway", ignoreCase = true)

                if (isQuotaOrAuth) {
                    tryNextKeyAndReconnect("Closed code=$code reason=$reason")
                } else if (isServerErrorOrNormalClose) {
                    reconnectSameKey("Closed code=$code reason=$reason")
                }
            }
        }
    }

    private suspend fun executeToolCallInternal(name: String, args: JSONObject): JSONObject {
        val resultObj = JSONObject()
        try {
            when (name) {
                "openApp" -> {
                    val appName = args.optString("appName", "")
                    val res = toolExecutor.openApp(appName)
                    resultObj.put("status", res.status.name)
                    resultObj.put("message", res.message)
                }
                "openUrl" -> {
                    val url = args.optString("url", "")
                    val res = toolExecutor.openUrl(url)
                    resultObj.put("status", res.status.name)
                    resultObj.put("message", res.message)
                }
                "setFlashlight" -> {
                    val on = args.optBoolean("on", false)
                    val res = toolExecutor.setFlashlight(on)
                    resultObj.put("status", res.status.name)
                    resultObj.put("message", res.message)
                }
                "callContact" -> {
                    if (!android.provider.Settings.canDrawOverlays(this)) {
                        resultObj.put("status", DeviceToolExecutor.ToolStatus.UNAVAILABLE.name)
                        resultObj.put("message", "অন্যান্য অ্যাপের উপরে দেখানোর (Display over other apps) অনুমতি দেওয়া নেই। সেটিংসে গিয়ে অনুমতি দিন।")
                    } else {
                        val contactName = args.optString("name", "")
                        val nameEnglish = args.optString("nameEnglish", "").ifBlank { null }
                        val contacts = toolExecutor.searchContacts(contactName, nameEnglish)
                        if (contacts.isEmpty()) {
                            resultObj.put("status", DeviceToolExecutor.ToolStatus.FAILED.name)
                            resultObj.put("message", "'$contactName' নামে কোনো কন্টাক্ট খুঁজে পাওয়া যায়নি।")
                        } else if (contacts.size > 1) {
                            val candidateNames = contacts.map { it.name }.distinct().take(4).joinToString(", ")
                            resultObj.put("status", DeviceToolExecutor.ToolStatus.UNKNOWN.name)
                            resultObj.put("message", "'$contactName' নামে একাধিক কন্টাক্ট পাওয়া গেছে: $candidateNames। কার কাছে কল করতে চান স্পষ্ট করে বলুন।")
                        } else {
                            val first = contacts.first()
                            val dialIntent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${first.number}")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "before startActivity (callContact: ${first.name}, overlayRunning=${com.example.service.MairaOverlayService.isOverlayShowing})")
                                startActivity(dialIntent)
                                val service = MairaAccessibilityService.instance
                                var isOpened = false
                                if (service != null && MairaAccessibilityService.isServiceConnected) {
                                    for (i in 0 until 5) {
                                        delay(300)
                                        val fgPkg = service.getForegroundPackage()
                                        MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "callContact polling attempt $i: foregroundPackage='$fgPkg'")
                                        if (fgPkg.isNotBlank() && (fgPkg.lowercase().contains("dialer") || fgPkg.lowercase().contains("phone") || fgPkg.lowercase() != packageName.lowercase())) {
                                            isOpened = true
                                            break
                                        }
                                    }
                                }
                                if (isOpened) {
                                    resultObj.put("status", DeviceToolExecutor.ToolStatus.SUCCESS.name)
                                    resultObj.put("message", "${first.name}-কে কল করতে ডায়াল প্যাড প্রস্তুত করা হয়েছে।")
                                } else {
                                    toolExecutor.postActionNotification(
                                        "${first.name}-কে কল করতে স্পর্শ করুন",
                                        "ডায়াল প্যাড খুলতে নোটিফিকেশনে ট্যাপ করুন।",
                                        dialIntent
                                    )
                                    resultObj.put("status", DeviceToolExecutor.ToolStatus.UNKNOWN.name)
                                    resultObj.put("message", "ডায়াল প্যাড সরাসরি খোলা সম্ভব হয়নি। একটি নোটিফিকেশন পাঠানো হয়েছে, সেটিতে ট্যাপ করুন।")
                                }
                                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "callContact final result: status=${resultObj.optString("status")}, message='${resultObj.optString("message")}'")
                            } catch (e: Exception) {
                                resultObj.put("status", DeviceToolExecutor.ToolStatus.FAILED.name)
                                resultObj.put("message", "ডায়ালার খোলা সম্ভব হয়নি: ${e.message}")
                                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "callContact final result: status=${resultObj.optString("status")}, message='${resultObj.optString("message")}'")
                            }
                        }
                    }
                }
                "playYoutube" -> {
                    val query = args.optString("query", "")
                    val res = toolExecutor.playYoutube(query)
                    resultObj.put("status", res.status.name)
                    resultObj.put("message", res.message)
                }
                "sendWhatsAppMessage", "sendMessage", "send_whatsapp_message" -> {
                    val contactName = args.optString("contactName", "")
                        .ifBlank { args.optString("recipientName", "") }
                        .ifBlank { args.optString("name", "") }
                        .ifBlank { args.optString("phone", "") }
                        .ifBlank { args.optString("recipient", "") }
                    val text = args.optString("text", "")
                        .ifBlank { args.optString("message", "") }
                        .ifBlank { args.optString("body", "") }
                        .ifBlank { args.optString("content", "") }
                    val nameEnglish = args.optString("nameEnglish", "").ifBlank { null }
                    val res = toolExecutor.sendWhatsAppMessage(
                        contactName,
                        text,
                        prefsManager.isAskBeforeSending(),
                        nameEnglish
                    )
                    resultObj.put("status", res.status.name)
                    resultObj.put("message", res.message)
                }
                "sendMessengerMessage", "send_messenger_message" -> {
                    val recipientName = args.optString("recipientName", "")
                        .ifBlank { args.optString("contactName", "") }
                        .ifBlank { args.optString("name", "") }
                    val text = args.optString("text", "")
                        .ifBlank { args.optString("message", "") }
                    val nameEnglish = args.optString("nameEnglish", "")
                        .ifBlank { args.optString("recipientNameEnglish", "") }
                        .ifBlank { null }
                    val res = toolExecutor.sendMessengerMessage(
                        recipientName,
                        nameEnglish,
                        text
                    )
                    resultObj.put("status", res.status.name)
                    resultObj.put("message", res.message)
                }
                "goBack", "back", "go_back" -> {
                    val service = MairaAccessibilityService.instance
                    if (service == null) {
                        resultObj.put("status", DeviceToolExecutor.ToolStatus.UNAVAILABLE.name)
                        resultObj.put("message", "অ্যাক্সেসিবিলিটি সার্ভিস সক্রিয় নেই। সেটিংসে গিয়ে সেবাটি সক্রিয় করুন।")
                    } else {
                        val ok = service.goBack()
                        if (ok) {
                            resultObj.put("status", DeviceToolExecutor.ToolStatus.SUCCESS.name)
                            resultObj.put("message", "পূর্ববর্তী স্ক্রিনে ফিরে যাওয়া হয়েছে।")
                        } else {
                            resultObj.put("status", DeviceToolExecutor.ToolStatus.FAILED.name)
                            resultObj.put("message", "ব্যাক অ্যাকশন সম্পাদন করা সম্ভব হয়নি।")
                        }
                    }
                }
                else -> {
                    resultObj.put("status", DeviceToolExecutor.ToolStatus.UNAVAILABLE.name)
                    resultObj.put("message", "'$name' টুলটি বর্তমানে আপনার সিস্টেমে পাওয়া যাচ্ছে না।")
                }
            }
        } catch (e: Exception) {
            resultObj.put("status", DeviceToolExecutor.ToolStatus.FAILED.name)
            resultObj.put("message", "টুল পরিচালনায় সমস্যা হয়েছে: ${e.message}")
        }
        val statusName = resultObj.optString("status", "")
        if (statusName == DeviceToolExecutor.ToolStatus.SUCCESS.name) {
            setActionResult("SUCCESS")
        } else if (statusName == DeviceToolExecutor.ToolStatus.FAILED.name || statusName == DeviceToolExecutor.ToolStatus.UNAVAILABLE.name) {
            setActionResult("ERROR")
        }
        return resultObj
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        if (isRecording) return
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            // Audio FX
            val audioSessionId = audioRecord?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
                    }
                } catch (e: Exception) {
                    MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "AcousticEchoCanceler error: ${e.message}")
                }
                try {
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                    }
                } catch (e: Exception) {
                    MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "NoiseSuppressor error: ${e.message}")
                }
                try {
                    if (AutomaticGainControl.isAvailable()) {
                        automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
                    }
                } catch (e: Exception) {
                    MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "AutomaticGainControl error: ${e.message}")
                }
            }

            audioRecord?.startRecording()
            isRecording = true

            recordThread = Thread {
                val buffer = ByteArray(640) // 20ms chunk at 16kHz PCM16 mono
                var lastLogTime = System.currentTimeMillis()

                while (isRecording && !Thread.currentThread().isInterrupted) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val pcmChunk = buffer.copyOf(read)
                        calculateMicRms(pcmChunk)

                        // Prevent speaker echo and acoustic feedback from interrupting Maira's speech
                        val isMairaSpeaking = _statusMessage.value == "বলছি..." || audioQueue.isNotEmpty() || _speakerRmsFlow.value > 0.02f

                        if (!isMairaSpeaking) {
                            // Normal listening: forward microphone audio chunk to Gemini Live
                            liveClient?.sendAudioChunk(pcmChunk)
                        } else {
                            // Maira is speaking: protect ongoing playback from speakerphone echo / background noise.
                            // Only forward to Gemini Live if strong intentional user speech is detected above speaker feedback
                            val micLevel = _micRmsFlow.value
                            val speakerLevel = _speakerRmsFlow.value
                            if (micLevel > 0.35f && micLevel > speakerLevel * 1.25f) {
                                liveClient?.sendAudioChunk(pcmChunk)
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= 1000) {
                        lastLogTime = now
                        // 1s summary log
                    }
                }
            }.apply { start() }

            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.INFO, "মাইক্রোফোন স্ট্রিম শুরু হয়েছে (16kHz PCM16)")
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.ERROR, "মাইক্রোফোন রেকর্ডিং শুরু করতে ব্যর্থ: ${e.message}")
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes.USAGE_ASSISTANT
            } else {
                AudioAttributes.USAGE_MEDIA
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { _ -> }
                    .build()

                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { _ -> },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "Audio focus অনুরোধ করতে ব্যর্থ: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { _ -> }
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "Audio focus ত্যাগ করতে ব্যর্থ: ${e.message}")
        }
    }

    private fun setupAudioRoutingForSession() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!isAudioModeSaved) {
                savedAudioMode = audioManager.mode
                @Suppress("DEPRECATION")
                savedIsSpeakerphoneOn = audioManager.isSpeakerphoneOn
                isAudioModeSaved = true
            }

            // Set MODE_IN_COMMUNICATION for hardware AEC
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Route audio output to speakerphone so playback doesn't go to earpiece
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                } catch (e: Exception) {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "Audio routing setup সমস্যা: ${e.message}")
        }
    }

    private fun restoreAudioRoutingAfterSession() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                } catch (_: Exception) {}
            }
            if (isAudioModeSaved) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = savedIsSpeakerphoneOn
                audioManager.mode = savedAudioMode
                isAudioModeSaved = false
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "Audio routing restore সমস্যা: ${e.message}")
        }
    }

    private fun initAudioTrack() {
        val sampleRate = 24000
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes.USAGE_ASSISTANT
        } else {
            AudioAttributes.USAGE_MEDIA
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            minBufferSize * 4,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        // Request audio focus and setup audio mode/speakerphone routing
        requestAudioFocus()
        setupAudioRoutingForSession()

        audioTrack?.play()
        isPlaying = true

        playbackThread = Thread {
            var emptyQueueStartTime = 0L
            while (isPlaying && !Thread.currentThread().isInterrupted) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    emptyQueueStartTime = 0L
                    calculateSpeakerRms(chunk)
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    _speakerRmsFlow.value = 0f
                    // If server completed its turn and all queued audio finished playing, return cleanly to listening state
                    if (isServerTurnComplete && _statusMessage.value == "বলছি...") {
                        _statusMessage.value = "শুনছি..."
                        isServerTurnComplete = false
                        emptyQueueStartTime = 0L
                        MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.INFO, "মাইরা উত্তর বলা সম্পন্ন করেছে, শুনছি...")
                    } else if (_statusMessage.value == "বলছি...") {
                        // Watchdog: If audioQueue is empty and state is "বলছি...", check 2s threshold
                        if (emptyQueueStartTime == 0L) {
                            emptyQueueStartTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - emptyQueueStartTime >= 2000L) {
                            _statusMessage.value = "শুনছি..."
                            isServerTurnComplete = false
                            emptyQueueStartTime = 0L
                            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.WARN, "'বলছি...' ওয়াচডগ টাইমআউট (২ সে.), 'শুনছি...' স্টেটে ফিরে যাওয়া হয়েছে")
                        }
                    } else {
                        emptyQueueStartTime = 0L
                    }
                    try { Thread.sleep(20) } catch (e: InterruptedException) { break }
                }
            }
        }.apply { start() }
    }

    private fun enqueueAudioPlayback(pcm24kBytes: ByteArray) {
        if (audioQueue.size > 150) {
            while (audioQueue.size > 100) {
                audioQueue.poll()
            }
        }
        audioQueue.add(pcm24kBytes)
    }

    private fun flushAudioPlayback() {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {}
        _speakerRmsFlow.value = 0f
        isServerTurnComplete = false
    }

    private fun calculateMicRms(buffer: ByteArray) {
        var sum = 0.0
        val samples = buffer.size / 2
        for (i in 0 until samples) {
            val sample = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = if (samples > 0) sqrt(sum / samples).toFloat() else 0f
        _micRmsFlow.value = (rms / 32768f).coerceIn(0f, 1f)
    }

    private fun calculateSpeakerRms(buffer: ByteArray) {
        var sum = 0.0
        val samples = buffer.size / 2
        for (i in 0 until samples) {
            val sample = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = if (samples > 0) sqrt(sum / samples).toFloat() else 0f
        _speakerRmsFlow.value = (rms / 32768f).coerceIn(0f, 1f)
    }

    fun stopLiveSession() {
        if (_sessionState.value == "বন্ধ" && liveClient == null && !isRecording) {
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "ভয়েস সেসন ইতিমধ্যেই বন্ধ রয়েছে (Idempotent call)")
            return
        }

        isRecording = false
        recordThread?.interrupt()
        try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) {}
        audioRecord = null

        try { acousticEchoCanceler?.release() } catch (_: Exception) {}
        acousticEchoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try { automaticGainControl?.release() } catch (_: Exception) {}
        automaticGainControl = null

        isPlaying = false
        playbackThread?.interrupt()
        try { audioTrack?.stop(); audioTrack?.release() } catch (e: Exception) {}
        audioTrack = null

        abandonAudioFocus()
        restoreAudioRoutingAfterSession()

        liveClient?.disconnect()
        liveClient = null

        if (wakeLock?.isHeld == true) wakeLock?.release()

        _sessionState.value = "বন্ধ"
        _statusMessage.value = "মাইক বন্ধ করা হয়েছে"
        _micRmsFlow.value = 0f
        _speakerRmsFlow.value = 0f
        isServerTurnComplete = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (!prefsManager.isOverlayEnabled()) {
            try {
                MairaOverlayService.stopService(this)
            } catch (e: Exception) {
                MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.WARN, "ভাসমান ওভারলে বন্ধ করতে সমস্যা: ${e.message}")
            }
        }

        MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "ভয়েস সেসন ও সার্ভিস সম্পূর্ণ বন্ধ করা হয়েছে")
    }


    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, MairaVoiceService::class.java).apply { action = ACTION_STOP_SESSION }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("মাইরা চলছে")
            .setContentText("আপনার ভয়েস নির্দেশনা শুনছে ও কথা বলছে")
            .setSmallIcon(R.drawable.maira_app_icon_1793185004076)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "বন্ধ করুন", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Maira Voice Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopLiveSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
