package com.example.api

import com.example.data.MairaLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GroqModelResolver {
    private var cachedModel: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getCachedModel(): String? = cachedModel

    fun clearCache() {
        cachedModel = null
    }

    fun resolveModel(apiKey: String, forceRefresh: Boolean = false): String {
        if (!forceRefresh && cachedModel != null) {
            return cachedModel!!
        }

        if (apiKey.isBlank()) {
            return GroqConstants.DEFAULT_FALLBACK_MODEL
        }

        try {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseStr = response.body?.string() ?: ""

            if (response.isSuccessful && responseStr.isNotBlank()) {
                val root = JSONObject(responseStr)
                val data = root.optJSONArray("data")
                val availableModels = mutableListOf<String>()

                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id", "")
                        if (id.isNotBlank()) {
                            availableModels.add(id)
                        }
                    }
                }

                // Match against priority list in order
                for (priorityModel in GroqConstants.PRIORITY_MODELS) {
                    if (availableModels.contains(priorityModel)) {
                        cachedModel = priorityModel
                        MairaLogger.log(
                            MairaLogger.Category.API_KEY,
                            MairaLogger.Level.INFO,
                            "Groq মডেল সিলেক্ট করা হয়েছে: $priorityModel"
                        )
                        return priorityModel
                    }
                }

                // Fallback to first available model from Groq response if priority list doesn't match
                val firstAvailable = availableModels.firstOrNull()
                if (firstAvailable != null) {
                    cachedModel = firstAvailable
                    MairaLogger.log(
                        MairaLogger.Category.API_KEY,
                        MairaLogger.Level.INFO,
                        "Groq মডেল সিলেক্ট করা হয়েছে (ডিফল্ট ফলব্যাক): $firstAvailable"
                    )
                    return firstAvailable
                }
            } else {
                MairaLogger.log(
                    MairaLogger.Category.API_KEY,
                    MairaLogger.Level.WARN,
                    "Groq /models API ব্যর্থ (HTTP ${response.code}): $responseStr"
                )
            }
        } catch (e: Exception) {
            MairaLogger.log(
                MairaLogger.Category.API_KEY,
                MairaLogger.Level.ERROR,
                "Groq model resolve করতে গিয়ে ত্রুটি: ${e.message}"
            )
        }

        val fallback = GroqConstants.DEFAULT_FALLBACK_MODEL
        cachedModel = fallback
        return fallback
    }
}
