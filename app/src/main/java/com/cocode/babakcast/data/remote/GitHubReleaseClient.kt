package com.cocode.babakcast.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class LatestRelease(
    val tagName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long
)

@Singleton
class GitHubReleaseClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    suspend fun fetchLatest(owner: String, repo: String): Result<LatestRelease> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub release lookup failed: HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                val release = parseLatestResponse(body)
                    ?: return@withContext Result.failure(Exception("GitHub response did not contain a usable APK asset"))
                Result.success(release)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseLatestResponse(body: String): LatestRelease? = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val asset = root["assets"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true }
                ?: return@runCatching null
            val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
            LatestRelease(tagName = tag, apkDownloadUrl = url, apkSizeBytes = size)
        }.getOrNull()
    }
}
