package com.cocode.babakcast.data.repository

import java.io.File
import java.nio.file.Files
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Which addresses the real [ReleaseSource.github] lets the yt-dlp update use, and what it does about the rest. */
class YtDlpAddressTest {

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
        dir = Files.createTempDirectory("ytdlp_address_test").toFile()
        binary = File(dir, "yt-dlp").also { it.writeBytes(oldFile) }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun releaseAllows(url: String) = runCatching { source.release(tag).require(url.toHttpUrl()) }.isSuccess

    @Test
    fun theApiAddress_isTheConfiguredChannelsOverHttps() {
        assertTrue(source.apiUrl == api)
        source.api.require(api.toHttpUrl())
        assertFalse(runCatching { source.api.require("http://api.github.com/repos/yt-dlp/$repo/releases/latest".toHttpUrl()) }.isSuccess)
        assertFalse(runCatching { source.api.require("https://github.com/yt-dlp/$repo".toHttpUrl()) }.isSuccess)
    }

    @Test
    fun theFilesOfThisRelease_andGitHubsDownloadHosts_areAllowed() {
        assertTrue(releaseAllows("$base/yt-dlp"))
        assertTrue(releaseAllows("$base/SHA2-256SUMS"))
        assertTrue(releaseAllows("https://release-assets.githubusercontent.com/github-production-release-asset/1/f?sp=r&rscd=attachment%3B+filename%3Dyt-dlp"))
        assertTrue(releaseAllows("https://objects.githubusercontent.com/x/y"))
    }

    @Test
    fun anythingElse_isRefused() {
        val refused = listOf(
            "http://github.com:443/yt-dlp/$repo/releases/download/$tag/yt-dlp",    // not HTTPS
            "http://release-assets.githubusercontent.com/x",
            "https://evil.example/yt-dlp",
            "https://github.com.evil.example/yt-dlp/$repo/releases/download/$tag/yt-dlp",
            "https://github.com@evil.example/yt-dlp/$repo/releases/download/$tag/yt-dlp",
            "https://github.com:8443/yt-dlp/$repo/releases/download/$tag/yt-dlp",
            "https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt-dlp",
            // another release, repository, file, or a longer or shorter path on github.com
            "https://github.com/yt-dlp/$repo/releases/download/2025.01.01/yt-dlp",
            "https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp",
            "https://github.com/someone-else/$repo/releases/download/$tag/yt-dlp",
            "$base/yt-dlp.exe",
            "$base/yt-dlp/extra",
            "https://github.com/yt-dlp/$repo/releases/latest",
            "https://github.com/yt-dlp/$repo",
            "https://github.com/yt-dlp/other-repo"
        )
        for (url in refused) assertFalse("must refuse $url", releaseAllows(url))
    }

    @Test
    fun aPathThatHidesASeparatorOrADot_isRefused_onEveryHost() {
        val refused = listOf(
            "https://github.com/yt-dlp/..%2Fother%2Frepo",
            "https://github.com/yt-dlp/..%2fother%2frepo/releases/download/$tag/yt-dlp",
            "https://github.com/yt-dlp/$repo/releases/download/%2e%2e/other/yt-dlp",
            "https://github.com/yt-dlp/$repo/releases/download/%2E%2E/other/yt-dlp",
            "https://github.com/yt-dlp/$repo/releases/download/$tag%2Fyt-dlp",
            "https://github.com/yt-dlp/$repo/releases/download/$tag%5Cyt-dlp",
            "https://github.com/yt-dlp/$repo/releases/download/$tag/yt-dlp%2e",
            "https://release-assets.githubusercontent.com/a%2Fb",
            "https://objects.githubusercontent.com/a%5Cb",
            "https://objects.githubusercontent.com/a%2e%2e/b"
        )
        for (url in refused) assertFalse("must refuse $url", releaseAllows(url))
        assertFalse(runCatching { source.api.require("https://api.github.com/repos%2Fyt-dlp/x".toHttpUrl()) }.isSuccess)
    }

