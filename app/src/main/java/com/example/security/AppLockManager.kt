package com.example.security

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MairaLogger
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

object AppLockUtils {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(inputPassword: String, expectedHash: String): Boolean {
        val normalizedExpected = expectedHash.trim().lowercase()
        if (inputPassword.isEmpty() || normalizedExpected.length != 64) {
            return false
        }
        val inputHash = sha256(inputPassword)
        return MessageDigest.isEqual(
            inputHash.toByteArray(Charsets.UTF_8),
            normalizedExpected.toByteArray(Charsets.UTF_8)
        )
    }

    fun toBengaliDigits(number: Int): String {
        val bengali = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return number.toString().map { ch ->
            if (ch in '0'..'9') bengali[ch - '0'] else ch
        }.joinToString("")
    }
}

data class AppLockUiState(
    val isUnlocked: Boolean = false,
    val isFetchingConfig: Boolean = true,
    val hasValidConfigHash: Boolean = false,
    val isOffline: Boolean = false,
    val isUsingCachedConfig: Boolean = false,
    val errorMessage: String? = null,
    val failedAttempts: Int = 0,
    val lockoutSecondsRemaining: Int = 0
)

interface PasswordRemoteConfigSource {
    suspend fun fetchAndActivate(): Result<String>
    fun getCachedPasswordHash(): String
}

