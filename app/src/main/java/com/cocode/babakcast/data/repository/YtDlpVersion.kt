package com.cocode.babakcast.data.repository

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * yt-dlp is a zip archive that carries its own version in yt_dlp/version.py, as a date:
 * 2025.11.12 for a stable release, 2026.09.27.232945 for a nightly (the last part is the time).
 * Reading it from the file itself means the answer can never disagree with the binary the way
 * a saved record can, for example after Android restores an app's preferences without its files.
 */
internal object YtDlpVersion {
    private const val VERSION_ENTRY = "yt_dlp/version.py"
    private val VERSION = Regex("""__version__\s*=\s*'([^']+)'""")

    /** The version of the yt-dlp file at [file], or null when it is missing or not a yt-dlp. */
    fun of(file: File): String? = try {
        ZipFile(file).use { zip ->
            zip.getEntry(VERSION_ENTRY)?.let { entry ->
                parse(zip.getInputStream(entry).readBytes().decodeToString())
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Same, for a yt-dlp read from a stream (the copy that ships inside youtubedl-android). */
    fun of(stream: InputStream): String? = try {
        ZipInputStream(stream).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == VERSION_ENTRY }
                ?.let { parse(zip.readBytes().decodeToString()) }
        }
    } catch (e: Exception) {
        null
    }

    private fun parse(text: String): String? = VERSION.find(text)?.groupValues?.get(1)

    /** Negative when [a] is older than [b]. Compared part by part, as numbers. */
    fun compare(a: String, b: String): Int {
        val left = a.split('.').map { it.toLongOrNull() ?: 0L }
        val right = b.split('.').map { it.toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0L }.compareTo(right.getOrElse(i) { 0L })
            if (diff != 0) return diff
        }
        return 0
    }
}

/**
 * After an app update: keep the yt-dlp the user installed from Settings when it is the same
 * as, or newer than, the one the new app ships, so an app update does not bring back an old
 * yt-dlp that YouTube rejects. Reset it when the shipped one is newer, or when the installed
 * file is broken. [shipped] is null when the shipped version could not be read; the installed
 * one stays then.
 */
internal fun shouldResetYtdlp(installed: String?, shipped: String?): Boolean = when {
    installed == null -> true
    shipped == null -> false
    else -> YtDlpVersion.compare(shipped, installed) > 0
}

/** Applies [shouldResetYtdlp] to the yt-dlp in [dir]: deletes [dir] when it says reset. */
internal fun resetYtdlpIfNeeded(dir: File, shippedVersion: String?): Boolean {
    if (!shouldResetYtdlp(YtDlpVersion.of(File(dir, "yt-dlp")), shippedVersion)) return false
    dir.deleteRecursively()
    return true
}