    // --- the installer on the real policy, with a network that is only a list of canned answers ---

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

    @Test
    fun aRedirectToPlainHttp_isRefused_andNeverRequested() {
        val seen = mutableListOf<String>()
        val client = network(seen) { url ->
            when (url) {
                api -> Triple(200, releaseJson("$base/SHA2-256SUMS", "$base/yt-dlp"), null)
                "$base/SHA2-256SUMS" -> Triple(200, sums, null)
                "$base/yt-dlp" -> Triple(302, "", "http://release-assets.githubusercontent.com/x")
                else -> Triple(404, "", null)
            }
        }
        assertRefused(client)
        assertFalse(seen.any { it.startsWith("http://") })
    }

    @Test
    fun aGitHubRedirectToAnotherTag_isRefused_andNeverRequested() {
        val seen = mutableListOf<String>()
        val other = "https://github.com/yt-dlp/$repo/releases/download/2025.01.01/yt-dlp"
        val client = network(seen) { url ->
            when (url) {
                api -> Triple(200, releaseJson("$base/SHA2-256SUMS", "$base/yt-dlp"), null)
                "$base/SHA2-256SUMS" -> Triple(200, sums, null)
                "$base/yt-dlp" -> Triple(302, "", other)
                else -> Triple(404, "", null)
            }
        }
        assertRefused(client)
        assertFalse(seen.contains(other))
    }

    @Test
    fun theRawText_isCheckedForHiddenDotsAndSeparators_inThePathOnly() {
        val dirty = listOf(
            "https://objects.githubusercontent.com/a/%2E%2E/b", "https://objects.githubusercontent.com/a/%2e/b",
            "https://objects.githubusercontent.com/a%2Fb", "https://objects.githubusercontent.com/a%5cb",
            "https://objects.githubusercontent.com/a\\b", "https://github.com\\@evil.example/x",
            "/a/%2e%2e/b", "a/%2E/b?x=1",
            // a "://" inside the path is not a scheme
            "//objects.githubusercontent.com/a/%2e%2e/https://x", "/a/%2e%2e/https://x",
            "/a/b/https://x/%2E%2E", "https://objects.githubusercontent.com/a/%2e%2e/https://x",
            "//objects.githubusercontent.com/a\\b/https://x"
        )
        val clean = listOf(
            "https://release-assets.githubusercontent.com/github-production-release-asset/1/f?sp=r&sig=a%2Fb%2Bc%3D#x",
            "https://objects.githubusercontent.com/x/y", "/real?sig=%2F", "https://github.com",
            "//release-assets.githubusercontent.com/x/y?next=https://z/%2e", "/download/https://x/ok", "a/b#%2e"
        )
        for (raw in dirty) assertFalse("must refuse $raw", isCleanAddress(raw))
        for (raw in clean) assertTrue("must accept $raw", isCleanAddress(raw))
    }

    @Test
    fun theRawPath_isFoundByTheSchemeAtTheStartOnly() {
        assertEquals("/a/%2e%2e/https://x", rawPath("//objects.githubusercontent.com/a/%2e%2e/https://x"))
        assertEquals("/a/%2e%2e/https://x", rawPath("https://objects.githubusercontent.com/a/%2e%2e/https://x?q=1#f"))
        assertEquals("/a/%2e%2e/https://x", rawPath("/a/%2e%2e/https://x?https://y"))
        assertEquals("a/b", rawPath("a/b#c"))
        assertEquals("", rawPath("https://github.com"))
        assertEquals("", rawPath("//github.com?x=/y"))
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
            val client = network(seen) { url ->
                when (url) {
                    api -> Triple(200, releaseJson("$base/SHA2-256SUMS", "$base/yt-dlp"), null)
                    "$base/SHA2-256SUMS" -> Triple(200, sums, null)
                    "$base/yt-dlp" -> Triple(302, "", location)
                    else -> Triple(404, "", null)
                }
            }
            assertRefused(client)
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
