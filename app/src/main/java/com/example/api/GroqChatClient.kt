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
    private val geminiKey: String,
    private val systemInstruction: String
) {
    companion object {
        const val GROQ_MODEL = "llama-3.3-70b-versatile"
        const val GEMINI_TEXT_MODEL = "gemini-2.5-flash"
        private var currentKeyIndex = 0
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatResponse(
        val textResponse: String,
        val toolCalls: List<ToolCallData> = emptyList(),
        val isFallback: Boolean = false,
        val errorMessage: String? = null
    )

    data class ToolCallData(val id: String, val name: String, val argsJson: JSONObject)

    suspend fun sendMessage(
        historyMessages: List<Pair<String, String>>, // sender ("user"/"assistant"), text
        userMessage: String
    ): ChatResponse = withContext(Dispatchers.IO) {
        if (groqKeys.isNotEmpty()) {
            return@withContext sendGroqMessageWithRetry(historyMessages, userMessage)
        } else if (geminiKey.isNotBlank()) {
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

            try {
                val jsonBody = JSONObject().apply {
                    put("model", GROQ_MODEL)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemInstruction)
                        })
                        // Add last ~20 messages history
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
                    .url("https://api.groq.com/openai/v1/chat/completions")
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
                        val content = messageObj?.optString("content", "") ?: ""

                        MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.INFO, "Groq উত্তর সফলভাবে পাওয়া গেছে")
                        return ChatResponse(textResponse = content)
                    }
                } else if (response.code == 429 || response.code == 401) {
                    MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.WARN, "Groq Key রেট লিমিট বা অবৈধ (code=${response.code}), পরবর্তী key চেষ্টা করা হচ্ছে...")
                    continue
                } else {
                    MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, "Groq HTTP Error ${response.code}: $responseStr")
                }
            } catch (e: Exception) {
                MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, "Groq নেটওয়ার্ক ত্রুটি: ${e.message}")
            }
        }

        // All Groq keys failed, check Gemini key fallback
        if (geminiKey.isNotBlank()) {
            return sendGeminiTextMessage(historyMessages, userMessage)
        }
        return ChatResponse(textResponse = "", errorMessage = "সকল Groq Key কাজ করছে না বা রেট লিমিট শেষ হয়েছে।")
    }

    private fun sendGeminiTextMessage(
        historyMessages: List<Pair<String, String>>,
        userMessage: String
    ): ChatResponse {
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

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_TEXT_MODEL:generateContent?key=$geminiKey"
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

                    return ChatResponse(textResponse = text, isFallback = true)
                }
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.CHAT, MairaLogger.Level.ERROR, "Gemini Text API ত্রুটি: ${e.message}")
        }
        return ChatResponse(textResponse = "", errorMessage = "Gemini Text API থেকে উত্তর পাওয়া যায়নি।")
    }
}
