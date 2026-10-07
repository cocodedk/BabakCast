package com.cocode.babakcast.data.repository

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Replaces the yt-dlp at [binary] with the latest release from GitHub. It does not use the
 * library's own updater: that one reads GitHub with no timeout at all, deletes the old file
 * before it has the new one, and could finish late. Here every request has a deadline
 * (about a minute for the whole update), the new file is checked before it counts, and it
 * replaces the old one in a single rename. The caller holds the gate, so no yt-dlp is
 * running meanwhile; a failure leaves the old file exactly as it was.
 */
internal class YtDlpInstaller(
    private val client: OkHttpClient,
    private val releaseUrl: String,
    private val binary: File,
    private val releaseTimeoutMs: Long = 20_000,
    private val downloadTimeoutMs: Long = 40_000
) {
    fun update(): YtDlpUpdateResult {
        val release = Json.parseToJsonElement(fetchText()).jsonObject
        val tag = release.string("tag_name") ?: throw IOException("The release has no version")
        val installed = YtDlpVersion.of(binary)
        if (installed != null && YtDlpVersion.compare(installed, tag) >= 0) {
            return YtDlpUpdateResult.AlreadyLatest
        }
        val asset = release["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it.string("name") == ASSET_NAME }
            ?: throw IOException("The release has no $ASSET_NAME file")
        val url = asset.string("browser_download_url") ?: throw IOException("The file has no address")

        val download = File(binary.parentFile, "$ASSET_NAME.download")
        try {
            download(url, download)
            val size = asset["size"]?.jsonPrimitive?.longOrNull
            if (size != null && download.length() != size) throw IOException("The download is incomplete")
            if (YtDlpVersion.of(download) != tag) throw IOException("The download is not yt-dlp $tag")
            if (!download.renameTo(binary)) throw IOException("Could not put the new yt-dlp in place")
        } finally {
            download.delete()
        }
        return YtDlpUpdateResult.Updated
    }

    private fun fetchText(): String =
        call(releaseUrl, releaseTimeoutMs) { it.body.string() }

    private fun download(url: String, target: File) {
        call(url, downloadTimeoutMs) { response ->
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    if (input.copyLimited(output, MAX_BYTES) > MAX_BYTES) throw IOException("The download is too large")
                }
            }
        }
    }

    /** One request with a deadline for the whole call, the body included. */
    private fun <T> call(url: String, timeoutMs: Long, read: (Response) -> T): T {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        val bounded = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        bounded.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
            return read(response)
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /** Copies at most [limit] bytes. Returns the number read, which is above [limit] when it was cut off. */
    private fun InputStream.copyLimited(out: OutputStream, limit: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) return total
            total += n
            if (total > limit) return total
            out.write(buffer, 0, n)
        }
    }

    private companion object {
        const val ASSET_NAME = "yt-dlp"
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
