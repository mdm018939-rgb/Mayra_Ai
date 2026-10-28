package com.example.api

object GroqConstants {
    val PRIORITY_MODELS = listOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "llama-3.3-70b-versatile",
        "meta-llama/llama-4-scout-17b-16e-instruct"
    )

    const val DEFAULT_FALLBACK_MODEL = "llama-3.3-70b-versatile"
}
