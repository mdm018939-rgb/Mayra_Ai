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

    // Audio Track
    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlaying = false
    private var playbackThread: Thread? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private val stopWords = listOf("থামো", "থাম", "চুপ", "চুপ করো", "স্টপ", "বন্ধ করো")

    override fun onCreate() {
        super.onCreate()
        prefsManager = EncryptedPreferencesManager(this)
        toolExecutor = DeviceToolExecutor(this)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Maira:VoiceWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> startLiveSession()
            ACTION_STOP_SESSION -> stopLiveSession()
        }
        return START_NOT_STICKY
    }

    private fun startLiveSession() {
        val geminiKey = prefsManager.getGeminiKey()
        if (geminiKey.isBlank()) {
            _statusMessage.value = "প্রথমে সেটিংসে আপনার Gemini key দিন"
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Gemini Key খালি থাকার কারণে সেসন শুরু করা যায়নি")
            return
        }

        startForegroundServiceNotification()
        wakeLock?.acquire(30 * 60 * 1000L) // 30 mins max wake lock

        _sessionState.value = "সংযুক্ত হচ্ছে..."
        _statusMessage.value = "সংযুক্ত হচ্ছে..."

        val personality = prefsManager.getPersonality()
        val nickname = prefsManager.getNickname()
        val systemPrompt = PersonalityPrompts.getSystemInstruction(personality, nickname)

        initAudioTrack()

        liveClient = GeminiLiveWebSocketClient(
            apiKey = geminiKey,
            systemInstruction = systemPrompt,
            listener = createLiveListener()
        ).apply {
            connect()
        }

        if (prefsManager.isOverlayEnabled()) {
            showFloatingOverlay()
        }
    }

    private fun createLiveListener() = object : GeminiLiveWebSocketClient.LiveEventListener {
        override fun onSetupComplete() {
            _sessionState.value = "সংযুক্ত"
            _statusMessage.value = "শুনছি..."
            startAudioRecording()
        }

        override fun onAudioDataReceived(pcm24kBytes: ByteArray) {
            _statusMessage.value = "বলছি..."
            enqueueAudioPlayback(pcm24kBytes)
        }

        override fun onInputTranscription(text: String) {
            _lastInputTranscription.value = text
            MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.INFO, "শুনেছি: $text")

            // Check stop words
            if (stopWords.any { text.contains(it) }) {
                MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.INFO, "স্টপ ওয়াড চিহ্নিত হয়েছে: প্লেব্যাক বন্ধ করা হচ্ছে")
                flushAudioPlayback()
            }
        }

        override fun onOutputTranscription(text: String) {
            _lastOutputTranscription.value = text
        }

        override fun onInterrupted() {
            MairaLogger.log(MairaLogger.Category.AUDIO, MairaLogger.Level.INFO, "Gemini Live Interrupted event: প্লেব্যাক থামানো হচ্ছে")
            flushAudioPlayback()
            _statusMessage.value = "শুনছি..."
        }

        override fun onTurnComplete() {
            serviceScope.launch {
                delay(300)
                if (audioQueue.isEmpty()) {
                    _statusMessage.value = "শুনছি..."
                }
            }
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            _statusMessage.value = "ভাবছি..."
            serviceScope.launch(Dispatchers.IO) {
                val responseObj = executeToolCallInternal(name, args)
                liveClient?.sendToolResponse(id, name, responseObj)
            }
        }

        override fun onError(errorMessage: String) {
            _statusMessage.value = errorMessage
        }

        override fun onClosed(code: Int, reason: String) {
            if (_sessionState.value != "বন্ধ") {
                _statusMessage.value = "সংযোগ বিচ্ছিন্ন ($code)"
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
                    resultObj.put("result", res.message)
                }
                "openUrl" -> {
                    val url = args.optString("url", "")
                    val res = toolExecutor.openUrl(url)
                    resultObj.put("result", res.message)
                }
                "setFlashlight" -> {
                    val on = args.optBoolean("on", false)
                    val res = toolExecutor.setFlashlight(on)
                    resultObj.put("result", res.message)
                }
                "callContact" -> {
                    val contactName = args.optString("name", "")
                    val contacts = toolExecutor.searchContacts(contactName)
                    if (contacts.isNotEmpty()) {
                        val first = contacts.first()
                        val dialIntent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${first.number}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(dialIntent)
                        resultObj.put("result", "${first.name}-কে কল করতে ডায়াল প্যাড প্রস্তুত করা হয়েছে।")
                    } else {
                        resultObj.put("result", "'$contactName' নামে কোনো কন্টাক্ট পাওয়া যায়নি।")
                    }
                }
                "playYoutube" -> {
                    val query = args.optString("query", "")
                    val res = toolExecutor.playYoutube(query)
                    resultObj.put("result", res.message)
                }
                else -> {
                    resultObj.put("result", "টুল সফলভাবে সম্পন্ন হয়েছে।")
                }
            }
        } catch (e: Exception) {
            resultObj.put("result", "টুল কার্যকর করতে সমস্যা হয়েছে: ${e.message}")
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
                if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(audioSessionId)?.enabled = true
                if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(audioSessionId)?.enabled = true
                if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(audioSessionId)?.enabled = true
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
                        liveClient?.sendAudioChunk(pcmChunk)
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

    private fun initAudioTrack() {
        val sampleRate = 24000
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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

        // Route speakerphone
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speakerDevice != null) audioManager.setCommunicationDevice(speakerDevice)
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }

        audioTrack?.play()
        isPlaying = true

        playbackThread = Thread {
            while (isPlaying && !Thread.currentThread().isInterrupted) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    calculateSpeakerRms(chunk)
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    _speakerRmsFlow.value = 0f
                    try { Thread.sleep(20) } catch (e: InterruptedException) { break }
                }
            }
        }.apply { start() }
    }

    private fun enqueueAudioPlayback(pcm24kBytes: ByteArray) {
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
        isRecording = false
        recordThread?.interrupt()
        try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) {}
        audioRecord = null

        isPlaying = false
        playbackThread?.interrupt()
        try { audioTrack?.stop(); audioTrack?.release() } catch (e: Exception) {}
        audioTrack = null

        liveClient?.disconnect()
        liveClient = null

        removeFloatingOverlay()

        if (wakeLock?.isHeld == true) wakeLock?.release()

        _sessionState.value = "বন্ধ"
        _statusMessage.value = "প্রস্তুত, বলো..."
        _micRmsFlow.value = 0f
        _speakerRmsFlow.value = 0f

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "ভয়েস সেসন ও সার্ভিস সম্পূর্ণ বন্ধ করা হয়েছে")
    }

    private fun showFloatingOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = resources.displayMetrics
            val screenHeightPx = displayMetrics.heightPixels
            val heightPx = (screenHeightPx * 0.22f).toInt().coerceAtLeast(200)
            val widthPx = (heightPx * 0.65f).toInt() // Proportional width for standing figure

            val params = WindowManager.LayoutParams(
                widthPx, heightPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }

            val img = ImageView(this).apply {
                setImageResource(R.drawable.maira_body)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            img.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager?.updateViewLayout(img, params)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = Math.abs(event.rawX - initialTouchX)
                            val diffY = Math.abs(event.rawY - initialTouchY)
                            if (diffX < 10 && diffY < 10) {
                                // Single tap -> stop current speech
                                flushAudioPlayback()
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            overlayView = img
            windowManager?.addView(overlayView, params)
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান অ্যাভাটার ওভারলে যোগ করা হয়েছে")
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.ERROR, "ওভারলে উইন্ডো যোগ করতে ব্যর্থ: ${e.message}")
        }
    }

    private fun removeFloatingOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
        }
        overlayView = null
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
