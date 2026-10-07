package com.cocode.babakcast.data.repository

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
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

private const val SHEBANG = "#!/usr/bin/env python3\n"

/**
 * A yt-dlp file as GitHub serves it: an executable zip, which is a `#!/usr/bin/env python3` line
 * followed by a zip archive whose index points at absolute offsets, exactly as Python's zipapp
 * writes it. [versionPySize] pads yt_dlp/version.py to that many bytes. Without [shebang] it is a
 * plain zip.
 */
fun fakeYtDlp(version: String, versionPySize: Int = 0, shebang: Boolean = true): ByteArray {
    val text = "__version__ = '$version'\n" + "#".repeat(maxOf(0, versionPySize - 40)) + "\n"
    val zip = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("__main__.py"))
            z.write("import yt_dlp\n".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("yt_dlp/version.py"))
            z.write(text.toByteArray())
            z.closeEntry()
        }
    }.toByteArray()
    if (!shebang) return zip
    val prefix = SHEBANG.toByteArray()
    return prefix + withAbsoluteOffsets(zip, prefix.size)
}

/** Moves every offset in the zip's index by [shift], as if the zip had been written after a prefix. */
private fun withAbsoluteOffsets(zip: ByteArray, shift: Int): ByteArray {
    val bytes = zip.copyOf()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val end = (bytes.size - 22 downTo 0).first { buf.getInt(it) == 0x06054b50 }
    val indexStart = buf.getInt(end + 16)
    val indexSize = buf.getInt(end + 12)
    var pos = indexStart
    while (pos < indexStart + indexSize) {
        check(buf.getInt(pos) == 0x02014b50)
        buf.putInt(pos + 42, buf.getInt(pos + 42) + shift)
        pos += 46 + buf.getShort(pos + 28) + buf.getShort(pos + 30) + buf.getShort(pos + 32)
    }
    buf.putInt(end + 16, indexStart + shift)
    return bytes
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * A stand-in for GitHub's "latest release" page and the files it points to: yt-dlp at /yt-dlp
 * and its checksum file at /sums. Each part can answer normally, say nothing at all (a stalled
 * network), wait for [gate], answer with something too large, or redirect.
 */
class FakeGitHub(
    private val tag: String,
    private val file: ByteArray = fakeYtDlp(tag),
    private val declaredSize: Long = file.size.toLong(),
    private val sums: String = "${sha256Hex(file)}  yt-dlp\n",
    private val listSums: Boolean = true
) {
    val server = MockWebServer()

    @Volatile var releaseStalls = false
    @Volatile var fileStalls = false

    /** The release page does not answer until this is counted down (null: answers at once). */
    @Volatile var gate: CountDownLatch? = null

    /** Counted down when the release page is asked for. */
    val releaseAsked = CountDownLatch(1)

    /** Where /yt-dlp redirects to, or null to serve the file there. */
    @Volatile var fileRedirect: String? = null
    @Volatile var hugeRelease = false
    @Volatile var hugeSums = false
    @Volatile var hugeFile = false

    val releaseUrl: String get() = server.url("/release").toString()

    /** Test only: plain HTTP to this one host (the real policy wants HTTPS on GitHub). */
    internal val policy: UrlPolicy get() = UrlPolicy { it.host == server.hostName }

    fun start(): FakeGitHub {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path.also { paths += it.orEmpty() }?.substringBefore('?')) {
                "/release" -> {
                    releaseAsked.countDown()
                    gate?.await(15, TimeUnit.SECONDS)
                    if (releaseStalls) stalled() else if (hugeRelease) big(2L * 1024 * 1024) else json()
                }
                "/sums" -> when {
                    hugeSums -> big(100L * 1024)
                    hugeFile -> MockResponse().setBody("${sha256Hex(bigFile)}  yt-dlp\n")
                    else -> MockResponse().setBody(sums)
                }
                "/yt-dlp" -> when {
                    fileStalls -> stalled()
                    fileRedirect != null -> MockResponse().setResponseCode(302).setHeader("Location", fileRedirect!!)
                    hugeFile -> MockResponse().setBody(Buffer().write(bigFile))
                    else -> MockResponse().setBody(Buffer().write(file))
                }
                "/real" -> MockResponse().setBody(Buffer().write(file))
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        return this
    }

    /** The paths asked for so far, in order. */
    val paths = CopyOnWriteArrayList<String>()

    fun shutdown() = server.shutdown()

    private fun stalled() = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)

    private val bigFile: ByteArray by lazy { ByteArray(21 * 1024 * 1024) { 'a'.code.toByte() } }

    private fun big(bytes: Long) = MockResponse().setBody(Buffer().write(ByteArray(bytes.toInt()) { 'a'.code.toByte() }))

    private fun json(): MockResponse {
        val sumsAsset = if (listSums) """{"name":"SHA2-256SUMS","browser_download_url":"${server.url("/sums")}","size":1},""" else ""
        return MockResponse().setBody(
            """{"tag_name":"$tag","name":"yt-dlp $tag","assets":[
                $sumsAsset
                {"name":"yt-dlp.exe","browser_download_url":"${server.url("/other")}","size":1},
                {"name":"yt-dlp","browser_download_url":"${server.url("/yt-dlp")}","size":$declaredSize}]}"""
        )
    }
}
