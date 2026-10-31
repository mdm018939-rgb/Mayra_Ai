package com.example.api

import android.util.Base64
import com.example.data.MairaLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveWebSocketClient(
    private val apiKey: String,
    private val systemInstruction: String,
    private val listener: LiveEventListener
) {
    interface LiveEventListener {
        fun onSetupComplete()
        fun onAudioDataReceived(pcm24kBytes: ByteArray)
        fun onInputTranscription(text: String)
        fun onOutputTranscription(text: String)
        fun onInterrupted()
        fun onTurnComplete()
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onError(errorMessage: String)
        fun onClosed(code: Int, reason: String)
    }

    companion object {
        const val LIVE_MODEL = "models/gemini-3.1-flash-live-preview"
        private const val BASE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var setupTimeoutJob: Job? = null
    private var autoReconnectCount = 0
    private var isConnected = false
    private var isClosedIntentionally = false

    private val _connectionState = MutableStateFlow("বিচ্ছিন্ন")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    fun connect() {
        if (apiKey.isBlank()) {
            listener.onError("প্রথমে সেটিংসে আপনার Gemini key দিন")
            return
        }

        isClosedIntentionally = false
        _connectionState.value = "সংযুক্ত হচ্ছে..."
        MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "Gemini Live সংযোগ শুরু করা হচ্ছে...")

        val url = "$BASE_WS_URL?key=$apiKey"
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()

        setupTimeoutJob?.cancel()
        setupTimeoutJob = clientScope.launch {
            delay(15000)
            if (!isConnected) {
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "15 সেকেন্ডের মধ্যে setupComplete পাওয়া যায়নি")
                listener.onError("Gemini Live সংযোগ সময় পার হয়ে গেছে (Timeout)")
                disconnect()
            }
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "WebSocket সংযোগ খোলা হয়েছে, Setup বার্তা পাঠানো হচ্ছে...")
                sendSetupMessage(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleJsonMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleJsonMessage(bytes.utf8())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                setupTimeoutJob?.cancel()
                isConnected = false
                _connectionState.value = "সংযোগ ব্যর্থ"
                val rawError = t.localizedMessage ?: "অজানা নেটওয়ার্ক ত্রুটি"
                val isNetworkIssue = rawError.contains("Unable to resolve host", ignoreCase = true) ||
                        rawError.contains("UnknownHostException", ignoreCase = true) ||
                        rawError.contains("SocketTimeoutException", ignoreCase = true) ||
                        rawError.contains("ConnectException", ignoreCase = true) ||
                        rawError.contains("Failed to connect", ignoreCase = true) ||
                        rawError.contains("SSLHandshakeException", ignoreCase = true) ||
                        t is java.net.UnknownHostException ||
                        t is java.net.SocketTimeoutException ||
                        t is java.net.ConnectException

                val userFacingError = if (isNetworkIssue) {
                    "আপনার ইন্টারনেট সংযোগ চেক করুন"
                } else {
                    "Gemini Live ত্রুটি: $rawError"
                }

                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "WebSocket ব্যর্থতা: $rawError")
                listener.onError(userFacingError)

                attemptAutoReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                setupTimeoutJob?.cancel()
                isConnected = false
                _connectionState.value = "বিচ্ছিন্ন"
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.WARN, "WebSocket বন্ধ হয়েছে (code=$code, reason=$reason)")
                listener.onClosed(code, reason)

                if (!isClosedIntentionally) {
                    attemptAutoReconnect()
                }
            }
        })
    }

    private fun attemptAutoReconnect() {
        if (autoReconnectCount < 3 && !isClosedIntentionally) {
            autoReconnectCount++
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "স্বয়ংক্রিয় পুনঃসংযোগ চেষ্টা করা হচ্ছে ($autoReconnectCount/3)...")
            clientScope.launch {
                delay(2000)
                connect()
            }
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", LIVE_MODEL)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Kore") // Sweet female prebuilt voice
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", getFunctionDeclarations())
                        })
                    })
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                })
            }

            ws.send(setupJson.toString())
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "Setup বার্তা তৈরি করতে ব্যর্থ: ${e.message}")
        }
    }

    private fun handleJsonMessage(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)

            // 1. Check setupComplete
            if (root.has("setupComplete") || root.has("setup_complete")) {
                setupTimeoutJob?.cancel()
                isConnected = true
                autoReconnectCount = 0
                _connectionState.value = "সংযুক্ত"
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "SetupComplete সম্পন্ন হয়েছে")
                listener.onSetupComplete()
                return
            }

            // 2. Check serverContent / server_content
            val serverContent = root.optJSONObject("serverContent") ?: root.optJSONObject("server_content")
            if (serverContent != null) {
                if (serverContent.optBoolean("interrupted", false)) {
                    listener.onInterrupted()
                }

                val modelTurn = serverContent.optJSONObject("modelTurn") ?: serverContent.optJSONObject("model_turn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", inlineData.optString("mime_type", ""))
                                val dataBase64 = inlineData.optString("data", "")
                                if (dataBase64.isNotBlank()) {
                                    val decodedBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                    listener.onAudioDataReceived(decodedBytes)
                                }
                            }
                            if (part.has("text")) {
                                val text = part.optString("text")
                                if (text.isNotBlank()) {
                                    listener.onOutputTranscription(text)
                                }
                            }
                        }
                    }
                }

                val inputTrans = serverContent.optJSONObject("inputTranscription") ?: serverContent.optJSONObject("input_transcription")
                if (inputTrans != null) {
                    val text = inputTrans.optString("text", "")
                    if (text.isNotBlank()) {
                        listener.onInputTranscription(text)
                    }
                }

                val outputTrans = serverContent.optJSONObject("outputTranscription") ?: serverContent.optJSONObject("output_transcription")
                if (outputTrans != null) {
                    val text = outputTrans.optString("text", "")
                    if (text.isNotBlank()) {
                        listener.onOutputTranscription(text)
                    }
                }

                if (serverContent.optBoolean("turnComplete", serverContent.optBoolean("turn_complete", false))) {
                    listener.onTurnComplete()
                }
            }

            // 3. Check toolCall / tool_call
            val toolCall = root.optJSONObject("toolCall") ?: root.optJSONObject("tool_call")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: toolCall.optJSONArray("function_calls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val fc = functionCalls.optJSONObject(i) ?: continue
                        val id = fc.optString("id", "")
                        val name = fc.optString("name", "")
                        val args = fc.optJSONObject("args") ?: JSONObject()
                        MairaLogger.log(MairaLogger.Category.TOOL, MairaLogger.Level.INFO, "Tool call পাওয়া গেছে: $name (id=$id)")
                        listener.onToolCall(id, name, args)
                    }
                }
            }

            // 4. Check error / goAway
            if (root.has("goAway") || root.has("go_away")) {
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.WARN, "Server sent goAway notice")
                if (!isClosedIntentionally) {
                    listener.onClosed(1000, "Server goAway notice")
                }
            }

            val errorObj = root.optJSONObject("error")
            if (errorObj != null) {
                val message = errorObj.optString("message", "অজানা সার্ভার সমস্যা")
                MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "সার্ভার এরর: $message")
                listener.onError("Gemini Live এরর: $message")
            }

        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.ERROR, "বার্তা পার্স করতে ব্যর্থ: ${e.message}")
        }
    }

    fun sendAudioChunk(pcm16Bytes: ByteArray) {
        if (!isConnected || webSocket == null) return
        val base64Data = Base64.encodeToString(pcm16Bytes, Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("data", base64Data)
                    put("mimeType", "audio/pcm;rate=16000")
                })
            })
        }
        webSocket?.send(json.toString())
    }

    fun sendAudioStreamEnd() {
        if (!isConnected || webSocket == null) return
        val json = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audioStreamEnd", true)
            })
        }
        webSocket?.send(json.toString())
    }

    fun sendTypedText(text: String) {
        if (!isConnected || webSocket == null) return
        val json = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("text", text)
            })
        }
        webSocket?.send(json.toString())
        MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.INFO, "লাইভ সেশনে টেক্সট পাঠানো হয়েছে: $text")
    }

    fun sendToolResponse(callId: String, functionName: String, responseObj: JSONObject) {
        if (webSocket == null) return
        val json = JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", callId)
                        put("name", functionName)
                        put("response", responseObj)
                    })
                })
            })
        }
        webSocket?.send(json.toString())
        MairaLogger.log(MairaLogger.Category.TOOL, MairaLogger.Level.INFO, "Tool প্রতিক্রিয়া পাঠানো হয়েছে: $functionName (id=$callId)")
    }

    fun disconnect() {
        isClosedIntentionally = true
        setupTimeoutJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        _connectionState.value = "বিচ্ছিন্ন"
        MairaLogger.log(MairaLogger.Category.CONNECTION, MairaLogger.Level.INFO, "Gemini Live সংযোগ বন্ধ করা হয়েছে")
    }

    private fun getFunctionDeclarations(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("name", "openApp")
                put("description", "Opens an installed app by name or Bengali alias (e.g. ফেসবুক, হোয়াটসঅ্যাপ, ইউটিউব).")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("appName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Name or alias of the app to open.")
                        })
                    })
                    put("required", JSONArray().apply { put("appName") })
                })
            })
            put(JSONObject().apply {
                put("name", "openUrl")
                put("description", "Opens a website URL in the browser.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The website URL to open.")
                        })
                    })
                    put("required", JSONArray().apply { put("url") })
                })
            })
            put(JSONObject().apply {
                put("name", "setFlashlight")
                put("description", "Turns the phone flashlight on or off.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("on", JSONObject().apply {
                            put("type", "BOOLEAN")
                            put("description", "True for on, false for off.")
                        })
                    })
                    put("required", JSONArray().apply { put("on") })
                })
            })
            put(JSONObject().apply {
                put("name", "callContact")
                put("description", "Calls a contact by name or relationship (e.g., মা, বাবা, আম্মু, Rahul).")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Contact name or relationship alias.")
                        })
                        put("nameEnglish", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "the same person's name written in English letters")
                        })
                    })
                    put("required", JSONArray().apply { put("name") })
                })
            })
            put(JSONObject().apply {
                put("name", "playYoutube")
                put("description", "Plays a song or video on YouTube.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Song or video search query.")
                        })
                    })
                    put("required", JSONArray().apply { put("query") })
                })
            })
            put(JSONObject().apply {
                put("name", "sendMessengerMessage")
                put("description", "Sends a message to a recipient on Facebook Messenger.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("recipientName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Recipient display name")
                        })
                        put("text", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Message text")
                        })
                        put("nameEnglish", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "the same person's name written in English letters")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("recipientName")
                        put("text")
                    })
                })
            })
            put(JSONObject().apply {
                put("name", "sendWhatsAppMessage")
                put("description", "Prepares a WhatsApp message to a contact by opening the chat with prefilled text for user confirmation and sending.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("contactName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Contact name, relationship (e.g. মা, বাবা, রহিম), or phone number")
                        })
                        put("text", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The message text to send")
                        })
                        put("nameEnglish", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "the same person's name written in English letters")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("contactName")
                        put("text")
                    })
                })
            })
            put(JSONObject().apply {
                put("name", "postOnFacebook")
                put("description", "Posts a status update on Facebook.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("text", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Status post text")
                        })
                    })
                    put("required", JSONArray().apply { put("text") })
                })
            })
            put(JSONObject().apply {
                put("name", "createReminder")
                put("description", "Creates a time reminder alarm.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("time", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Time in HH:mm 24-hour format")
                        })
                        put("days", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Days of week (e.g., MON,TUE,WED)")
                        })
                        put("text", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "Spoken reminder message text")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("time")
                        put("text")
                    })
                })
            })
            put(JSONObject().apply {
                put("name", "runShortcut")
                put("description", "Executes a saved phrase shortcut by name.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("shortcutName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The phrase shortcut name")
                        })
                    })
                    put("required", JSONArray().apply { put("shortcutName") })
                })
            })
            put(JSONObject().apply {
                put("name", "goBack")
                put("description", "Navigates back to the previous screen or app using the system Back gesture.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject())
                })
            })
        }
    }
}
