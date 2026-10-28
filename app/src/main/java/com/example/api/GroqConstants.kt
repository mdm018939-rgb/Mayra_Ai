package com.example.api

object GroqConstants {
    const val MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"
    const val CHAT_COMPLETIONS_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    val PRIORITY_MODELS = listOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "llama-3.3-70b-versatile"
    )

    const val DEFAULT_FALLBACK_MODEL = "llama-3.3-70b-versatile"
}
