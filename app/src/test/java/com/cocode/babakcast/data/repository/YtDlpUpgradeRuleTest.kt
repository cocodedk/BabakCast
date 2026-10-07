package com.cocode.babakcast.data.repository

import com.cocode.babakcast.BuildConfig
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What an app update does to the yt-dlp the user installed from Settings: keep it when it is
 * the same as or newer than the one the new app ships (otherwise the update would bring back
 * a yt-dlp that YouTube rejects), reset it when the shipped one is newer or the file is
 * broken. Also: the fdroid flavour never refreshes yt-dlp at start.
 */
class YtDlpUpgradeRuleTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ytdlp_upgrade_test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        scratch.deleteRecursively()
    }

    private fun install(version: String) = File(dir, "yt-dlp").writeBytes(fakeYtDlp(version))

    @Test
    fun anUpdatedCopy_newerThanTheShippedOne_survivesAnAppUpdate() {
        install("2026.09.27.232945")

        assertFalse(resetYtdlpIfNeeded(dir, shippedVersion = "2025.11.12"))

        assertEquals("2026.09.27.232945", YtDlpVersion.of(File(dir, "yt-dlp")))
    }

    @Test
    fun aCopyEqualToTheShippedOne_isKept() {
        install("2025.11.12")
        assertFalse(resetYtdlpIfNeeded(dir, shippedVersion = "2025.11.12"))
        assertTrue(File(dir, "yt-dlp").exists())
    }

    @Test
    fun aCopyOlderThanTheShippedOne_isReset() {
        install("2025.11.12")

        assertTrue(resetYtdlpIfNeeded(dir, shippedVersion = "2026.01.05"))

        assertFalse("the library extracts the shipped copy again", dir.exists())
    }

    @Test
    fun aBrokenCopy_isReset() {
        File(dir, "yt-dlp").writeText("<html>not found</html>")
        assertTrue(resetYtdlpIfNeeded(dir, shippedVersion = "2025.11.12"))
        assertFalse(dir.exists())
    }

    @Test
    fun whenTheShippedVersionCannotBeRead_aWorkingCopyStays() {
        install("2026.09.27.232945")
        assertFalse(resetYtdlpIfNeeded(dir, shippedVersion = null))
    }

    @Test
    fun versionsCompareAsDates_andANightlyBeatsTheStableOfTheSameDay() {
        assertTrue(YtDlpVersion.compare("2026.09.27.232945", "2025.11.12") > 0)
        assertTrue(YtDlpVersion.compare("2025.11.12", "2025.11.12.000001") < 0)
        assertTrue(YtDlpVersion.compare("2025.9.1", "2025.11.12") < 0)
        assertEquals(0, YtDlpVersion.compare("2025.11.12", "2025.11.12.0"))
    }

    @Test
    fun theVersionIsReadFromAFileAndFromAStream() {
        install("2025.11.12")
        assertEquals("2025.11.12", YtDlpVersion.of(File(dir, "yt-dlp")))
        assertEquals("2026.01.05", shipped("2026.01.05"))
        assertNull(YtDlpVersion.of(File(dir, "missing")))
    }

    // --- the real file format: a "#!" line in front of the zip ---

    private val scratch: File by lazy { Files.createTempDirectory("ytdlp_scratch").toFile() }

    /** The yt-dlp that ships inside the app, as a stream, the way the resource is read. */
    private fun shipped(version: String) = YtDlpVersion.ofStream(fakeYtDlp(version).inputStream(), scratch)

    @Test
    fun theTestFileIsInTheRealFormat_aZipReaderAtByteZeroFindsNothing() {
        val bytes = fakeYtDlp("2025.11.12")
        assertTrue(String(bytes, 0, 2) == "#!")
        assertNull(java.util.zip.ZipInputStream(bytes.inputStream()).nextEntry)
    }

    @Test
    fun theShippedVersion_isReadThroughTheLeadingLine() {
        assertEquals("2026.01.05", shipped("2026.01.05"))
        assertEquals("2025.11.12", YtDlpVersion.of(File(dir, "yt-dlp").also { it.writeBytes(fakeYtDlp("2025.11.12")) }))
    }

    @Test
    fun aShippedCopyNewerThanTheInstalledOne_resetsIt() {
        install("2025.11.12")
        val bundled = shipped("2026.01.05")

        assertEquals("2026.01.05", bundled)
        assertTrue(resetYtdlpIfNeeded(dir, bundled))
        assertFalse(dir.exists())
    }

    @Test
    fun anInstalledCopyNewerThanTheShippedOne_isKept() {
        install("2026.09.27.232945")
        assertFalse(resetYtdlpIfNeeded(dir, shipped("2025.11.12")))
        assertTrue(File(dir, "yt-dlp").exists())
    }

    @Test
    fun aVersionFileOfAnyRealisticSize_isReadButAHugeOneIsNot() {
        assertEquals("2026.01.05", YtDlpVersion.of(File(dir, "a").also { it.writeBytes(fakeYtDlp("2026.01.05", versionPySize = 4_000)) }))
        assertNull(YtDlpVersion.of(File(dir, "b").also { it.writeBytes(fakeYtDlp("2026.01.05", versionPySize = 200_000)) }))
    }

    @Test
    fun fdroid_neverRefreshesAtStart_githubDoes() {
        assertFalse(shouldRefreshAtStart(selfUpdate = false, lastUpdateDay = 0, today = 20_000))
        assertTrue(shouldRefreshAtStart(selfUpdate = true, lastUpdateDay = 19_999, today = 20_000))
        assertFalse(shouldRefreshAtStart(selfUpdate = true, lastUpdateDay = 20_000, today = 20_000))
        // The flag each flavour is built with.
        assertEquals(BuildConfig.FLAVOR == "github", BuildConfig.YTDLP_SELF_UPDATE)
    }
}
