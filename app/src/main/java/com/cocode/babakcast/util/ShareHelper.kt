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

        val uris = filesToShare.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (videoInfo.title.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, videoInfo.title)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Share text content
     */
    fun shareText(text: String, title: String = "Share") {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        val chooser = Intent.createChooser(shareIntent, title).apply {
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
        if (files.isEmpty()) {
            Log.w(tag, "No files to share")
            return
        }
        if (files.size == 1) {
            shareFile(files.first(), mimeType, title, text)
            return
        }

        val uris = files.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
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

        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
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
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
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

        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
