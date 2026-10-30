package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("maira_secure_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "maira_master_key"

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _nicknameFlow = MutableStateFlow(getNickname())
    val nicknameFlow: StateFlow<String> = _nicknameFlow.asStateFlow()

    private val _groqKeysFlow = MutableStateFlow(getGroqKeys())
    val groqKeysFlow: StateFlow<List<String>> = _groqKeysFlow.asStateFlow()

    private val _geminiKeysFlow = MutableStateFlow(emptyList<String>())
    val geminiKeysFlow: StateFlow<List<String>> = _geminiKeysFlow.asStateFlow()

    init {
        ensureKey()
        migrateSingleGeminiKey()
        _geminiKeysFlow.value = getGeminiKeys()
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

    private fun migrateSingleGeminiKey() {
        try {
            val encList = prefs.getString("gemini_keys_enc", null)
            if (encList == null) {
                val oldEnc = prefs.getString("gemini_key_enc", "") ?: ""
                val oldKey = decrypt(oldEnc).trim()
                val list = mutableListOf<String>()
                if (oldKey.isNotBlank()) {
                    list.add(oldKey)
                }
                try {
                    val buildKey = com.example.BuildConfig.GEMINI_API_KEY
                    if (buildKey.isNotBlank() && buildKey != "your_api_key_here" && !list.contains(buildKey)) {
                        list.add(buildKey)
                    }
                } catch (_: Throwable) {}
                if (list.isNotEmpty()) {
                    saveGeminiKeysList(list)
                    MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "পূর্ববর্তী Gemini key সফলভাবে নতুন তালিকায় মাইগ্রেট করা হয়েছে (${list.size} টি key)")
                }
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.ERROR, "Gemini key মাইগ্রেশন ত্রুটি: ${e.message}")
        }
    }

    // Gemini API Keys List & Round-Robin
    private var geminiRoundRobinIndex = 0

    fun getGeminiKeys(): List<String> {
        val enc = prefs.getString("gemini_keys_enc", "") ?: ""
        val raw = decrypt(enc)
        val list = if (raw.isNotBlank()) {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        if (list.isNotEmpty()) return list

        // Fallback to old single key if present
        val oldEnc = prefs.getString("gemini_key_enc", "") ?: ""
        val oldSingle = decrypt(oldEnc).trim()
        if (oldSingle.isNotBlank()) return listOf(oldSingle)

        return try {
            val buildKey = com.example.BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "your_api_key_here") listOf(buildKey) else emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    fun getNextGeminiKey(): String {
        val keys = getGeminiKeys()
        if (keys.isEmpty()) return ""
        val key = keys[geminiRoundRobinIndex % keys.size]
        geminiRoundRobinIndex = (geminiRoundRobinIndex + 1) % keys.size
        return key
    }

    fun getGeminiKey(): String {
        val keys = getGeminiKeys()
        return keys.firstOrNull() ?: ""
    }

    fun setGeminiKey(key: String) {
        addGeminiKey(key)
    }

    fun addGeminiKey(newKey: String): Boolean {
        val cleanKey = newKey.trim()
        if (cleanKey.isBlank()) return false
        val currentList = getGeminiKeys().toMutableList()
        if (!currentList.contains(cleanKey)) {
            currentList.add(cleanKey)
            saveGeminiKeysList(currentList)
            val last4 = if (cleanKey.length >= 4) cleanKey.takeLast(4) else "XXXX"
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "নতুন Gemini key যোগ করা হয়েছে (...$last4)")
            return true
        }
        return false
    }

    fun removeGeminiKey(keyToRemove: String) {
        val currentList = getGeminiKeys().toMutableList()
        if (currentList.remove(keyToRemove)) {
            saveGeminiKeysList(currentList)
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "Gemini key মুছে ফেলা হয়েছে")
        }
    }

    fun clearGeminiKey() {
        clearGeminiKeys()
    }

    fun clearGeminiKeys() {
        prefs.edit().remove("gemini_keys_enc").remove("gemini_key_enc").apply()
        _geminiKeysFlow.value = emptyList()
        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "সকল Gemini key মুছে ফেলা হয়েছে")
    }

    private fun saveGeminiKeysList(list: List<String>) {
        val csv = list.joinToString(",")
        val enc = encrypt(csv)
        prefs.edit().putString("gemini_keys_enc", enc).apply()
        _geminiKeysFlow.value = list
    }

    // Groq API Keys List
    fun getGroqKeys(): List<String> {
        val enc = prefs.getString("groq_keys_enc", "") ?: ""
        val raw = decrypt(enc)
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addGroqKey(newKey: String): Boolean {
        val cleanKey = newKey.trim()
        if (cleanKey.isBlank()) return false
        val currentList = getGroqKeys().toMutableList()
        if (!currentList.contains(cleanKey)) {
            currentList.add(cleanKey)
            saveGroqKeysList(currentList)
            val last4 = if (cleanKey.length >= 4) cleanKey.takeLast(4) else "XXXX"
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "নতুন Groq key যোগ করা হয়েছে (...$last4)")
            return true
        }
        return false
    }

    fun removeGroqKey(keyToRemove: String) {
        val currentList = getGroqKeys().toMutableList()
        if (currentList.remove(keyToRemove)) {
            saveGroqKeysList(currentList)
            MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "Groq key মুছে ফেলা হয়েছে")
        }
    }

    fun setGroqKeys(keysCsv: String) {
        val list = keysCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        saveGroqKeysList(list)
    }

    private fun saveGroqKeysList(list: List<String>) {
        val csv = list.joinToString(",")
        val enc = encrypt(csv)
        prefs.edit().putString("groq_keys_enc", enc).apply()
        _groqKeysFlow.value = list
    }

    fun clearGroqKeys() {
        prefs.edit().remove("groq_keys_enc").apply()
        _groqKeysFlow.value = emptyList()
        MairaLogger.log(MairaLogger.Category.API_KEY, MairaLogger.Level.INFO, "সকল Groq key মুছে ফেলা হয়েছে")
    }

    // User Nickname
    fun getNickname(): String = prefs.getString("user_nickname", "") ?: ""
    fun setNickname(name: String) {
        val cleanName = name.trim()
        prefs.edit().putString("user_nickname", cleanName).apply()
        _nicknameFlow.value = cleanName
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ডাকনাম পরিবর্তন: $cleanName")
    }

    // Personality
    private val validPersonalityStyles = listOf(
        "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)",
        "রোমান্টিক সঙ্গী (সন্দেহবাজ প্রেমিকা)",
        "রোমান্টিক সঙ্গী (রাগী প্রেমিকা)"
    )

    private val _personalityFlow = MutableStateFlow(getPersonality())
    val personalityFlow: StateFlow<String> = _personalityFlow.asStateFlow()

    fun getPersonality(): String {
        val saved = prefs.getString("selected_personality", "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)") ?: "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)"
        if (saved in validPersonalityStyles) {
            return saved
        }
        val fallback = "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)"
        prefs.edit().putString("selected_personality", fallback).apply()
        _personalityFlow.value = fallback
        return fallback
    }

    fun setPersonality(personality: String) {
        val target = if (personality in validPersonalityStyles) personality else "রোমান্টিক সঙ্গী (ভালো প্রেমিকা)"
        prefs.edit().putString("selected_personality", target).apply()
        _personalityFlow.value = target
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "পারসোনালিটি পরিবর্তন: $target")
    }

    // Theme Choice
    fun getThemeMode(): String = prefs.getString("theme_mode", "light") ?: "light"
    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeModeFlow.value = mode
        ThemeLoadingManager.triggerThemeChange()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "থিম পরিবর্তন: $mode")
    }

    // Overlay Floating Avatar Settings
    private val _overlayEnabledFlow = MutableStateFlow(isOverlayEnabled())
    val overlayEnabledFlow: StateFlow<Boolean> = _overlayEnabledFlow.asStateFlow()

    fun isOverlayEnabled(): Boolean = prefs.getBoolean("overlay_enabled", false)
    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_enabled", enabled).apply()
        _overlayEnabledFlow.value = enabled
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান মেয়ে সুইচ: $enabled")
    }

    // Messaging Automation
    fun isAskBeforeSending(): Boolean = prefs.getBoolean("ask_before_sending", true)
    fun setAskBeforeSending(ask: Boolean) {
        prefs.edit().putBoolean("ask_before_sending", ask).apply()
        MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "পাঠানোর আগে জিজ্ঞেস করো: $ask")
    }
}
