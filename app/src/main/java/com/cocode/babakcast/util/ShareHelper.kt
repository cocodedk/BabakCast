package com.cocode.babakcast.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.cocode.babakcast.data.model.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for sharing content via Android share sheet
 */
@Singleton
class ShareHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ShareHelper"
    private val fileProviderAuthority = "${context.packageName}.fileprovider"
    /**
     * Share video files
     */
    fun shareVideos(videoInfo: VideoInfo) {
        val filesToShare = if (videoInfo.splitFiles.isNotEmpty()) {
            videoInfo.splitFiles
        } else {
            videoInfo.file?.let { listOf(it) } ?: emptyList()
        }

        if (filesToShare.isEmpty()) {
            Log.w(tag, "No files to share")
            return
        }

        val chooser = buildShareFilesChooser(
            files = filesToShare,
            mimeType = "video/*",
            title = "Share video",
            text = videoInfo.title.ifBlank { null }
        )
        if (chooser != null) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    /**
     * Share text content
     */
    fun shareText(text: String, title: String = "Share") {
        val chooser = buildShareTextChooser(text, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Share text, falling back to a file if it's too large for some share targets.
     */
    fun shareLongText(
        text: String,
        title: String = "Share",
        fileName: String = "summary.txt",
        forceFile: Boolean = false
    ) {
        val maxInlineChars = 8000
        if (!forceFile && text.length <= maxInlineChars) {
            shareText(text, title)
            return
        }

        try {
            val cacheFile = File(context.cacheDir, fileName)
            cacheFile.writeText(text, Charsets.UTF_8)
            shareFile(cacheFile, "text/plain", title)
        } catch (e: Exception) {
            Log.e(tag, "Failed to write share file, falling back to text", e)
            shareText(text, title)
        }
    }

    /**
     * Share multiple files
     */
    fun shareFiles(
        files: List<File>,
        mimeType: String = "video/*",
        title: String = "Share videos",
        text: String? = null
    ) {
        val chooser = buildShareFilesChooser(files, mimeType, title, text)
        if (chooser == null) {
            Log.w(tag, "No files to share")
            return
        }
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Share single file
     */
    fun shareFile(
        file: File,
        mimeType: String = "application/octet-stream",
        title: String = "Share file",
        text: String? = null
    ) {
        val chooser = buildShareFileChooser(file, mimeType, title, text)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun buildShareTextChooser(text: String, title: String = "Share"): Intent {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(shareIntent, title)
    }

    fun buildShareFilesChooser(
        files: List<File>,
        mimeType: String,
        title: String,
        text: String? = null
    ): Intent? {
        if (files.isEmpty()) return null
        val orderedFiles = normalizeShareFileOrder(files)
        return if (orderedFiles.size == 1) {
            buildShareFileChooser(orderedFiles.first(), mimeType, title, text)
        } else {
            val uris = orderedFiles.map { file ->
                FileProvider.getUriForFile(
                    context,
                    fileProviderAuthority,
                    file
                )
            }
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                if (!text.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(shareIntent, title)
        }
    }

    private fun normalizeShareFileOrder(files: List<File>): List<File> {
        if (files.size < 2) return files

        val withPartNumbers = files.map { file ->
            file to DownloadFileParser.extractPartNumber(file.nameWithoutExtension)
        }
        if (withPartNumbers.any { it.second == null }) {
            return files
        }

        val normalized = withPartNumbers
            .sortedWith(compareBy<Pair<File, Int?>> { it.second }.thenBy { it.first.name })
            .map { it.first }

        Log.d(
            tag,
            "normalizeShareFileOrder applied multipart sort input=[${files.joinToString { it.name }}] output=[${normalized.joinToString { it.name }}]"
        )
        return normalized
    }

    fun buildShareFileChooser(
        file: File,
        mimeType: String,
        title: String,
        text: String? = null
    ): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            fileProviderAuthority,
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!text.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, text)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, title)
    }
}
