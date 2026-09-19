package com.cocode.babakcast.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [shouldReExtractYtdlp] is the pure decision behind the fdroid-only re-extraction fix in
 * [YoutubeDLReady]: youtubedl-android 0.18.1's init_ytdlp() only copies the bundled yt-dlp
 * when the previously extracted file is absent, so on its own it never notices that this
 * app shipped a newer one. These tests cover the decision in isolation, without touching
 * the filesystem or SharedPreferences that the real caller uses.
 */
class YoutubeDLReadyTest {

    @Test
    fun whenNeverRecorded_shouldReExtract() {
        // UNSET_VERSION_CODE (-1) is what a fresh SharedPreferences read returns; a real
        // versionCode is always positive, so this always compares unequal.
        assertTrue(shouldReExtractYtdlp(UNSET_VERSION_CODE, currentVersionCode = 7))
    }

    @Test
    fun whenRecordedMatchesCurrent_shouldNotReExtract() {
        assertFalse(shouldReExtractYtdlp(recordedVersionCode = 7, currentVersionCode = 7))
    }

    @Test
    fun whenRecordedIsOlderThanCurrent_shouldReExtract() {
        // The common case: an app update bundled a newer yt-dlp.
        assertTrue(shouldReExtractYtdlp(recordedVersionCode = 7, currentVersionCode = 8))
    }

    @Test
    fun whenRecordedIsNewerThanCurrent_shouldReExtract() {
        // A downgrade, or replacing a github install (versionCode from run_number) with an
        // fdroid one (versionCode from the release tag) — either way the extracted binary
        // no longer matches what this build shipped, so it must be re-extracted too.
        assertTrue(shouldReExtractYtdlp(recordedVersionCode = 8, currentVersionCode = 7))
    }
}
