package com.cocode.babakcast.domain.audio

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cocode.babakcast.domain.FfmpegCommands
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes ID3 "Part n of N" tags onto split audio parts so the sequence is visible
 * in any player, even when a messaging app delivers the files out of order.
 * Best-effort: a failed write leaves that part untagged rather than failing the share.
 */
@Singleton
class AudioPartTagger @Inject constructor() {

    data class PartMetadata(val title: String, val track: String, val album: String)

    companion object {
        private const val TAG = "AudioPartTagger"

        fun partMetadata(displayTitle: String, partIndex: Int, totalParts: Int): PartMetadata {
            val album = displayTitle.trim()
            val label = "Part $partIndex of $totalParts"
            val title = if (album.isBlank()) label else "$album ($label)"
            return PartMetadata(title = title, track = "$partIndex/$totalParts", album = album)
        }
    }

    fun tagParts(files: List<File>, displayTitle: String): List<File> {
        val total = files.size
        files.forEachIndexed { index, file ->
            val meta = partMetadata(displayTitle, index + 1, total)
            runCatching { writeTags(file, meta) }
                .onFailure { Log.w(TAG, "tagParts failed name=${file.name}", it) }
        }
        return files
    }

    private fun writeTags(file: File, meta: PartMetadata) {
        val dir = file.parentFile ?: return
        val temp = File(dir, "${file.nameWithoutExtension}.tagged.${file.extension}")
        val command = FfmpegCommands.buildAddMetadataCommand(
            inputFile = file,
            outputFile = temp,
            title = meta.title,
            track = meta.track,
            album = meta.album
        )
        val session = FFmpegKit.execute(command)
        val ok = ReturnCode.isSuccess(session.returnCode) && temp.exists() && temp.length() > 0
        if (ok && file.delete()) {
            temp.renameTo(file)
        } else {
            temp.delete()
        }
    }
}
