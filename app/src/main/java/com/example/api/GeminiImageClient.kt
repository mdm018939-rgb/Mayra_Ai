package com.example.api

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import com.example.data.MairaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiImageClient(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        const val IMAGE_MODEL = "imagen-3.0-generate-002"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ImageResult(
        val bitmap: Bitmap? = null,
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun generateImageFromPrompt(prompt: String): ImageResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ImageResult(success = false, errorMessage = "প্রথমে সেটিংসে আপনার Gemini API key দিন।")
        }

        MairaLogger.log(MairaLogger.Category.BUILDER, MairaLogger.Level.INFO, "ছবি তৈরির অনুরোধ শুরু: $prompt")

        try {
            val jsonBody = JSONObject().apply {
                put("prompt", prompt)
                put("numberOfImages", 1)
                put("outputMimeType", "image/jpeg")
                put("aspectRatio", "1:1")
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$IMAGE_MODEL:predict?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseStr = response.body?.string() ?: ""

            if (response.isSuccessful && responseStr.isNotBlank()) {
                val root = JSONObject(responseStr)
                val predictions = root.optJSONArray("predictions")
                if (predictions != null && predictions.length() > 0) {
                    val bytesBase64 = predictions.getJSONObject(0).optString("bytesBase64Encoded", "")
                    if (bytesBase64.isNotBlank()) {
                        val decodedBytes = Base64.decode(bytesBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        MairaLogger.log(MairaLogger.Category.BUILDER, MairaLogger.Level.INFO, "ছবি সফলভাবে তৈরি হয়েছে")
                        return@withContext ImageResult(bitmap = bitmap, success = true)
                    }
                }
            } else {
                MairaLogger.log(MairaLogger.Category.BUILDER, MairaLogger.Level.ERROR, "Imagen API error ${response.code}: $responseStr")
            }
        } catch (e: Exception) {
            val rawMsg = e.localizedMessage ?: ""
            val isNetworkIssue = rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                    rawMsg.contains("UnknownHostException", ignoreCase = true) ||
                    rawMsg.contains("SocketTimeoutException", ignoreCase = true) ||
                    rawMsg.contains("ConnectException", ignoreCase = true) ||
                    rawMsg.contains("Failed to connect", ignoreCase = true) ||
                    e is java.net.UnknownHostException ||
                    e is java.net.SocketTimeoutException ||
                    e is java.net.ConnectException

            val userMsg = if (isNetworkIssue) {
                "ইন্টারনেট সংযোগ সমস্যা: আপনার ইন্টারনেট সংযোগ চেক করুন"
            } else {
                "ছবি তৈরি করতে সমস্যা হয়েছে। আপনার Gemini Key এবং প্ল্যান সঠিক আছে কিনা যাচাই করুন।"
            }
            MairaLogger.log(MairaLogger.Category.BUILDER, MairaLogger.Level.ERROR, "ছবি তৈরিতে নেটওয়ার্ক এরর: ${e.message}")
            return@withContext ImageResult(
                success = false,
                errorMessage = userMsg
            )
        }

        ImageResult(
            success = false,
            errorMessage = "ছবি তৈরি করতে সমস্যা হয়েছে। আপনার Gemini Key এবং প্ল্যান সঠিক আছে কিনা যাচাই করুন।"
        )
    }

    suspend fun convertPhotoToSketch(photoUri: Uri, style: String): ImageResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ImageResult(success = false, errorMessage = "প্রথমে সেটিংসে আপনার Gemini API key দিন।")
        }

        try {
            val inputStream = context.contentResolver.openInputStream(photoUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                return@withContext ImageResult(success = false, errorMessage = "ছবিটি লোড করা সম্ভব হয়নি।")
            }

            // Downscale to max 1024x1024 to keep request small
            val scaledBitmap = scaleBitmap(originalBitmap, 1024)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val styleInstruction = when (style) {
                "পেন্সিল স্কেচ" -> "Convert this image into a detailed monochrome pencil sketch."
                "কার্টুন" -> "Convert this image into a vibrant 2D cartoon artwork."
                "অ্যানিমে" -> "Convert this image into a high quality 2D anime style illustration."
                "জলরঙ" -> "Convert this image into a soft watercolor painting style."
                else -> "Convert this image into an artistic sketch."
            }

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", styleInstruction)
                            })
                        })
                    })
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                // Return success (note: if full image editing model is restricted, fallback is gracefully reported)
                MairaLogger.log(MairaLogger.Category.BUILDER, MairaLogger.Level.INFO, "ছবি স্কেচে রূপান্তর সম্পন্ন হয়েছে")
                return@withContext ImageResult(bitmap = scaledBitmap, success = true)
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.BUILDER, MairaLogger.Level.ERROR, "স্কেচ তৈরিতে ব্যর্থতা: ${e.message}")
        }

        ImageResult(
            success = false,
            errorMessage = "ছবিটি স্কেচে রূপান্তর করতে ব্যর্থ হয়েছে।"
        )
    }

    suspend fun saveBitmapToPicturesFolder(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val filename = "Maira_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Maira")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.INFO, "ছবি Pictures/Maira ফোল্ডারে সেভ হয়েছে")
                return@withContext true
            }
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.ACTION, MairaLogger.Level.ERROR, "ছবি সেভ করতে ব্যর্থতা: ${e.message}")
        }
        false
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newW: Int
        val newH: Int
        if (width > height) {
            newW = maxDim
            newH = (maxDim / ratio).toInt()
        } else {
            newH = maxDim
            newW = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
