package com.cocode.babakcast

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards two release facts an F-Droid reviewer checked on 1.0.89, by reading the files
 * that decide them (Gradle runs unit tests from the app module directory).
 */
class PackagingGuardTest {

    private fun appFile(path: String): File =
        listOf(File(path), File("app/$path")).first { it.exists() }

    /**
     * ffmpeg-kit-audio ships no 32-bit x86 libraries. An APK carrying x86 would crash with an
     * UnsatisfiedLinkError, which `catch (e: Exception)` does not catch, the first time it
     * split or tagged a file on such a device.
     */
    @Test
    fun abiFilters_keepOnlyTheAbisFfmpegKitShips() {
        val script = appFile("build.gradle.kts").readText()
        val line = script.lines().single { it.contains("abiFilters") }
        val abis = Regex("\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }.toList()
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86_64"), abis)
        assertFalse(abis.contains("x86"))
    }

    /**
     * Every file the app reads or writes lives in its own app-specific folders
     * (getExternalFilesDir, cacheDir, filesDir) or goes out through FileProvider. None of that
     * needs a storage permission on Android 7 to 12, so the manifest must not ask for one.
     */
    @Test
    fun manifest_asksForNoStoragePermission() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("requestLegacyExternalStorage"))
        assertTrue(manifest.contains("android.permission.INTERNET"))
    }

    /**
     * yt-dlp lives in a folder that is not backed up, so the two preference files that record
     * which yt-dlp is installed (the library's, and the app's own) must not be restored alone.
     */
    @Test
    fun backupRules_leaveOutTheYtDlpRecords() {
        val old = appFile("src/main/res/xml/backup_rules.xml").readText()
        val new = appFile("src/main/res/xml/data_extraction_rules.xml").readText()
        // Android 11 and older read backup_rules.xml; Android 12 and newer read each section here.
        val sections = mapOf(
            "backup_rules.xml" to old,
            "cloud-backup" to new.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>"),
            "device-transfer" to new.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        )
        for ((name, rules) in sections) {
            for (record in listOf("youtubedl-android.xml", "ytdlp_update.xml")) {
                assertTrue("$name must exclude $record",
                    rules.contains("""<exclude domain="sharedpref" path="$record"/>"""))
            }
        }
    }

    /** The F-Droid build's download error points to the button that updates yt-dlp; the default does not. */
    @Test
    fun theFdroidBuildsDownloadErrorHint_pointsToUpdateYtDlp() {
        val fdroid = appFile("src/fdroid/res/values/strings.xml").readText()
        val main = appFile("src/main/res/values/strings.xml").readText()
        assertTrue(fdroid.contains("""name="error_hint_ytdlp_failed">"""))
        assertTrue(fdroid.contains("Update yt-dlp"))
        assertFalse(Regex("""name="error_hint_ytdlp_failed">[^<]*Update yt-dlp""").containsMatchIn(main))
    }
}
