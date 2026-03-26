package com.cocode.babakcast.data.repository

import android.util.Log
import com.cocode.babakcast.data.model.TweetMedia
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * OkHttp-based downloader for X/Twitter direct media URLs (photos and MP4 videos).
 */
internal class XDirectDownloader(
    private val okHttpClient: OkHttpClient,
    private val tag: String
) {

    fun downloadPhoto(photo: TweetMedia.Photo, outputFile: File): Result<File> =
        downloadUrl(photo.url, outputFile)

    fun downloadVideo(directUrl: String, outputFile: File): Result<File> =
        downloadUrl(directUrl, outputFile)

    private fun downloadUrl(url: String, outputFile: File): Result<File> {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(IOException("Download failed: HTTP ${response.code}"))
            }

            val bytes = response.body?.bytes()
                ?: return Result.failure(IOException("Empty response body"))

            outputFile.writeBytes(bytes)
            Log.d(tag, "Downloaded: ${outputFile.name} (${bytes.size} bytes)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(tag, "Failed to download: $url", e)
            Result.failure(e)
        }
    }

    companion object {
        fun guessImageExtension(url: String): String {
            val path = url.substringBefore('?').substringAfterLast('/')
            return when {
                path.endsWith(".png", ignoreCase = true) -> "png"
                path.endsWith(".webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
        }
    }
}
