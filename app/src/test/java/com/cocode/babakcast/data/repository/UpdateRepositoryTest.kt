package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.remote.LatestRelease
import com.cocode.babakcast.domain.update.AppVersion
import com.cocode.babakcast.domain.update.UpdateAvailability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private fun repo(installedVersion: String, fakeResult: Result<LatestRelease>) =
        UpdateRepository(
            installedVersionName = installedVersion,
            fetchLatest = { fakeResult }
        )

    @Test
    fun reportsUpdateAvailable_whenLatestIsNewer() = runBlocking {
        val release = LatestRelease(
            tagName = "v1.0.40",
            apkDownloadUrl = "https://example.com/BabakCast.apk",
            apkSizeBytes = 12_345_678
        )
        val result = repo("1.0.39", Result.success(release)).checkForUpdate()

        assertTrue("expected UpdateAvailable, got $result", result is UpdateAvailability.UpdateAvailable)
        result as UpdateAvailability.UpdateAvailable
        assertEquals(AppVersion(1, 0, 40), result.latest)
        assertEquals("https://example.com/BabakCast.apk", result.apkDownloadUrl)
        assertEquals(12_345_678L, result.apkSizeBytes)
    }

    @Test
    fun reportsUpToDate_whenLatestEqualsInstalled() = runBlocking {
        val release = LatestRelease("v1.0.39", "https://example.com/x.apk", 1)
        val result = repo("1.0.39", Result.success(release)).checkForUpdate()

        assertEquals(UpdateAvailability.UpToDate(AppVersion(1, 0, 39)), result)
    }

    @Test
    fun reportsUpToDate_whenInstalledIsNewerThanLatest() = runBlocking {
        // Local dev build ahead of public release: the user should never be told to "downgrade".
        val release = LatestRelease("v1.0.30", "https://example.com/x.apk", 1)
        val result = repo("1.0.39", Result.success(release)).checkForUpdate()

        assertTrue(result is UpdateAvailability.UpToDate)
    }

    @Test
    fun reportsCheckFailed_whenClientFails() = runBlocking {
        val result = repo("1.0.39", Result.failure(java.io.IOException("offline"))).checkForUpdate()

        assertTrue(result is UpdateAvailability.CheckFailed)
        result as UpdateAvailability.CheckFailed
        assertTrue(result.reason.contains("offline", ignoreCase = true))
    }

    @Test
    fun reportsCheckFailed_whenLatestTagIsUnparseable() = runBlocking {
        val release = LatestRelease("not-a-version", "https://example.com/x.apk", 1)
        val result = repo("1.0.39", Result.success(release)).checkForUpdate()

        assertTrue(result is UpdateAvailability.CheckFailed)
    }

    @Test
    fun reportsCheckFailed_whenInstalledTagIsUnparseable() = runBlocking {
        val release = LatestRelease("v1.0.40", "https://example.com/x.apk", 1)
        val result = repo("garbage", Result.success(release)).checkForUpdate()

        assertTrue(result is UpdateAvailability.CheckFailed)
    }
}
