package com.example.api

import com.example.data.MairaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqChatClient(
    private val groqKeys: List<String>,
    private val geminiKeys: List<String>,
    private val systemInstruction: String
) {
    // Secondary constructor for backwards compatibility
    constructor(groqKeys: List<String>, geminiKey: String, systemInstruction: String) :
        this(groqKeys, if (geminiKey.isNotBlank()) listOf(geminiKey) else emptyList(), systemInstruction)

    companion object {
        const val GEMINI_TEXT_MODEL = "gemini-2.5-flash"
        private var currentKeyIndex = 0
        private var currentGeminiKeyIndex = 0
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatResponse(
        val textResponse: String,
        val toolCalls: List<ToolCallData> = emptyList(),
        val isFallback: Boolean = false,
        val modelUsed: String? = null,
        val errorMessage: String? = null
    )

    data class ToolCallData(val id: String, val name: String, val argsJson: JSONObject)

    suspend fun sendMessage(
        historyMessages: List<Pair<String, String>>, // sender ("user"/"assistant"), text
        userMessage: String
    ): ChatResponse = withContext(Dispatchers.IO) {
        if (groqKeys.isNotEmpty()) {
            return@withContext sendGroqMessageWithRetry(historyMessages, userMessage)
        } else if (geminiKeys.isNotEmpty()) {
            MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.INFO, "Groq key নাই, Gemini Text API-তে ফলব্যাক করা হচ্ছে")
            return@withContext sendGeminiTextMessage(historyMessages, userMessage)
        } else {
            return@withContext ChatResponse(
                textResponse = "",
                errorMessage = "প্রথমে সেটিংসে আপনার Groq অথবা Gemini API key দিন।"
            )
        }
    }

    private fun sendGroqMessageWithRetry(
        historyMessages: List<Pair<String, String>>,
        userMessage: String
    ): ChatResponse {
        val totalKeys = groqKeys.size
        var attempts = 0

        while (attempts < totalKeys) {
            val key = groqKeys[currentKeyIndex % totalKeys]
            currentKeyIndex = (currentKeyIndex + 1) % totalKeys
            attempts++

            var selectedModel = GroqModelResolver.resolveModel(key)
            var isModelRetried = false

            while (true) {
                try {
                    val jsonBody = JSONObject().apply {
                        put("model", selectedModel)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", systemInstruction)
                            })
                            historyMessages.takeLast(20).forEach { (sender, msg) ->
                                put(JSONObject().apply {
                                    put("role", if (sender == "user") "user" else "assistant")
                                    put("content", msg)
                                })
                            }
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userMessage)
                            })
                        })
                        put("temperature", 0.7)
                    }

                    val request = Request.Builder()
                        .url(GroqConstants.CHAT_COMPLETIONS_ENDPOINT)
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    if (response.isSuccessful && responseStr.isNotBlank()) {
                        val root = JSONObject(responseStr)
                        val choices = root.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val messageObj = choice.optJSONObject("message")

                            // Parse content text safely for both llama and gpt-oss models
                            val content = messageObj?.optString("content", "") ?: ""
                            val reasoning = messageObj?.optString("reasoning", "") ?: ""
                            val reasoningContent = messageObj?.optString("reasoning_content", "") ?: ""

                            val finalText = when {
                                content.isNotBlank() && content != "null" -> content
                                reasoning.isNotBlank() && reasoning != "null" -> reasoning
                                reasoningContent.isNotBlank() && reasoningContent != "null" -> reasoningContent
                                else -> ""
                            }

                            MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.INFO, "Groq উত্তর সফলভাবে পাওয়া গেছে (মডেল: $selectedModel)")
                            return ChatResponse(textResponse = finalText, modelUsed = selectedModel)
                        }
                    } else if (response.code == 404 || responseStr.contains("model_not_found")) {
                        MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.WARN, "Groq 404 Model Not Found ($selectedModel)। মডেল নতুন করে রিজলভ করে পুনরায় চেষ্টা করা হচ্ছে...")
                        if (!isModelRetried) {
                            isModelRetried = true
                            GroqModelResolver.clearCache()
                            selectedModel = GroqModelResolver.resolveModel(key, forceRefresh = true)
                            continue // Retry request once with newly resolved model
                        } else {
                            val errorMsg = "Groq ত্রুটি (HTTP 404): '$selectedModel' মডেলটি Groq সার্ভারে আর সক্রিয় নেই।"
                            MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, errorMsg)
                            return ChatResponse(textResponse = "", modelUsed = selectedModel, errorMessage = errorMsg)
                        }
                    } else if (response.code == 429) {
                        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Groq Key রেট লিমিট শেষ (code=429), পরবর্তী key চেষ্টা করা হচ্ছে...")
                        break // Try next key in outer loop
                    } else if (response.code == 401 || response.code == 403) {
                        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Groq Key অবৈধ বা অকার্যকর (code=${response.code}), পরবর্তী key চেষ্টা করা হচ্ছে...")
                        break // Try next key in outer loop
                    } else {
                        val errorDetail = try {
                            JSONObject(responseStr).optJSONObject("error")?.optString("message", responseStr) ?: responseStr
                        } catch (_: Exception) {
                            responseStr
                        }
                        val errorMsg = "Groq এপিআই ত্রুটি (HTTP ${response.code}): $errorDetail"
                        MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, errorMsg)
                        return ChatResponse(textResponse = "", modelUsed = selectedModel, errorMessage = errorMsg)
                    }
                } catch (e: Exception) {
                    val rawMsg = e.localizedMessage ?: "নেটওয়ার্ক বিচ্ছিন্ন"
                    val isNetworkIssue = rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                            rawMsg.contains("UnknownHostException", ignoreCase = true) ||
                            rawMsg.contains("SocketTimeoutException", ignoreCase = true) ||
                            rawMsg.contains("ConnectException", ignoreCase = true) ||
                            rawMsg.contains("Failed to connect", ignoreCase = true) ||
                            e is java.net.UnknownHostException ||
                            e is java.net.SocketTimeoutException ||
                            e is java.net.ConnectException

                    val errorMsg = if (isNetworkIssue) {
                        "ইন্টারনেট সংযোগ সমস্যা: আপনার ইন্টারনেট সংযোগ চেক করুন"
                    } else {
                        "Groq সংযোগ ব্যর্থ: $rawMsg"
                    }
                    MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, errorMsg)
                    break
                }
            }
        }

        // All Groq keys failed, fallback to Gemini
        if (geminiKeys.isNotEmpty()) {
            return sendGeminiTextMessage(historyMessages, userMessage)
        }
        return ChatResponse(textResponse = "", errorMessage = "সকল Groq Key কাজ করছে না বা রেট লিমিট শেষ হয়েছে।")
    }

    private fun sendGeminiTextMessage(
        historyMessages: List<Pair<String, String>>,
        userMessage: String
    ): ChatResponse {
        val totalGeminiKeys = geminiKeys.size
        if (totalGeminiKeys == 0) {
            return ChatResponse(textResponse = "", errorMessage = "কোনো Gemini API key পাওয়া যায়নি।")
        }

        var attempts = 0
        while (attempts < totalGeminiKeys) {
            val key = geminiKeys[currentGeminiKeyIndex % totalGeminiKeys]
            currentGeminiKeyIndex = (currentGeminiKeyIndex + 1) % totalGeminiKeys
            attempts++

            try {
                val contents = JSONArray()
                historyMessages.takeLast(20).forEach { (sender, msg) ->
                    contents.put(JSONObject().apply {
                        put("role", if (sender == "user") "user" else "model")
                        put("parts", JSONArray().apply { put(JSONObject().apply { put("text", msg) }) })
                    })
                }
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", userMessage) }) })
                })

                val jsonBody = JSONObject().apply {
                    put("contents", contents)
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply { put(JSONObject().apply { put("text", systemInstruction) }) })
                    })
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_TEXT_MODEL:generateContent?key=$key"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful && responseStr.isNotBlank()) {
                    val root = JSONObject(responseStr)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val contentObj = candidate.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                        return ChatResponse(textResponse = text, isFallback = true, modelUsed = GEMINI_TEXT_MODEL)
                    }
                } else if (response.code in listOf(401, 403, 429) || responseStr.contains("RESOURCE_EXHAUSTED") || responseStr.contains("API_KEY_INVALID")) {
                    MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Gemini Key রেট লিমিট বা ত্রুটি (HTTP ${response.code}), পরবর্তী key চেষ্টা করা হচ্ছে...")
                    continue
                } else {
                    val errorMsg = "Gemini Text API ত্রুটি (HTTP ${response.code}): $responseStr"
                    MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, errorMsg)
                    return ChatResponse(textResponse = "", errorMessage = errorMsg)
                }
            } catch (e: Exception) {
                val rawMsg = e.localizedMessage ?: "নেটওয়ার্ক বিচ্ছিন্ন"
                val isNetworkIssue = rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                        rawMsg.contains("UnknownHostException", ignoreCase = true) ||
                        rawMsg.contains("SocketTimeoutException", ignoreCase = true) ||
                        rawMsg.contains("ConnectException", ignoreCase = true) ||
                        rawMsg.contains("Failed to connect", ignoreCase = true) ||
                        e is java.net.UnknownHostException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.ConnectException

                val errorMsg = if (isNetworkIssue) {
                    "ইন্টারনেট সংযোগ সমস্যা: আপনার ইন্টারনেট সংযোগ চেক করুন"
                } else {
                    "Gemini Text API নেটওয়ার্ক ত্রুটি: $rawMsg"
                }
                MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, errorMsg)
            }
        }
        return ChatResponse(textResponse = "", errorMessage = "সবগুলো Gemini Key ব্যর্থ হয়েছে বা কোটা শেষ হয়ে গেছে।")
    }
}
