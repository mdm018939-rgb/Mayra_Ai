package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import com.example.data.MairaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DeviceToolExecutor(private val context: Context) {

    private val bangaliAppAliases = mapOf(
        "ফেসবুক" to listOf("facebook", "fb"),
        "মেসেঞ্জার" to listOf("messenger"),
        "হোয়াটসঅ্যাপ" to listOf("whatsapp"),
        "ইউটিউব" to listOf("youtube"),
        "ইনস্টাগ্রাম" to listOf("instagram"),
        "টিকটক" to listOf("tiktok"),
        "টেলিগ্রাম" to listOf("telegram"),
        "ক্যামেরা" to listOf("camera"),
        "গ্যালারি" to listOf("gallery", "photos"),
        "সেটিংস" to listOf("settings"),
        "ক্রোম" to listOf("chrome"),
        "গুগল" to listOf("google"),
        "ম্যাপস" to listOf("maps"),
        "প্লে স্টোর" to listOf("play store", "vending"),
        "ক্যালকুলেটর" to listOf("calculator"),
        "ঘড়ি" to listOf("clock")
    )

    private val relationshipAliases = mapOf(
        "মা" to listOf("mom", "mother", "ammu", "amma", "maa", "মা", "আম্মু"),
        "আম্মু" to listOf("mom", "mother", "ammu", "amma", "maa", "মা", "আম্মু"),
        "আম্মা" to listOf("mom", "mother", "ammu", "amma", "maa", "মা", "আম্মু"),
        "মামনি" to listOf("mom", "mother", "ammu", "amma", "maa", "মা"),
        "বাবা" to listOf("dad", "father", "abbu", "baba", "বাবা", "আব্বু"),
        "আব্বু" to listOf("dad", "father", "abbu", "baba", "বাবা", "আব্বু"),
        "আব্বা" to listOf("dad", "father", "abbu", "baba", "বাবা", "আব্বু"),
        "ভাইয়া" to listOf("bhaiya", "bhai", "brother", "ভাইয়া", "ভাই"),
        "আপু" to listOf("apu", "sister", "আপু", "আপি")
    )

    data class ToolResult(val success: Boolean, val message: String, val details: String? = null)

    suspend fun openApp(appName: String): ToolResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(intent, 0)

        val targetQuery = appName.lowercase().trim()
        val aliasMatches = bangaliAppAliases[targetQuery] ?: listOf(targetQuery)

        val matchedApps = apps.filter { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString().lowercase()
            val pkg = resolveInfo.activityInfo.packageName.lowercase()
            aliasMatches.any { alias -> label.contains(alias) || pkg.contains(alias) }
        }

        if (matchedApps.isEmpty()) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "অ্যাপ খুঁজে পাওয়া যায়নি: $appName")
            return@withContext ToolResult(false, "'$appName' অ্যাপটি আপনার ফোনে খুঁজে পাওয়া যায়নি।")
        }

        if (matchedApps.size > 1) {
            val names = matchedApps.take(3).joinToString(", ") { it.loadLabel(pm).toString() }
            return@withContext ToolResult(false, "একাধিক অ্যাপ পাওয়া গেছে: $names। কোনটি খুলব বলুন?")
        }

        val selected = matchedApps.first()
        val launchIntent = pm.getLaunchIntentForPackage(selected.activityInfo.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                val appLabel = selected.loadLabel(pm).toString()
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "অ্যাপ খোলা হয়েছে: $appLabel")
                return@withContext ToolResult(true, "$appLabel অ্যাপটি খোলা হয়েছে।")
            } catch (e: Exception) {
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "অ্যাপ খোলার ব্যর্থতা: ${e.message}")
                return@withContext ToolResult(false, "অ্যাপটি সরাসরি খোলা সম্ভব হয়নি।")
            }
        }
        ToolResult(false, "অ্যাপটি চালু করা যাচ্ছে না।")
    }

    suspend fun openUrl(urlStr: String): ToolResult = withContext(Dispatchers.IO) {
        var cleanUrl = urlStr.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "URL খোলা হয়েছে: $cleanUrl")
            ToolResult(true, "ওয়েবসাইট খোলা হয়েছে।")
        } catch (e: Exception) {
            ToolResult(false, "ওয়েবসাইট খুলতে সমস্যা হয়েছে: ${e.localizedMessage}")
        }
    }

    suspend fun setFlashlight(on: Boolean): ToolResult = withContext(Dispatchers.IO) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            return@withContext ToolResult(false, "আপনার ডিভাইসে ফ্ল্যাশলাইট সমর্থন করে না।")
        }
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                val statusStr = if (on) "চালু" else "বন্ধ"
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "ফ্ল্যাশলাইট $statusStr করা হয়েছে")
                ToolResult(true, "ফ্ল্যাশলাইট $statusStr করা হয়েছে।")
            } else {
                ToolResult(false, "ফ্ল্যাশলাইট ক্যামেরা আইডি পাওয়া যায়নি।")
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "ফ্ল্যাশলাইট কন্ট্রোল ব্যর্থতা: ${e.message}")
            ToolResult(false, "ফ্ল্যাশলাইট পরিবর্তন করা সম্ভব হয়নি।")
        }
    }

    data class ContactMatch(val name: String, val number: String)

    suspend fun searchContacts(query: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            MairaLogger.log(MairaLogger.Category.PERMISSION, MairaLogger.Level.WARN, "যোগাযোগের অনুমতি নাই (READ_CONTACTS)")
            return@withContext emptyList()
        }

        val cleanQuery = query.lowercase().trim()
        val aliasList = relationshipAliases[cleanQuery] ?: listOf(cleanQuery)

        val results = mutableListOf<ContactMatch>()
        val cr = context.contentResolver
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val displayName = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                val phoneNum = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                val lowerName = displayName.lowercase()

                val isMatched = aliasList.any { alias ->
                    lowerName.contains(alias) || alias.contains(lowerName)
                }

                if (isMatched && phoneNum.isNotBlank()) {
                    results.add(ContactMatch(displayName, phoneNum))
                }
            }
        }

        MairaLogger.log(
            MairaLogger.Category.ACTION,
            MairaLogger.Level.INFO,
            "কন্টাক্ট সার্চ করা হয়েছে: '$cleanQuery', পাওয়া গেছে: ${results.size} টি"
        )
        results
    }

    suspend fun playYoutube(query: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val connection = URL(searchUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val regex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
            val match = regex.find(html)

            if (match != null) {
                val videoId = match.groupValues[1]
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "ইউটিউব গান পাওয়া গেছে ও চালানো হয়েছে")
                return@withContext ToolResult(true, "ইউটিউবে '$query' চালানো হচ্ছে।")
            } else {
                // Fallback to search results page
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "ইউটিউব নির্দিষ্ট ভিডিও আইডি মেলেনি, সার্চ পেজ খোলা হয়েছে")
                return@withContext ToolResult(true, "ইউটিউব সার্চ ফলাফল খোলা হয়েছে, পছন্দমতো গানটি বেছে নাও।")
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "ইউটিউব গান খোঁজা ব্যর্থ: ${e.message}")
            ToolResult(false, "ইউটিউবে গান খুঁজতে সমস্যা হয়েছে।")
        }
    }
}
