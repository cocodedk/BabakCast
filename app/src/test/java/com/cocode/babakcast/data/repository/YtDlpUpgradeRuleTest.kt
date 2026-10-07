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
        assertEquals("2026.01.05", YtDlpVersion.of(fakeYtDlp("2026.01.05").inputStream()))
        assertNull(YtDlpVersion.of(File(dir, "missing")))
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
