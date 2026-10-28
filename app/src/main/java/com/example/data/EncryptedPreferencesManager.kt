package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("maira_secure_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "maira_master_key"

    init {
        ensureKey()
    }

    private fun ensureKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.ERROR, "KeyStore initialize error: ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            null
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getSecretKey() ?: return plainText
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    private fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        val secretKey = getSecretKey() ?: return encryptedBase64
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= 12) return encryptedBase64
            val iv = ByteArray(12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedBase64
        }
    }

    // Gemini API Key
    fun getGeminiKey(): String {
        val enc = prefs.getString("gemini_key_enc", "") ?: ""
        return decrypt(enc)
    }

    fun setGeminiKey(key: String) {
        val enc = encrypt(key.trim())
        prefs.edit().putString("gemini_key_enc", enc).apply()
        val last4 = if (key.length >= 4) key.takeLast(4) else "XXXX"
        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "Gemini key সংরক্ষণ করা হয়েছে (...$last4)")
    }

    fun clearGeminiKey() {
        prefs.edit().remove("gemini_key_enc").apply()
        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "Gemini key মুছে ফেলা হয়েছে")
    }

    // Groq API Keys (comma-separated list)
    fun getGroqKeys(): List<String> {
        val enc = prefs.getString("groq_keys_enc", "") ?: ""
        val raw = decrypt(enc)
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setGroqKeys(keysCsv: String) {
        val enc = encrypt(keysCsv.trim())
        prefs.edit().putString("groq_keys_enc", enc).apply()
        val count = getGroqKeys().size
        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "Groq key(s) সংরক্ষণ করা হয়েছে ($count টি)")
    }

    fun clearGroqKeys() {
        prefs.edit().remove("groq_keys_enc").apply()
        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "Groq key(s) মুছে ফেলা হয়েছে")
    }

    // User Nickname
    fun getNickname(): String = prefs.getString("user_nickname", "") ?: ""
    fun setNickname(name: String) {
        prefs.edit().putString("user_nickname", name.trim()).apply()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ডাকনাম পরিবর্তন: $name")
    }

    // Personality ("রোমান্টিক সঙ্গী (বউ মোড)" is default)
    fun getPersonality(): String = prefs.getString("selected_personality", "রোমান্টিক সঙ্গী (বউ মোড)") ?: "রোমান্টিক সঙ্গী (বউ মোড)"
    fun setPersonality(personality: String) {
        prefs.edit().putString("selected_personality", personality).apply()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "পারসোনালিটি পরিবর্তন: $personality")
    }

    // Theme Choice ("light", "dark", "system")
    fun getThemeMode(): String = prefs.getString("theme_mode", "light") ?: "light"
    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "থিম পরিবর্তন: $mode")
    }

    // Overlay Floating Avatar Settings
    fun isOverlayEnabled(): Boolean = prefs.getBoolean("overlay_enabled", false)
    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_enabled", enabled).apply()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান মেয়ে সুইচ: $enabled")
    }

    fun getOverlaySize(): String = prefs.getString("overlay_size", "medium") ?: "medium"
    fun setOverlaySize(size: String) {
        prefs.edit().putString("overlay_size", size).apply()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান মেয়ের সাইজ: $size")
    }

    // Messaging Automation: "Ask before sending" switch (default ON)
    fun isAskBeforeSending(): Boolean = prefs.getBoolean("ask_before_sending", true)
    fun setAskBeforeSending(ask: Boolean) {
        prefs.edit().putBoolean("ask_before_sending", ask).apply()
        MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "পাঠানোর আগে জিজ্ঞেস করো: $ask")
    }
}
