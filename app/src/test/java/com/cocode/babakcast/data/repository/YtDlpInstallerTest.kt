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

/**
 * The phone runs what [YtDlpInstaller] installs, so it must only take a file whose SHA-256 is the
 * one published with the same release, only talk to HTTPS addresses on GitHub, and never read
 * more than it should. Every failure leaves the installed file exactly as it was.
 */
class YtDlpInstallerTest {

    private lateinit var dir: File
    private lateinit var binary: File
    private lateinit var github: FakeGitHub
    private val tag = "2026.09.27.232945"
    private val oldFile = fakeYtDlp("2025.11.12")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ytdlp_installer_test").toFile()
        binary = File(dir, "yt-dlp").also { it.writeBytes(oldFile) }
        github = FakeGitHub(tag).start()
    }

    @After
    fun tearDown() {
        github.shutdown()
        dir.deleteRecursively()
    }

    private fun update(): YtDlpUpdateResult =
        YtDlpInstaller(OkHttpClient(), github.releaseUrl, binary, github.policy).update()

    private fun withGitHub(g: FakeGitHub) {
        github.shutdown()
        github = g.start()
    }

    /** The update must fail, for [reason] when given, and leave the installed file and the folder as they were. */
    private fun assertRefusedAndUntouched(message: String, reason: String? = null) {
        val failure = runCatching { update() }.exceptionOrNull()
        assertTrue("$message: must be refused", failure != null)
        if (reason != null) assertTrue("$message: ${failure?.message}", failure?.message.orEmpty().contains(reason))
        assertArrayEquals(oldFile, binary.readBytes())
        assertFalse(File(dir, "yt-dlp.download").exists())
    }

    // --- integrity: the file must match SHA2-256SUMS of the same release ---

    @Test
    fun aFileThatMatchesThePublishedChecksum_isInstalled() {
        assertEquals(YtDlpUpdateResult.Updated, update())
        assertEquals(tag, YtDlpVersion.of(binary))
    }

    @Test
    fun aGoodYtDlp_withTheWrongChecksum_isNotInstalled() {
        withGitHub(FakeGitHub(tag, sums = "${"0".repeat(64)}  yt-dlp\n"))
        assertRefusedAndUntouched("a valid yt-dlp that does not match the checksum")
    }

    @Test
    fun aMissingMalformedOrAmbiguousChecksumLine_isNotInstalled() {
        val good = sha256Hex(fakeYtDlp(tag))
        val sums = listOf(
            "",                                              // empty file
            "$good  yt-dlp.exe\n",                           // only another file's line
            "${good.uppercase()}  yt-dlp\n",                 // not lower-case hex
            "${good.dropLast(1)}  yt-dlp\n",                 // 63 digits
            "$good yt-dlp\n",                                // one space
            "$good  yt-dlp\n$good  yt-dlp\n",                // twice
            "<html>Not Found</html>"                         // an error page
        )
        for (text in sums) {
            withGitHub(FakeGitHub(tag, sums = text))
            assertRefusedAndUntouched("checksum file: ${text.take(30)}")
        }
    }

    @Test
    fun aRelease_withNoChecksumFile_isNotInstalled() {
        withGitHub(FakeGitHub(tag, listSums = false))
        assertRefusedAndUntouched("no SHA2-256SUMS in the release")
    }

    @Test
    fun theChecksumIsCheckedBeforeTheFileIsOpened() {
        // Not a zip at all, with the wrong checksum: refused for the checksum, which comes first.
        val junk = "#!/bin/sh\nrm -rf /\n".toByteArray()
        withGitHub(FakeGitHub(tag, file = junk, sums = "${sha256Hex(fakeYtDlp(tag))}  yt-dlp\n"))
        val failure = runCatching { update() }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("checksum"))
        assertArrayEquals(oldFile, binary.readBytes())
    }

    // --- transport: HTTPS on GitHub only, every hop checked before it is requested ---

    @Test
    fun theRealPolicy_allowsGitHubOverHttpsOnly() {
        val ok = listOf(
            "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest",
            "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/download/1/yt-dlp",
            "https://objects.githubusercontent.com/x",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1?sp=r"
        )
        val refused = listOf(
            "http://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
            "http://release-assets.githubusercontent.com/x",
            "http://github.com:443/yt-dlp/x", // the right port, but not HTTPS
            "https://evil.example/yt-dlp",
            "https://github.com.evil.example/yt-dlp/x",
            "https://github.com@evil.example/yt-dlp/x",
            "https://evil.example/github.com/yt-dlp/x",
            "https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt-dlp",
            "https://github.com/someone-else/yt-dlp/releases/download/1/yt-dlp",
            "https://github.com:8443/yt-dlp/x"
        )
        ok.forEach { UrlPolicy.GITHUB.require(it.toHttpUrl()) }
        for (url in refused) {
            val allowed = runCatching { UrlPolicy.GITHUB.require(url.toHttpUrl()) }.isSuccess
            assertFalse("must refuse $url", allowed)
        }
    }

    @Test
    fun aRedirectToAnotherHost_isRefused_andNeverRequested() {
        github.fileRedirect = "http://127.0.0.1:${github.server.port}/yt-dlp?elsewhere=1"
        assertRefusedAndUntouched("a redirect to a host that is not on the list")
        assertFalse("the other host was requested", github.paths.any { it.contains("elsewhere") })
    }

    @Test
    fun aRedirectWithinTheList_isFollowed() {
        github.fileRedirect = github.server.url("/real?second=1").toString()
        assertEquals(YtDlpUpdateResult.Updated, update())
        assertTrue(github.paths.any { it.contains("second") })
    }

    private fun network(handler: (String) -> Response.Builder.() -> Unit, seen: MutableList<String>) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            seen += url
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("m")
                .also(handler(url)).build()
        }.build()

    private fun answer(code: Int, text: String = "", location: String? = null): Response.Builder.() -> Unit = {
        code(code)
        body(text.toResponseBody("application/json".toMediaType()))
        location?.let { header("Location", it) }
    }

    private val api = "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"
    private val base = "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/download/$tag"

    private fun releaseJson(fileUrl: String) = """{"tag_name":"$tag","assets":[
        {"name":"SHA2-256SUMS","browser_download_url":"$base/SHA2-256SUMS"},
        {"name":"yt-dlp","browser_download_url":"$fileUrl"}]}"""

    @Test
    fun aRedirectToPlainHttp_isRefused_withTheRealPolicy() {
        val seen = mutableListOf<String>()
        val client = network({ url ->
            when (url) {
                api -> answer(200, releaseJson("$base/yt-dlp"))
                "$base/SHA2-256SUMS" -> answer(200, "${sha256Hex(fakeYtDlp(tag))}  yt-dlp\n")
                "$base/yt-dlp" -> answer(302, location = "http://release-assets.githubusercontent.com/x")
                else -> answer(404)
            }
        }, seen)

        val failure = runCatching { YtDlpInstaller(client, api, binary).update() }.exceptionOrNull()

        assertTrue("must refuse", failure != null)
        assertFalse(seen.any { it.startsWith("http://") })
        assertArrayEquals(oldFile, binary.readBytes())
    }

    @Test
    fun aReleaseFileOnAnotherHost_isRefused_beforeAnythingIsSent_withTheRealPolicy() {
        val seen = mutableListOf<String>()
        val client = network({ url ->
            when (url) {
                api -> answer(200, releaseJson("https://evil.example/yt-dlp"))
                "$base/SHA2-256SUMS" -> answer(200, "${sha256Hex(fakeYtDlp(tag))}  yt-dlp\n")
                else -> answer(404)
            }
        }, seen)

        val failure = runCatching { YtDlpInstaller(client, api, binary).update() }.exceptionOrNull()

        assertTrue("must refuse", failure != null)
        assertFalse(seen.any { it.contains("evil.example") })
    }

    // --- limits: nothing unbounded, nothing installed on an oversize answer ---

    @Test
    fun anOversizeReleasePage_isNotInstalled() {
        github.hugeRelease = true
        assertRefusedAndUntouched("a release page over 1 MB", reason = "larger than")
    }

    @Test
    fun anOversizeChecksumFile_isNotInstalled() {
        github.hugeSums = true
        assertRefusedAndUntouched("a checksum file over 64 KB", reason = "larger than")
    }

    @Test
    fun anOversizeFile_isCutOff_withNoLeftover() {
        github.hugeFile = true
        assertRefusedAndUntouched("a download over 20 MB", reason = "too large")
    }
}
