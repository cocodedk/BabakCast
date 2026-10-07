package com.cocode.babakcast.data.repository

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Replaces the yt-dlp at [binary] with the latest release from GitHub. The phone runs what this
 * installs, so it is strict:
 *  - every address is HTTPS on GitHub, checked before each request (see [UrlPolicy]);
 *  - the file must match the SHA2-256SUMS published with the same release, checked on the bytes
 *    as they are written, before anything is opened or installed;
 *  - nothing it reads is unbounded, and every request has a deadline (about a minute in all);
 *  - the new file replaces the old one in a single rename, and a failure leaves the old one as it was.
 * It does not use the library's updater: that one has no timeouts, deletes the old file before it
 * has the new one, and checks nothing. The caller holds the gate, so no yt-dlp runs meanwhile.
 *
 * What the check does not give: the checksum file comes from the same release as the program, so
 * it catches a damaged or swapped file, not a release GitHub itself serves wrongly. yt-dlp also
 * publishes SHA2-256SUMS.sig, a GPG signature over that file. Checking it would take an OpenPGP
 * library (such as Bouncy Castle's bcpg) and yt-dlp's public key built into the app, and a new
 * app release whenever the key changes.
 */
internal class YtDlpInstaller(
    client: OkHttpClient,
    private val releaseUrl: String,
    private val binary: File,
    policy: UrlPolicy = UrlPolicy.GITHUB,
    private val releaseTimeoutMs: Long = 20_000,
    private val downloadTimeoutMs: Long = 40_000
) {
    private val net = YtDlpTransport(client, policy)

    fun update(): YtDlpUpdateResult {
        val release = Json.parseToJsonElement(net.get(releaseUrl, releaseTimeoutMs) { it.readLimited(MAX_JSON) }).jsonObject
        val tag = release.string("tag_name") ?: throw IOException("The release has no version")
        val installed = YtDlpVersion.of(binary)
        if (installed != null && YtDlpVersion.compare(installed, tag) >= 0) return YtDlpUpdateResult.AlreadyLatest

        val assets = release["assets"]?.jsonArray?.map { it.jsonObject }.orEmpty().associateBy { it.string("name") }
        val file = assets[FILE] ?: throw IOException("The release has no $FILE file")
        val sums = assets[SUMS] ?: throw IOException("The release has no $SUMS file")
        val expected = expectedSha256(net.get(address(sums), releaseTimeoutMs) { it.readLimited(MAX_SUMS) })

        val staging = File(binary.parentFile, "$FILE.download")
        try {
            val actual = net.get(address(file), downloadTimeoutMs) { stage(it, staging) }
            if (actual != expected) throw IOException("The download does not match the published checksum")
            val size = file["size"]?.jsonPrimitive?.longOrNull
            if (size != null && staging.length() != size) throw IOException("The download is incomplete")
            if (YtDlpVersion.of(staging) != tag) throw IOException("The download is not yt-dlp $tag")
            if (!staging.renameTo(binary)) throw IOException("Could not put the new yt-dlp in place")
        } finally {
            staging.delete()
        }
        return YtDlpUpdateResult.Updated
    }

    /** Streams the answer into [target], never holding it in memory, and returns its SHA-256 in hex. */
    private fun stage(response: Response, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        response.body.byteStream().use { input ->
            target.outputStream().use { output ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE) throw IOException("The download is too large")
                    digest.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun address(asset: JsonObject): String =
        asset.string("browser_download_url") ?: throw IOException("A release file has no address")

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        const val FILE = "yt-dlp"
        const val SUMS = "SHA2-256SUMS"
        private const val MAX_JSON = 1L * 1024 * 1024
        private const val MAX_SUMS = 64L * 1024
        private const val MAX_FILE = 20L * 1024 * 1024
        private val SUMS_LINE = Regex("^([0-9a-f]{64})  $FILE$")

        /**
         * The SHA-256 the checksum file gives for yt-dlp: exactly one line, `<64 hex digits>`, two
         * spaces and `yt-dlp`. Anything else (no such line, a bad hash, two lines) is an error.
         */
        fun expectedSha256(sums: String): String {
            val lines = sums.lines().mapNotNull { SUMS_LINE.matchEntire(it)?.groupValues?.get(1) }
            return lines.singleOrNull() ?: throw IOException("The checksum file has no usable $FILE line")
        }
    }
}
