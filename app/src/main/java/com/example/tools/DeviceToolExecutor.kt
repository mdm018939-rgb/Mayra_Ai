package com.example.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.MairaLogger
import com.example.service.MairaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DeviceToolExecutor(private val context: Context) {

    fun postActionNotification(title: String, text: String, intent: Intent) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "maira_action_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Maira Actions",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.maira_app_icon_1793185004076)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "নোটিফিকেশন পোস্ট করতে ব্যর্থ: ${e.message}")
        }
    }

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

    enum class ToolStatus { SUCCESS, FAILED, UNAVAILABLE, UNKNOWN }

    data class ToolResult(val status: ToolStatus, val message: String, val details: String? = null) {
        val success: Boolean get() = status == ToolStatus.SUCCESS
    }

    suspend fun openApp(appName: String): ToolResult = withContext(Dispatchers.IO) {
        if (!android.provider.Settings.canDrawOverlays(context)) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "Display over other apps (canDrawOverlays) অনুমতি দেওয়া নেই")
            return@withContext ToolResult(
                status = ToolStatus.UNAVAILABLE,
                message = "অন্যান্য অ্যাপের উপরে দেখানোর (Display over other apps) অনুমতি দেওয়া নেই। সেটিংসে গিয়ে অনুমতি দিন।"
            )
        }

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
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "'$appName' অ্যাপটি আপনার ফোনে খুঁজে পাওয়া যায়নি।")
        }

        val selected: android.content.pm.ResolveInfo = if (matchedApps.size == 1) {
            matchedApps.first()
        } else {
            // Prefer exact label or package match (e.g. com.whatsapp over WhatsApp Business)
            val exactMatch = matchedApps.firstOrNull { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString().lowercase().trim()
                val pkg = resolveInfo.activityInfo.packageName.lowercase().trim()
                aliasMatches.any { alias ->
                    pkg == alias || pkg == "com.$alias" || label == alias || label == targetQuery
                }
            } ?: matchedApps.firstOrNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName.lowercase()
                aliasMatches.any { alias -> pkg.endsWith(".$alias") || pkg == alias }
            }

            if (exactMatch != null) {
                exactMatch
            } else {
                val names = matchedApps.take(3).joinToString(", ") { it.loadLabel(pm).toString() }
                return@withContext ToolResult(ToolStatus.UNKNOWN, "একাধিক অ্যাপ পাওয়া গেছে: $names। কোনটি খুলব বলুন?")
            }
        }

        val targetPkg = selected.activityInfo.packageName
        val appLabel = selected.loadLabel(pm).toString()
        val launchIntent = pm.getLaunchIntentForPackage(targetPkg)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                context.startActivity(launchIntent)
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "অ্যাপ খোলার চেষ্টা করা হয়েছে: $appLabel ($targetPkg)")

                val service = com.example.service.MairaAccessibilityService.instance
                if (service != null && com.example.service.MairaAccessibilityService.isServiceConnected) {
                    var isOpened = false
                    for (i in 0 until 5) {
                        kotlinx.coroutines.delay(300)
                        val fgPkg = service.getForegroundPackage()
                        if (fgPkg.isNotBlank() && (fgPkg.equals(targetPkg, ignoreCase = true) || fgPkg.lowercase().contains(targetPkg.lowercase()))) {
                            isOpened = true
                            break
                        }
                    }
                    return@withContext if (isOpened) {
                        ToolResult(ToolStatus.SUCCESS, "$appLabel অ্যাপটি খোলা হয়েছে।")
                    } else {
                        ToolResult(ToolStatus.UNKNOWN, "$appLabel অ্যাপটি খোলার চেষ্টা করা হয়েছে, কিন্তু তা নিশ্চিত করা যায়নি।")
                    }
                } else {
                    return@withContext ToolResult(ToolStatus.UNKNOWN, "$appLabel অ্যাপটি খোলার চেষ্টা করা হয়েছে, কিন্তু তা নিশ্চিত করা যায়নি।")
                }
            } catch (e: Exception) {
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "অ্যাপ খোলার ব্যর্থতা: ${e.message}")
                return@withContext ToolResult(ToolStatus.FAILED, "অ্যাপটি সরাসরি খোলা সম্ভব হয়নি: ${e.localizedMessage}")
            }
        }
        ToolResult(ToolStatus.FAILED, "অ্যাপটি চালু করা যাচ্ছে না।")
    }

    suspend fun openUrl(urlStr: String): ToolResult = withContext(Dispatchers.IO) {
        if (!Settings.canDrawOverlays(context)) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "Display over other apps (canDrawOverlays) অনুমতি দেওয়া নেই")
            return@withContext ToolResult(
                status = ToolStatus.UNAVAILABLE,
                message = "অন্যান্য অ্যাপের উপরে দেখানোর (Display over other apps) অনুমতি দেওয়া নেই। সেটিংসে গিয়ে অনুমতি দিন।"
            )
        }

        var cleanUrl = urlStr.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "URL খোলা হয়েছে: $cleanUrl")

            val service = MairaAccessibilityService.instance
            var isOpened = false
            if (service != null && MairaAccessibilityService.isServiceConnected) {
                for (i in 0 until 5) {
                    kotlinx.coroutines.delay(300)
                    val fgPkg = service.getForegroundPackage()
                    if (fgPkg.isNotBlank() && fgPkg.lowercase() != context.packageName.lowercase()) {
                        isOpened = true
                        break
                    }
                }
            }

            if (isOpened) {
                ToolResult(ToolStatus.SUCCESS, "ওয়েবসাইট খোলা হয়েছে।")
            } else {
                postActionNotification(
                    "ওয়েবসাইট খুলতে স্পর্শ করুন",
                    "ওয়েবসাইটটি সরাসরি খোলা সম্ভব হয়নি। খুলতে নোটিফিকেশনে ট্যাপ করুন।",
                    intent
                )
                ToolResult(ToolStatus.UNKNOWN, "ওয়েবসাইটটি সরাসরি খোলা সম্ভব হয়নি। একটি নোটিফিকেশন পাঠানো হয়েছে, সেটিতে ট্যাপ করুন।")
            }
        } catch (e: Exception) {
            ToolResult(ToolStatus.FAILED, "ওয়েবসাইট খুলতে সমস্যা হয়েছে: ${e.localizedMessage}")
        }
    }

    suspend fun setFlashlight(on: Boolean): ToolResult = withContext(Dispatchers.IO) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "আপনার ডিভাইসে ফ্ল্যাশলাইট সমর্থন করে না।")
        }
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                val statusStr = if (on) "চালু" else "বন্ধ"
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "ফ্ল্যাশলাইট $statusStr করা হয়েছে")
                ToolResult(ToolStatus.SUCCESS, "ফ্ল্যাশলাইট $statusStr করা হয়েছে।")
            } else {
                ToolResult(ToolStatus.UNAVAILABLE, "ফ্ল্যাশলাইট ক্যামেরা আইডি পাওয়া যায়নি।")
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "ফ্ল্যাশলাইট কন্ট্রোল ব্যর্থতা: ${e.message}")
            ToolResult(ToolStatus.FAILED, "ফ্ল্যাশলাইট পরিবর্তন করা সম্ভব হয়নি।")
        }
    }

    companion object {
        fun convertBengaliDigitsToAscii(str: String): String {
            val bengaliDigits = "০১২৩৪৫৬৭৮৯"
            val sb = java.lang.StringBuilder()
            for (ch in str) {
                val idx = bengaliDigits.indexOf(ch)
                if (idx != -1) {
                    sb.append(idx)
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun normalizePhoneNumber(raw: String): String? {
            if (raw.isBlank()) return null
            val asciiRaw = convertBengaliDigitsToAscii(raw)
            val hasPlus = asciiRaw.trim().startsWith("+")
            val digitsOnly = asciiRaw.replace(Regex("[^0-9]"), "")
            if (digitsOnly.length < 6) return null

            val cleanedDigits = if (digitsOnly.startsWith("00")) digitsOnly.substring(2) else digitsOnly

            return when {
                // Bangladeshi mobile standard: 01XXXXXXXXX (11 digits starting with 01)
                cleanedDigits.startsWith("01") && cleanedDigits.length == 11 -> {
                    "88$cleanedDigits"
                }
                // Bangladeshi mobile with country code without plus: 8801XXXXXXXXX (13 digits)
                cleanedDigits.startsWith("8801") && cleanedDigits.length == 13 -> {
                    cleanedDigits
                }
                // International with leading plus or length 7-15
                hasPlus && cleanedDigits.length in 7..15 -> {
                    cleanedDigits
                }
                cleanedDigits.length in 8..15 -> {
                    cleanedDigits
                }
                else -> null
            }
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isWhatsAppInstalled(): Boolean {
        return isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
    }

    data class ContactMatch(val name: String, val number: String)

    fun normalizeForSearch(str: String?): String {
        if (str.isNullOrBlank()) return ""
        val ascii = convertBengaliDigitsToAscii(str).lowercase().trim()
        return ascii.replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    suspend fun searchContacts(query: String, nameEnglish: String? = null): List<ContactMatch> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            MairaLogger.log(MairaLogger.Category.PERMISSION, MairaLogger.Level.WARN, "যোগাযোগের অনুমতি নাই (READ_CONTACTS)")
            return@withContext emptyList()
        }

        val rawClean = query.lowercase().trim()
        if (rawClean.isBlank() && nameEnglish.isNullOrBlank()) return@withContext emptyList()

        val candidateQueries = mutableListOf<String>()
        if (rawClean.isNotBlank()) {
            candidateQueries.add(rawClean)
            if (rawClean.endsWith("কে") && rawClean.length > 2) {
                candidateQueries.add(rawClean.removeSuffix("কে").trim())
            }
            if (rawClean.endsWith("রে") && rawClean.length > 2) {
                candidateQueries.add(rawClean.removeSuffix("রে").trim())
            }
            if (rawClean.endsWith("এর") && rawClean.length > 2) {
                candidateQueries.add(rawClean.removeSuffix("এর").trim())
            }
        }

        if (!nameEnglish.isNullOrBlank()) {
            val engClean = nameEnglish.lowercase().trim()
            if (engClean.isNotBlank()) {
                candidateQueries.add(engClean)
            }
        }

        val allRawAliases = candidateQueries.flatMap { q ->
            relationshipAliases[q] ?: listOf(q)
        }.distinct()

        val normalizedAliases = allRawAliases
            .map { normalizeForSearch(it) }
            .filter { it.length >= 2 }
            .distinct()

        if (normalizedAliases.isEmpty()) return@withContext emptyList()

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

                val normContactName = normalizeForSearch(displayName)
                if (normContactName.length < 2) continue

                val isMatched = normalizedAliases.any { normAlias ->
                    normContactName.contains(normAlias) || normAlias.contains(normContactName)
                }

                if (isMatched && phoneNum.isNotBlank()) {
                    results.add(ContactMatch(displayName, phoneNum))
                }
            }
        }

        // Deduplicate contacts by (name, normalized number)
        val deduplicated = mutableListOf<ContactMatch>()
        val seen = mutableSetOf<String>()
        for (m in results) {
            val norm = normalizePhoneNumber(m.number) ?: m.number.replace(Regex("[^0-9]"), "")
            val key = "${m.name.trim().lowercase()}:$norm"
            if (seen.add(key)) {
                deduplicated.add(m)
            }
        }

        MairaLogger.log(
            MairaLogger.Category.ACTION,
            MairaLogger.Level.INFO,
            "কন্টাক্ট সার্চ করা হয়েছে: query='$rawClean', nameEnglish='$nameEnglish', পাওয়া গেছে: ${deduplicated.size} টি"
        )
        deduplicated
    }

    suspend fun sendWhatsAppMessage(
        recipientQuery: String,
        messageText: String,
        askBeforeSending: Boolean = true,
        nameEnglish: String? = null
    ): ToolResult = withContext(Dispatchers.IO) {
        if (!Settings.canDrawOverlays(context)) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "Display over other apps (canDrawOverlays) অনুমতি দেওয়া নেই")
            return@withContext ToolResult(
                status = ToolStatus.UNAVAILABLE,
                message = "অন্যান্য অ্যাপের উপরে দেখানোর (Display over other apps) অনুমতি দেওয়া নেই। সেটিংসে গিয়ে অনুমতি দিন।"
            )
        }

        val cleanRecipient = recipientQuery.trim()
        val cleanMessage = messageText.trim()

        if (cleanRecipient.isBlank()) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "হোয়াটসঅ্যাপ মেসেজ: প্রাপকের নাম অনুপস্থিত")
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "কার কাছে WhatsApp মেসেজ পাঠাতে চান বলুন।")
        }

        if (cleanMessage.isBlank()) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "হোয়াটসঅ্যাপ মেসেজ: মেসেজ ফাঁকা")
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "কী মেসেজ পাঠাতে চান তা বলুন।")
        }

        val isStandardInstalled = isPackageInstalled("com.whatsapp")
        val isBusinessInstalled = isPackageInstalled("com.whatsapp.w4b")
        if (!isStandardInstalled && !isBusinessInstalled) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "হোয়াটসঅ্যাপ অ্যাপ ইনস্টল করা নেই")
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "আপনার ফোনে WhatsApp ইনস্টল করা নেই। প্লে স্টোর থেকে ইনস্টল করুন।")
        }
        val targetPackage = if (isStandardInstalled) "com.whatsapp" else "com.whatsapp.w4b"

        val asciiRecipient = convertBengaliDigitsToAscii(cleanRecipient)
        val recipientDigits = asciiRecipient.replace(Regex("[^0-9]"), "")
        val isDirectPhoneNumber = recipientDigits.length >= 7 && (
            asciiRecipient.startsWith("+") ||
            asciiRecipient.startsWith("01") ||
            asciiRecipient.startsWith("8801") ||
            asciiRecipient.matches(Regex("^[+0-9\\s()-]+$"))
        )

        var resolvedName: String
        val normalizedPhone: String

        if (isDirectPhoneNumber) {
            val norm = normalizePhoneNumber(asciiRecipient)
            if (norm == null) {
                return@withContext ToolResult(ToolStatus.UNAVAILABLE, "ফোন নম্বরটি সঠিক ফরম্যাটে নেই।")
            }
            resolvedName = cleanRecipient
            normalizedPhone = norm
        } else {
            if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                MairaLogger.log(MairaLogger.Category.PERMISSION, MairaLogger.Level.WARN, "READ_CONTACTS পারমিশন নাই")
                return@withContext ToolResult(ToolStatus.UNAVAILABLE, "কন্টাক্ট নম্বর খুঁজে পেতে কন্টাক্ট পারমিশন (READ_CONTACTS) প্রয়োজন। সেটিংসে গিয়ে অনুমতি দিন।")
            }

            val matches = searchContacts(cleanRecipient, nameEnglish)
            if (matches.isEmpty()) {
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "কন্টাক্ট পাওয়া যায়নি: $cleanRecipient")
                return@withContext ToolResult(ToolStatus.FAILED, "'$cleanRecipient' নামে কোনো কন্টাক্ট নম্বর খুঁজে পাওয়া যায়নি।")
            }

            val selectedMatch: ContactMatch
            if (matches.size == 1) {
                selectedMatch = matches.first()
            } else {
                val stripped = cleanRecipient.removeSuffix("কে").removeSuffix("রে").removeSuffix("এর").trim()
                val exactMatches = matches.filter {
                    it.name.trim().equals(cleanRecipient, ignoreCase = true) ||
                    it.name.trim().equals(stripped, ignoreCase = true)
                }
                if (exactMatches.size == 1) {
                    selectedMatch = exactMatches.first()
                } else {
                    val candidateNames = matches.map { it.name }.distinct().take(4).joinToString(", ")
                    MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "একাধিক কন্টাক্ট পাওয়া গেছে: $candidateNames")
                    return@withContext ToolResult(ToolStatus.UNKNOWN, "'$cleanRecipient' নামে একাধিক কন্টাক্ট পাওয়া গেছে: $candidateNames। কার কাছে পাঠাতে চান স্পষ্ট করে বলুন।")
                }
            }

            resolvedName = selectedMatch.name
            val norm = normalizePhoneNumber(selectedMatch.number)
            if (norm == null) {
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "ফোন নম্বর সঠিক নয়: ${selectedMatch.number}")
                return@withContext ToolResult(ToolStatus.UNAVAILABLE, "${selectedMatch.name}-এর ফোন নম্বরটি সঠিক ফরম্যাটে নেই।")
            }
            normalizedPhone = norm
        }

        val encodedText = URLEncoder.encode(cleanMessage, "UTF-8")
        val clickToChatUrl = "https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedText"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clickToChatUrl)).apply {
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            var activeIntent = intent
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallbackUri = Uri.parse("whatsapp://send?phone=$normalizedPhone&text=$encodedText")
                    activeIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                        setPackage(targetPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(activeIntent)
                } catch (_: Exception) {
                    activeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(clickToChatUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(activeIntent)
                }
            }

            MairaLogger.log(
                MairaLogger.Category.ACTION,
                MairaLogger.Level.INFO,
                "হোয়াটসঅ্যাপ চ্যাটে মেসেজ প্রস্তুত করার চেষ্টা করা হয়েছে: $resolvedName ($normalizedPhone)"
            )

            var isOpened = false
            val service = MairaAccessibilityService.instance
            if (service != null && MairaAccessibilityService.isServiceConnected) {
                for (i in 0 until 5) {
                    kotlinx.coroutines.delay(300)
                    val fgPkg = service.getForegroundPackage()
                    if (fgPkg.isNotBlank() && (fgPkg.equals(targetPackage, ignoreCase = true) || fgPkg.lowercase().contains("whatsapp"))) {
                        isOpened = true
                        break
                    }
                }
            }

            val responseMessage = if (askBeforeSending) {
                "হোয়াটসঅ্যাপে $resolvedName-এর জন্য মেসেজটি প্রস্তুত করা হয়েছে। পাঠানোর জন্য সেন্ড বাটনে ট্যাপ করুন।"
            } else {
                "হোয়াটসঅ্যাপে $resolvedName-এর জন্য মেসেজটি প্রস্তুত করা হয়েছে, পাঠানোর জন্য সেন্ড বাটনে ট্যাপ করুন।"
            }

            if (isOpened) {
                ToolResult(
                    status = ToolStatus.SUCCESS,
                    message = responseMessage,
                    details = "PREFILLED_OPENED"
                )
            } else {
                postActionNotification(
                    "WhatsApp চ্যাট খুলতে স্পর্শ করুন",
                    "$resolvedName-এর WhatsApp চ্যাট খুলতে নোটিফিকেশনে ট্যাপ করুন।",
                    activeIntent
                )
                ToolResult(
                    status = ToolStatus.UNKNOWN,
                    message = "হোয়াটসঅ্যাপ চ্যাটটি সরাসরি খোলা সম্ভব হয়নি। একটি নোটিফিকেশন পাঠানো হয়েছে, সেটিতে ট্যাপ করুন।",
                    details = "NOTIFICATION_POSTED"
                )
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "হোয়াটসঅ্যাপ চ্যাট খুলতে সমস্যা: ${e.message}")
            ToolResult(
                status = ToolStatus.FAILED,
                message = "মেসেজ পাঠানো যায়নি: হোয়াটসঅ্যাপ চ্যাট খোলা সম্ভব হয়নি।"
            )
        }
    }

    suspend fun playYoutube(query: String): ToolResult = withContext(Dispatchers.IO) {
        if (!Settings.canDrawOverlays(context)) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "Display over other apps (canDrawOverlays) অনুমতি দেওয়া নেই")
            return@withContext ToolResult(
                status = ToolStatus.UNAVAILABLE,
                message = "অন্যান্য অ্যাপের উপরে দেখানোর (Display over other apps) অনুমতি দেওয়া নেই। সেটিংসে গিয়ে অনুমতি দিন।"
            )
        }

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

            val activeIntent: Intent
            if (match != null) {
                val videoId = match.groupValues[1]
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                activeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(activeIntent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }
            } else {
                activeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(activeIntent)
            }

            val service = MairaAccessibilityService.instance
            var isOpened = false
            if (service != null && MairaAccessibilityService.isServiceConnected) {
                for (i in 0 until 5) {
                    kotlinx.coroutines.delay(300)
                    val fgPkg = service.getForegroundPackage()
                    if (fgPkg.isNotBlank() && (fgPkg.lowercase().contains("youtube") || fgPkg.lowercase() != context.packageName.lowercase())) {
                        isOpened = true
                        break
                    }
                }
            }

            if (isOpened) {
                ToolResult(ToolStatus.SUCCESS, "ইউটিউবে '$query' চালানো হচ্ছে।")
            } else {
                postActionNotification(
                    "YouTube চালাতে স্পর্শ করুন",
                    "ইউটিউবে '$query' চালাতে নোটিফিকেশনে ট্যাপ করুন।",
                    activeIntent
                )
                ToolResult(ToolStatus.UNKNOWN, "ইউটিউব সরাসরি খোলা সম্ভব হয়নি। একটি নোটিফিকেশন পাঠানো হয়েছে, সেটিতে ট্যাপ করুন।")
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "ইউটিউব গান খোঁজা ব্যর্থ: ${e.message}")
            ToolResult(ToolStatus.FAILED, "ইউটিউবে গান খুঁজতে সমস্যা হয়েছে।")
        }
    }

    suspend fun sendMessengerMessage(
        recipientName: String,
        text: String,
        nameEnglish: String? = null
    ): ToolResult = withContext(Dispatchers.IO) {
        if (!Settings.canDrawOverlays(context)) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.WARN, "Display over other apps (canDrawOverlays) অনুমতি দেওয়া নেই")
            return@withContext ToolResult(
                status = ToolStatus.UNAVAILABLE,
                message = "অন্যান্য অ্যাপের উপরে দেখানোর (Display over other apps) অনুমতি দেওয়া নেই। সেটিংসে গিয়ে অনুমতি দিন।"
            )
        }

        val cleanRecipient = recipientName.trim()
        val cleanText = text.trim()

        if (cleanRecipient.isBlank()) {
            return@withContext ToolResult(ToolStatus.FAILED, "কার কাছে Messenger মেসেজ পাঠাতে চান বলুন।")
        }
        if (cleanText.isBlank()) {
            return@withContext ToolResult(ToolStatus.FAILED, "কী মেসেজ পাঠাতে চান তা বলুন।")
        }

        if (!isPackageInstalled("com.facebook.orca")) {
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "আপনার ফোনে Messenger অ্যাপ ইনস্টল করা নেই।")
        }

        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext ToolResult(ToolStatus.UNAVAILABLE, "কন্টাক্ট নম্বর খুঁজে পেতে কন্টাক্ট পারমিশন (READ_CONTACTS) প্রয়োজন। সেটিংসে গিয়ে অনুমতি দিন।")
        }

        val matches = searchContacts(cleanRecipient, nameEnglish)
        if (matches.isEmpty()) {
            return@withContext ToolResult(ToolStatus.FAILED, "'$cleanRecipient' নামে কোনো কন্টাক্ট খুঁজে পাওয়া যায়নি।")
        }

        if (matches.size > 1) {
            val candidateNames = matches.map { it.name }.distinct().take(4).joinToString(", ")
            return@withContext ToolResult(ToolStatus.UNKNOWN, "'$cleanRecipient' নামে একাধিক কন্টাক্ট পাওয়া গেছে: $candidateNames। কার কাছে মেসেজ পাঠাতে চান স্পষ্ট করে বলুন।")
        }

        val selectedMatch = matches.first()
        val targetPkg = "com.facebook.orca"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent)
                val service = MairaAccessibilityService.instance
                var isOpened = false
                if (service != null && MairaAccessibilityService.isServiceConnected) {
                    for (i in 0 until 5) {
                        kotlinx.coroutines.delay(300)
                        val fgPkg = service.getForegroundPackage()
                        if (fgPkg.isNotBlank() && fgPkg.lowercase().contains("orca")) {
                            isOpened = true
                            break
                        }
                    }
                }

                if (isOpened) {
                    ToolResult(
                        status = ToolStatus.SUCCESS,
                        message = "মেসেঞ্জারে ${selectedMatch.name}-এর জন্য চ্যাট খোলা হয়েছে।"
                    )
                } else {
                    postActionNotification(
                        "Messenger চ্যাট খুলতে স্পর্শ করুন",
                        "${selectedMatch.name}-এর সাথে চ্যাট করতে মেসেঞ্জার খুলুন।",
                        launchIntent
                    )
                    ToolResult(
                        status = ToolStatus.UNKNOWN,
                        message = "মেসেঞ্জার চ্যাট সরাসরি খোলা সম্ভব হয়নি। একটি নোটিফিকেশন পাঠানো হয়েছে, সেটিতে ট্যাপ করুন।"
                    )
                }
            } catch (e: Exception) {
                ToolResult(ToolStatus.FAILED, "মেসেঞ্জার চ্যাট খোলা সম্ভব হয়নি: ${e.localizedMessage}")
            }
        } else {
            ToolResult(ToolStatus.FAILED, "মেসেঞ্জার অ্যাপ চালু করা যাচ্ছে না।")
        }
    }
}