class FirebasePasswordRemoteConfigSource(
    private val context: Context
) : PasswordRemoteConfigSource {

    private fun getRemoteConfig(): FirebaseRemoteConfig {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        return FirebaseRemoteConfig.getInstance().apply {
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL_SECONDS)
                .build()
            setConfigSettingsAsync(settings)
        }
    }

    override suspend fun fetchAndActivate(): Result<String> {
        return try {
            val rc = getRemoteConfig()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL_SECONDS)
                .build()
            rc.setConfigSettingsAsync(settings)

            val taskSucceeded = suspendCancellableCoroutine<Boolean> { continuation ->
                rc.fetchAndActivate().addOnCompleteListener { task ->
                    if (continuation.isActive) {
                        continuation.resume(task.isSuccessful)
                    }
                }
            }

            val hash = rc.getString(REMOTE_CONFIG_KEY).trim().lowercase()
            if (taskSucceeded) {
                Result.success(hash)
            } else {
                Result.failure(IllegalStateException("Remote Config fetch failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCachedPasswordHash(): String {
        return try {
            getRemoteConfig().getString(REMOTE_CONFIG_KEY).trim().lowercase()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        const val REMOTE_CONFIG_KEY = "password_hash"
        const val MINIMUM_FETCH_INTERVAL_SECONDS = 60L
    }
}

class AppLockViewModel @JvmOverloads constructor(
    application: Application,
    private val remoteConfigSource: PasswordRemoteConfigSource = FirebasePasswordRemoteConfigSource(application),
    private val networkChecker: () -> Boolean = { isNetworkConnected(application) }
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var currentExpectedHash: String = ""
    private var unlockedWithHash: String? = null
    private var countdownJob: Job? = null
    private var fetchJob: Job? = null

    init {
        restoreLockoutState()
        val cached = remoteConfigSource.getCachedPasswordHash().trim().lowercase()
        if (cached.isNotBlank()) {
            currentExpectedHash = cached
            _uiState.update {
                it.copy(
                    hasValidConfigHash = true,
                    isUsingCachedConfig = true
                )
            }
        }
        refreshRemoteConfig()
    }

    fun onAppForegrounded() {
        refreshRemoteConfig()
    }

    fun refreshRemoteConfig() {
        if (fetchJob?.isActive == true) return
        fetchJob = viewModelScope.launch {
            val online = networkChecker()
            if (!online) {
                val cachedHash = remoteConfigSource.getCachedPasswordHash().trim().lowercase()
                currentExpectedHash = cachedHash
                handleFetchedHash(
                    hash = cachedHash,
                    isOfflineNow = true,
                    isCached = cachedHash.isNotBlank(),
                    fetchFailedWithoutCache = cachedHash.isBlank()
                )
                MairaLogger.log(
                    MairaLogger.Category.UI,
                    MairaLogger.Level.WARN,
                    "অ্যাপ লক: ইন্টারনেট সংযোগ নেই, ক্যাশ ব্যবহার করা হচ্ছে (hasCache=${cachedHash.isNotBlank()})"
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    isFetchingConfig = true,
                    isOffline = false
                )
            }

            val result = remoteConfigSource.fetchAndActivate()
            if (result.isSuccess) {
                val fetchedHash = result.getOrDefault("").trim().lowercase()
                currentExpectedHash = fetchedHash
                handleFetchedHash(
                    hash = fetchedHash,
                    isOfflineNow = false,
                    isCached = false,
                    fetchFailedWithoutCache = false
                )
                MairaLogger.log(
                    MairaLogger.Category.UI,
                    MairaLogger.Level.INFO,
                    "অ্যাপ লক: Remote Config সফলভাবে আপডেট হয়েছে"
                )
            } else {
                val cachedHash = remoteConfigSource.getCachedPasswordHash().trim().lowercase()
                currentExpectedHash = cachedHash
                val stillOnline = networkChecker()
                handleFetchedHash(
                    hash = cachedHash,
                    isOfflineNow = !stillOnline,
                    isCached = cachedHash.isNotBlank(),
                    fetchFailedWithoutCache = cachedHash.isBlank()
                )
                MairaLogger.log(
                    MairaLogger.Category.UI,
                    MairaLogger.Level.WARN,
                    "অ্যাপ লক: Remote Config আনতে ব্যর্থ, ক্যাশ ব্যবহার হচ্ছে (hasCache=${cachedHash.isNotBlank()})"
                )
            }
        }
    }

    private fun handleFetchedHash(
        hash: String,
        isOfflineNow: Boolean,
        isCached: Boolean,
        fetchFailedWithoutCache: Boolean
    ) {
        val currentState = _uiState.value
        val hashChangedWhileUnlocked = currentState.isUnlocked &&
            hash.isNotBlank() &&
            unlockedWithHash != null &&
            hash != unlockedWithHash

        if (hashChangedWhileUnlocked) {
            unlockedWithHash = null
            _uiState.update {
                it.copy(
                    isUnlocked = false,
                    isFetchingConfig = false,
                    hasValidConfigHash = true,
                    isOffline = isOfflineNow,
                    isUsingCachedConfig = isCached,
                    errorMessage = "পাসওয়ার্ড পরিবর্তন করা হয়েছে, অনুগ্রহ করে নতুন পাসওয়ার্ড দিন"
                )
            }
            return
        }

        val nextError = when {
            currentState.lockoutSecondsRemaining > 0 ->
                "${AppLockUtils.toBengaliDigits(currentState.lockoutSecondsRemaining)} সেকেন্ড অপেক্ষা করুন"
            fetchFailedWithoutCache && isOfflineNow ->
                "ইন্টারনেট সংযোগ নেই"
            fetchFailedWithoutCache ->
                "সার্ভার থেকে পাসওয়ার্ড তথ্য আনা যায়নি। পুনরায় চেষ্টা করুন।"
            hash.isBlank() ->
                "সার্ভারে পাসওয়ার্ড সেট করা নেই। পুনরায় চেষ্টা করুন।"
            else -> currentState.errorMessage?.takeIf {
                it.startsWith("ভুল পাসওয়ার্ড") || it.startsWith("পাসওয়ার্ড পরিবর্তন")
            }
        }

        _uiState.update {
            it.copy(
                isFetchingConfig = false,
                hasValidConfigHash = hash.isNotBlank(),
                isOffline = isOfflineNow,
                isUsingCachedConfig = isCached,
                errorMessage = nextError
            )
        }
    }

    fun submitPassword(inputPassword: String) {
        val state = _uiState.value
        if (state.lockoutSecondsRemaining > 0) {
            return
        }
        if (state.isFetchingConfig && currentExpectedHash.isBlank()) {
            return
        }
        if (inputPassword.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "অনুগ্রহ করে পাসওয়ার্ড লিখুন") }
            return
        }

        if (currentExpectedHash.isBlank()) {
            val cached = remoteConfigSource.getCachedPasswordHash().trim().lowercase()
            if (cached.isNotBlank()) {
                currentExpectedHash = cached
            }
        }

        if (currentExpectedHash.isBlank()) {
            val offlineNow = !networkChecker()
            _uiState.update {
                it.copy(
                    isOffline = offlineNow,
                    hasValidConfigHash = false,
                    errorMessage = if (offlineNow) "ইন্টারনেট সংযোগ নেই" else "সার্ভার থেকে পাসওয়ার্ড তথ্য আনা যায়নি। পুনরায় চেষ্টা করুন।"
                )
            }
            refreshRemoteConfig()
            return
        }

        if (AppLockUtils.verifyPassword(inputPassword, currentExpectedHash)) {
            unlockedWithHash = currentExpectedHash
            clearLockoutState()
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    errorMessage = null,
                    failedAttempts = 0,
                    lockoutSecondsRemaining = 0
                )
            }
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "অ্যাপ সফলভাবে আনলক হয়েছে")
        } else {
            val updatedAttempts = state.failedAttempts + 1
            if (updatedAttempts >= MAX_ATTEMPTS) {
                val lockoutEndMillis = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                prefs.edit()
                    .putLong(KEY_LOCKOUT_END_MILLIS, lockoutEndMillis)
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .apply()
                startLockoutCountdown(lockoutEndMillis)
                MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.WARN, "৩ বার ভুল পাসওয়ার্ড: ৩০ সেকেন্ড লকআউট শুরু")
            } else {
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, updatedAttempts).apply()
                val remainingTries = MAX_ATTEMPTS - updatedAttempts
                _uiState.update {
                    it.copy(
                        failedAttempts = updatedAttempts,
                        errorMessage = "ভুল পাসওয়ার্ড (আরও ${AppLockUtils.toBengaliDigits(remainingTries)} বার চেষ্টা বাকি)"
                    )
                }
            }
        }
    }

    fun clearTransientError() {
        _uiState.update { state ->
            if (state.lockoutSecondsRemaining > 0) state
            else state.copy(errorMessage = null)
        }
    }

    private fun restoreLockoutState() {
        val lockoutEnd = prefs.getLong(KEY_LOCKOUT_END_MILLIS, 0L)
        val now = System.currentTimeMillis()
        if (lockoutEnd > now) {
            startLockoutCountdown(lockoutEnd)
        } else {
            if (lockoutEnd > 0L) {
                prefs.edit()
                    .remove(KEY_LOCKOUT_END_MILLIS)
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .apply()
            }
            val savedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0).coerceIn(0, MAX_ATTEMPTS - 1)
            if (savedAttempts > 0) {
                _uiState.update { it.copy(failedAttempts = savedAttempts) }
            }
        }
    }

    private fun startLockoutCountdown(lockoutEndMillis: Long) {
        countdownJob?.cancel()
        val initialRemainingMs = lockoutEndMillis - System.currentTimeMillis()
        if (initialRemainingMs > 0L) {
            val initialSeconds = ((initialRemainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    failedAttempts = MAX_ATTEMPTS,
                    lockoutSecondsRemaining = initialSeconds,
                    errorMessage = "${AppLockUtils.toBengaliDigits(initialSeconds)} সেকেন্ড অপেক্ষা করুন"
                )
            }
        }
        countdownJob = viewModelScope.launch {
            while (true) {
                val remainingMs = lockoutEndMillis - System.currentTimeMillis()
                if (remainingMs <= 0L) {
                    clearLockoutState()
                    _uiState.update {
                        it.copy(
                            failedAttempts = 0,
                            lockoutSecondsRemaining = 0,
                            errorMessage = null
                        )
                    }
                    break
                }
                val seconds = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                _uiState.update {
                    it.copy(
                        failedAttempts = MAX_ATTEMPTS,
                        lockoutSecondsRemaining = seconds,
                        errorMessage = "${AppLockUtils.toBengaliDigits(seconds)} সেকেন্ড অপেক্ষা করুন"
                    )
                }
                delay(250L)
            }
        }
    }

    private fun clearLockoutState() {
        countdownJob?.cancel()
        countdownJob = null
        prefs.edit()
            .remove(KEY_LOCKOUT_END_MILLIS)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        fetchJob?.cancel()
    }

    companion object {
        private const val PREFS_NAME = "maira_lock_prefs"
        private const val KEY_LOCKOUT_END_MILLIS = "lockout_end_millis"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MS = 30_000L

        fun isNetworkConnected(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } catch (_: Exception) {
                false
            }
        }
    }
}
