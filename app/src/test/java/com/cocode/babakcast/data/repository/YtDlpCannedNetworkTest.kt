package com.cocode.babakcast.data.repository

import java.io.File
import java.nio.file.Files
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The yt-dlp installer on the real [ReleaseSource.github] policy, against a network that is only a
 * list of canned answers: every address it asks for is recorded, so a test can say that an address
 * was never requested. (Which addresses the policy allows is in [YtDlpAddressTest].)
 */
class YtDlpCannedNetworkTest {

    private val tag = "2026.09.27.232945"
    private val repo = "yt-dlp-nightly-builds"
    private val source = ReleaseSource.github(repo)
    private val base = "https://github.com/yt-dlp/$repo/releases/download/$tag"
    private val api = "https://api.github.com/repos/yt-dlp/$repo/releases/latest"

    private lateinit var dir: File
    private lateinit var binary: File
    private val oldFile = fakeYtDlp("2025.11.12")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ytdlp_canned_test").toFile()
        binary = File(dir, "yt-dlp").also { it.writeBytes(oldFile) }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun network(seen: MutableList<String>, answer: (String) -> Triple<Int, String, String?>) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            seen += url
            val (code, text, location) = answer(url)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("m").code(code)
                .body(text.toResponseBody("application/json".toMediaType()))
                .also { builder -> location?.let { builder.header("Location", it) } }
                .build()
        }.build()

    private fun releaseJson(sumsUrl: String, fileUrl: String, releaseTag: String = tag) = """{"tag_name":"$releaseTag","assets":[
        {"name":"SHA2-256SUMS","browser_download_url":"$sumsUrl"},
        {"name":"yt-dlp","browser_download_url":"$fileUrl"}]}"""

    private val sums = "${sha256Hex(fakeYtDlp(tag))}  yt-dlp\n"

    private fun assertRefused(client: OkHttpClient) {
        val failure = runCatching { YtDlpInstaller(client, source, binary).update() }.exceptionOrNull()
        assertTrue("must refuse", failure != null)
        assertArrayEquals(oldFile, binary.readBytes())
    }

    /** A network where the yt-dlp file's address redirects to [location]. */
    private fun redirectingTo(seen: MutableList<String>, location: String) = network(seen) { url ->
        when (url) {
            api -> Triple(200, releaseJson("$base/SHA2-256SUMS", "$base/yt-dlp"), null)
            "$base/SHA2-256SUMS" -> Triple(200, sums, null)
            "$base/yt-dlp" -> Triple(302, "", location)
            else -> Triple(404, "", null)
        }
    }

    @Test
    fun aRedirectToPlainHttp_isRefused_andNeverRequested() {
        val seen = mutableListOf<String>()
        assertRefused(redirectingTo(seen, "http://release-assets.githubusercontent.com/x"))
        assertFalse(seen.any { it.startsWith("http://") })
    }

    @Test
    fun aGitHubRedirectToAnotherTag_isRefused_andNeverRequested() {
        val seen = mutableListOf<String>()
        val other = "https://github.com/yt-dlp/$repo/releases/download/2025.01.01/yt-dlp"
        assertRefused(redirectingTo(seen, other))
        assertFalse(seen.contains(other))
    }

    /** Parsed, each of these redirects leads to an address the real policy allows; only the raw text gives them away. */
    @Test
    fun aRedirectThatHidesDotsOrSeparators_isRefused_andNeverRequested_withTheRealPolicy() {
        val hidden = listOf(
            "https://objects.githubusercontent.com/a/%2E%2E/b?m=dots",
            "https://objects.githubusercontent.com/a/%2e%2e/b?m=lowerdots",
            "https://release-assets.githubusercontent.com/a\\b?m=backslash",
            "https://release-assets.githubusercontent.com/a%5Cb/../c?m=encbackslash",
            "https://objects.githubusercontent.com/a%2Fb/../c?m=slash",
            "$base/%2e/yt-dlp?m=single",
            "$base/%2E/yt-dlp?m=singleupper",
            // scheme-relative, with a "://" inside the path (the parsed address is allowed)
            "//objects.githubusercontent.com/a/%2e%2e/https://x?m=schemerel",
            "//release-assets.githubusercontent.com/a%5C/../https://x?m=schemerel2"
        )
        for (location in hidden) {
            val marker = location.substringAfter("m=")
            val seen = mutableListOf<String>()
            assertRefused(redirectingTo(seen, location))
            assertFalse("$marker was requested: $seen", seen.any { it.contains("m=$marker") })
        }
    }

    @Test
    fun aReleaseFileOnAnotherHostOrRepository_isRefused_beforeAnythingIsSent() {
        for (elsewhere in listOf(
            "https://evil.example/yt-dlp",
            "https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp",
            "https://github.com/yt-dlp/..%2Fother%2Frepo/yt-dlp",
            "$base/%2e/yt-dlp"
        )) {
            val seen = mutableListOf<String>()
            val client = network(seen) { url ->
                if (url == api) Triple(200, releaseJson("$base/SHA2-256SUMS", elsewhere), null) else Triple(404, "", null)
            }
            assertRefused(client)
            assertFalse("$elsewhere was requested", seen.contains(elsewhere))
        }
    }
}
