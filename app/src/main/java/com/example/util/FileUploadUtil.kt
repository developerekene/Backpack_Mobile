package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class UploadedFileResult(
    val url: String,
    val fileType: String, // "image" | "video" | "document"
    val fileName: String
)

object FileUploadUtil {

    private const val CLOUDINARY_CLOUD_NAME = "dt2gk3gcn"
    private const val CLOUDINARY_UPLOAD_PRESET = "synod_preset"
    private const val MAX_FIRESTORE_DOC_BYTES = 450 * 1024 // 450 KB raw (~600 KB base64)
    private const val MAX_IMAGE_DIM_PX = 1280

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment ?: "attachment"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "attachment"
        }
    }

    suspend fun uploadUri(context: Context, uri: Uri): Result<UploadedFileResult> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = getFileName(context, uri)

            if (mimeType.startsWith("video/")) {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IllegalStateException("Unable to read selected video."))
                if (bytes.size > 40 * 1024 * 1024) {
                    return@withContext Result.failure(IllegalArgumentException("Video must be under 40 MB."))
                }
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        fileName,
                        bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    )
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .build()
                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/video/upload")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Video upload failed (${response.code}).")
                        )
                    }
                    val secureUrl = JSONObject(bodyStr).optString("secure_url")
                    if (secureUrl.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Upload returned empty URL."))
                    }
                    return@withContext Result.success(UploadedFileResult(secureUrl, "video", fileName))
                }
            }

            if (mimeType.startsWith("image/")) {
                val encoded = compressAndEncodeImage(context, uri)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to process selected image."))
                return@withContext Result.success(UploadedFileResult(encoded, "image", fileName))
            }

            // Documents (PDF, DOC, DOCX, TXT, etc.) encoded to Data URL matching Web's uploadFile()
            val docBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Unable to read selected document."))
            if (docBytes.size > MAX_FIRESTORE_DOC_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("File exceeds 450 KB limit for direct upload. Please paste a cloud link URL instead.")
                )
            }
            val b64 = Base64.encodeToString(docBytes, Base64.NO_WRAP)
            val dataUrl = "data:$mimeType;base64,$b64"
            Result.success(UploadedFileResult(dataUrl, "document", fileName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compressAndEncodeImage(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var inSampleSize = 1
        while (origW / inSampleSize > MAX_IMAGE_DIM_PX * 2 || origH / inSampleSize > MAX_IMAGE_DIM_PX * 2) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return null

        val maxDim = maxOf(sampled.width, sampled.height)
        val scaled = if (maxDim > MAX_IMAGE_DIM_PX) {
            val scale = MAX_IMAGE_DIM_PX.toFloat() / maxDim.toFloat()
            Bitmap.createScaledBitmap(
                sampled,
                (sampled.width * scale).toInt().coerceAtLeast(1),
                (sampled.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            sampled
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        if (scaled !== sampled) scaled.recycle()
        sampled.recycle()

        val bytes = baos.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }
}
