package com.cocode.babakcast.data.repository

import com.cocode.babakcast.util.AppError
import com.cocode.babakcast.util.AppErrorException
import com.cocode.babakcast.util.ErrorHandler
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The fdroid flavour updates yt-dlp only when the user taps "Update yt-dlp". These tests drive
 * the real [YtDlpUpdater], [YtDlpInstaller] and [YoutubeDlWrapper] against a fake GitHub:
 * an update and a yt-dlp run never wait for each other (the second is refused at once), a
 * stalled network ends in a failure and frees the gate, and a failed update changes nothing.
 */
class YtDlpUpdaterTest {

    private val originalRunner = YtDlpUpdater.runner
    private lateinit var dir: File
    private lateinit var binary: File
    private lateinit var github: FakeGitHub
    private val request = YoutubeDLRequest("https://www.youtube.com/watch?v=jNQXAC9IVRw")
    private val oldFile = fakeYtDlp("2025.11.12")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ytdlp_update_test").toFile()
        binary = File(dir, "yt-dlp").also { it.writeBytes(oldFile) }
        github = FakeGitHub("2026.09.27.232945").start()
        YtDlpUpdater.runner = { _, _ -> YoutubeDLResponse(emptyList(), 0, 0, "{}", "") }
    }

    @After
    fun tearDown() {
        YtDlpUpdater.runner = originalRunner
        github.shutdown()
        dir.deleteRecursively()
    }

    private fun installer(timeoutMs: Long = 5_000) =
        YtDlpInstaller(OkHttpClient(), github.releaseUrl, binary, timeoutMs, timeoutMs)

    @Test
    fun update_installsTheNewerRelease() {
        assertEquals(YtDlpUpdateResult.Updated, YtDlpUpdater.update(installer()))
        assertEquals("2026.09.27.232945", YtDlpVersion.of(binary))
        assertFalse(File(dir, "yt-dlp.download").exists())
    }

    @Test
    fun update_whenTheInstalledFileIsAlreadyThatRelease_saysSoAndDownloadsNothing() {
        binary.writeBytes(fakeYtDlp("2026.09.27.232945"))
        assertEquals(YtDlpUpdateResult.AlreadyLatest, YtDlpUpdater.update(installer()))
        assertEquals(1, github.server.requestCount)
    }

    /** After a restore the app's records can name a release the restored file is not: only the file counts. */
    @Test
    fun update_afterARestore_goesByTheFileNotByAnyRecord() {
        assertEquals("2025.11.12", YtDlpVersion.of(binary))
        assertEquals(YtDlpUpdateResult.Updated, YtDlpUpdater.update(installer()))
    }

    @Test
    fun update_whileAYtDlpRunIsGoing_isRefusedAtOnce_andChangesNothing() {
        val running = CountDownLatch(1)
        val finish = CountDownLatch(1)
        YtDlpUpdater.runner = { _, _ ->
            running.countDown()
            finish.await(10, TimeUnit.SECONDS)
            YoutubeDLResponse(emptyList(), 0, 0, "", "")
        }
        val download = Thread { YoutubeDlWrapper("test").executeDownload(request) {} }
        download.start()
        assertTrue(running.await(5, TimeUnit.SECONDS))

        val started = System.nanoTime()
        val result = YtDlpUpdater.update(installer())

        assertEquals(YtDlpUpdateResult.DownloadRunning, result)
        assertTrue("refused at once", System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1))
        assertEquals(0, github.server.requestCount)
        assertArrayEquals(oldFile, binary.readBytes())
        finish.countDown()
        download.join(5_000)
    }

    @Test
    fun aYtDlpRun_whileAnUpdateRuns_isRefusedAtOnce_withAMessageForTheScreen() {
        github.gate = CountDownLatch(1)
        val update = Thread { YtDlpUpdater.update(installer(timeoutMs = 20_000)) }
        update.start()
        assertTrue(github.releaseAsked.await(5, TimeUnit.SECONDS))

        val started = System.nanoTime()
        val refused = try {
            YoutubeDlWrapper("test").fetchInfo(request)
            null
        } catch (e: AppErrorException) {
            e
        }

        assertNotNull("the run must be refused, not queued", refused)
        assertTrue("refused at once", System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1))
        assertTrue(ErrorHandler.handleException(refused!!) is AppError.ToolUpdating)
        github.gate!!.countDown()
        update.join(10_000)
        assertEquals("2026.09.27.232945", YtDlpVersion.of(binary))
    }

    @Test(timeout = 20_000)
    fun update_onAStalledNetwork_endsInFailedAtTheDeadline_andFreesTheGate() {
        github.releaseStalls = true
        val started = System.nanoTime()

        val result = YtDlpUpdater.update(installer(timeoutMs = 800))

        assertEquals(YtDlpUpdateResult.Failed, result)
        assertTrue("within the deadline", System.nanoTime() - started < TimeUnit.SECONDS.toNanos(8))
        assertArrayEquals(oldFile, binary.readBytes())
        YoutubeDlWrapper("test").fetchInfo(request) // the gate is free again: not refused
    }

    @Test(timeout = 20_000)
    fun update_whenTheDownloadStalls_failsAndLeavesTheOldFileAndNoLeftover() {
        github.fileStalls = true

        assertEquals(YtDlpUpdateResult.Failed, YtDlpUpdater.update(installer(timeoutMs = 800)))

        assertArrayEquals(oldFile, binary.readBytes())
        assertFalse(File(dir, "yt-dlp.download").exists())
    }

    @Test
    fun update_whenTheDownloadIsShortOrNotThatRelease_isNotInstalled() {
        github.shutdown()
        github = FakeGitHub("2026.09.27.232945", file = fakeYtDlp("2025.01.01")).start()
        assertEquals(YtDlpUpdateResult.Failed, YtDlpUpdater.update(installer()))
        github.shutdown()
        github = FakeGitHub("2026.09.27.232945", declaredSize = 999_999).start()
        assertEquals(YtDlpUpdateResult.Failed, YtDlpUpdater.update(installer()))
        assertArrayEquals(oldFile, binary.readBytes())
    }

    @Test
    fun update_whenGitHubIsUnreachable_isFailedNotAnException() {
        github.shutdown()
        assertEquals(YtDlpUpdateResult.Failed, YtDlpUpdater.update(installer(timeoutMs = 2_000)))
        assertArrayEquals(oldFile, binary.readBytes())
        assertGateIsFree()
    }

    private fun assertGateIsFree() {
        try {
            YoutubeDlWrapper("test").fetchInfo(request)
        } catch (e: AppErrorException) {
            fail("the gate was not released")
        }
    }
}
