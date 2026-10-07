package com.cocode.babakcast.data.repository

import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fdroid flavour updates yt-dlp only when the user taps the button in Settings. The
 * update deletes and rewrites the yt-dlp binary, so it must never overlap a download.
 */
class YtDlpUpdaterTest {

    @Test
    fun update_whileADownloadRuns_reportsItAndChangesNothing() {
        val gate = YtDlpGate()
        val downloading = CountDownLatch(1)
        val finishDownload = CountDownLatch(1)
        val downloader = Thread {
            gate.download {
                downloading.countDown()
                finishDownload.await(5, TimeUnit.SECONDS)
            }
        }
        downloader.start()
        assertTrue(downloading.await(5, TimeUnit.SECONDS))

        val swapped = AtomicBoolean(false)
        val result = gate.update { swapped.set(true); YtDlpUpdateResult.Updated }

        assertEquals(YtDlpUpdateResult.DownloadRunning, result)
        assertFalse(swapped.get())
        finishDownload.countDown()
        downloader.join(5_000)
    }

    @Test
    fun update_withNoDownload_runsAndReturnsItsResult() {
        val gate = YtDlpGate()
        assertEquals(YtDlpUpdateResult.Updated, gate.update { YtDlpUpdateResult.Updated })
        // The write side is released afterwards, so a second update can run.
        assertEquals(YtDlpUpdateResult.AlreadyLatest, gate.update { YtDlpUpdateResult.AlreadyLatest })
    }

    @Test
    fun download_waitsWhileTheBinaryIsBeingSwapped() {
        val gate = YtDlpGate()
        val swapping = CountDownLatch(1)
        val finishSwap = CountDownLatch(1)
        val updater = Thread {
            gate.update {
                swapping.countDown()
                finishSwap.await(5, TimeUnit.SECONDS)
                YtDlpUpdateResult.Updated
            }
        }
        updater.start()
        assertTrue(swapping.await(5, TimeUnit.SECONDS))

        val started = AtomicBoolean(false)
        val downloader = Thread { gate.download { started.set(true) } }
        downloader.start()
        downloader.join(300)
        assertFalse("a download must not start mid-swap", started.get())

        finishSwap.countDown()
        downloader.join(5_000)
        updater.join(5_000)
        assertTrue(started.get())
    }

    @Test
    fun downloads_canRunTogether() {
        val gate = YtDlpGate()
        val both = CountDownLatch(2)
        val threads = List(2) {
            Thread { gate.download { both.countDown(); both.await(5, TimeUnit.SECONDS) } }
        }
        threads.forEach { it.start() }
        assertTrue(both.await(5, TimeUnit.SECONDS))
        threads.forEach { it.join(5_000) }
    }

    @Test
    fun libraryAnswerMapsToTheUsersResult() {
        assertEquals(YtDlpUpdateResult.Updated, toUpdateResult(YoutubeDL.UpdateStatus.DONE))
        assertEquals(
            YtDlpUpdateResult.AlreadyLatest,
            toUpdateResult(YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
        )
        assertEquals(YtDlpUpdateResult.Failed, toUpdateResult(null))
    }
}
