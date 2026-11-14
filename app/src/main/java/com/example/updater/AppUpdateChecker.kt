package com.example.updater

import com.example.BuildConfig
import com.example.data.MairaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isForceUpdate: Boolean
)

object AppUpdateChecker {
    private const val RELEASES_LATEST_URL =
        "https://api.github.com/repos/mdm018939-rgb/Mayra_Ai/releases/latest"
    private const val FORCE_TAG = "[force]"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun normalizeVersion(tagName: String): String {
        return tagName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
    }

    fun compareVersions(remoteVersion: String, currentVersion: String): Int {
        val remoteParts = parseNumericParts(remoteVersion)
        val currentParts = parseNumericParts(currentVersion)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return 0

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }

    private fun parseNumericParts(version: String): List<Int> {
        val clean = normalizeVersion(version)
            .substringBefore('-')
            .substringBefore('+')
        if (clean.isBlank()) return emptyList()
        return clean.split(".")
            .mapNotNull { segment ->
                val digits = segment.trim().takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
    }

    fun parseReleaseJson(
        jsonString: String,
        currentVersionName: String
    ): AppUpdateInfo? {
        return try {
            val root = JSONObject(jsonString)
            val tagName = root.optString("tag_name", "")
            val remoteVersion = normalizeVersion(tagName)
            if (remoteVersion.isBlank()) return null

            if (compareVersions(remoteVersion, currentVersionName) <= 0) {
                return null
            }

            var apkDownloadUrl = ""
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apkDownloadUrl = url
                        break
                    }
                }
            }

            val fallbackHtmlUrl = root.optString("html_url", "")
            val finalUrl = apkDownloadUrl.ifBlank { fallbackHtmlUrl }
            if (finalUrl.isBlank()) return null

            val rawBody = root.optString("body", "")
            val isForce = rawBody.contains(FORCE_TAG, ignoreCase = true)
            val cleanBody = rawBody
                .replace(Regex("\\[force]", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank {
                    "নতুন ভার্সন $remoteVersion পাওয়া গেছে। আরও ভালো অভিজ্ঞতার জন্য অ্যাপটি আপডেট করুন।"
                }

            AppUpdateInfo(
                latestVersion = remoteVersion,
                releaseNotes = cleanBody,
                downloadUrl = finalUrl,
                isForceUpdate = isForce
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_LATEST_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "Maira-Android-App")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                val info = parseReleaseJson(body, currentVersionName)
                if (info != null) {
                    MairaLogger.log(
                        MairaLogger.Category.UI,
                        MairaLogger.Level.INFO,
                        "নতুন অ্যাপ আপডেট পাওয়া গেছে: v${info.latestVersion} (force=${info.isForceUpdate})"
                    )
                }
                info
            }
        } catch (_: Exception) {
            // Silently ignore offline / network / parse errors so app runs normally
            null
        }
    }
}
