package com.cocode.babakcast.data.repository

import java.io.File
import java.nio.file.Files
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The phone runs what [YtDlpInstaller] installs, so it must only take a file whose SHA-256 is the
 * one published with the same release, from addresses inside that release, and never read more
 * than it should. Every failure leaves the installed file exactly as it was. (Which addresses
 * the real policy allows is in [YtDlpAddressTest].)
 */
class YtDlpInstallerTest {

    private lateinit var dir: File
    private lateinit var binary: File
    private lateinit var github: FakeGitHub
    private val tag = "2026.09.27.232945"
    private val otherTag = "2025.01.01"
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

    private fun update(): YtDlpUpdateResult = YtDlpInstaller(OkHttpClient(), github.source, binary).update()

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
        assertRefusedAndUntouched("a file that is not yt-dlp", reason = "checksum")
    }

    // --- the files must belong to the release named by tag_name ---

    @Test
    fun aChecksumFileFromAnotherRelease_isNotUsed_andNeverRequested() {
        withGitHub(FakeGitHub(tag, sumsTag = otherTag))
        assertRefusedAndUntouched("a SUMS address in another tag", reason = "points outside release")
        assertFalse("the other release was requested", github.paths.any { it.contains(otherTag) })
    }

    @Test
    fun aReleaseTagThatIsNotAVersion_isNotUsed() {
        for (bad in listOf("latest", "2026.09.27.232945/../x", "v2026.09.27", "2026.9.27", "2026.09.27.")) {
            withGitHub(FakeGitHub(bad))
            assertRefusedAndUntouched("tag $bad", reason = "no valid version")
        }
    }

    @Test
    fun aRedirectToAnotherRelease_isRefused_beforeItIsFollowed() {
        github.fileRedirect = github.server.url("/download/$otherTag/yt-dlp").toString()
        assertRefusedAndUntouched("a redirect to another tag")
        assertFalse("the other release was requested", github.paths.any { it.contains(otherTag) })
    }

    @Test
    fun aRedirectToAnotherHost_isRefused_andNeverRequested() {
        github.fileRedirect = "http://127.0.0.1:${github.server.port}/real?elsewhere=1"
        assertRefusedAndUntouched("a redirect to a host that is not on the list")
        assertFalse("the other host was requested", github.paths.any { it.contains("elsewhere") })
    }

    @Test
    fun aRedirectWithinTheRelease_isFollowed() {
        github.fileRedirect = github.server.url("/real?second=1").toString()
        assertEquals(YtDlpUpdateResult.Updated, update())
        assertTrue(github.paths.any { it.contains("second") })
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
