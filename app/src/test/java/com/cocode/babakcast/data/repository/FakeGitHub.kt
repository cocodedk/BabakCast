package com.cocode.babakcast.data.repository

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer

/** What a yt-dlp file looks like to [YtDlpVersion]: a zip with yt_dlp/version.py in it. */
fun fakeYtDlp(version: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("yt_dlp/version.py"))
        zip.write("__version__ = '$version'\n".toByteArray())
        zip.closeEntry()
    }
    return out.toByteArray()
}

/**
 * A stand-in for GitHub's "latest release" page and the yt-dlp file it points to. Each part can
 * be made to answer normally, to say nothing at all (a stalled network), or to wait for [gate].
 */
class FakeGitHub(
    private val tag: String,
    private val file: ByteArray = fakeYtDlp(tag),
    private val declaredSize: Long = file.size.toLong()
) {
    val server = MockWebServer()

    /** What the release page does; the file is requested at /yt-dlp. */
    @Volatile var releaseStalls = false
    @Volatile var fileStalls = false

    /** The release page does not answer until this is counted down (null: answers at once). */
    @Volatile var gate: CountDownLatch? = null

    /** Counted down when the release page is asked for. */
    val releaseAsked = CountDownLatch(1)

    val releaseUrl: String get() = server.url("/release").toString()

    fun start(): FakeGitHub {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/release" -> {
                    releaseAsked.countDown()
                    gate?.await(15, TimeUnit.SECONDS)
                    if (releaseStalls) stalled() else json()
                }
                "/yt-dlp" -> if (fileStalls) stalled() else MockResponse().setBody(Buffer().write(file))
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        return this
    }

    fun shutdown() = server.shutdown()

    private fun stalled() = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)

    private fun json() = MockResponse().setBody(
        """{"tag_name":"$tag","name":"yt-dlp $tag","assets":[
            {"name":"yt-dlp.exe","browser_download_url":"${server.url("/other")}","size":1},
            {"name":"yt-dlp","browser_download_url":"${server.url("/yt-dlp")}","size":$declaredSize}]}"""
    )
}
