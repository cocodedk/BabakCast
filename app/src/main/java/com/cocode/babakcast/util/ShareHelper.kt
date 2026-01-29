package com.cocode.babakcast.util

import android.content.Context
import android.content.Intent
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share video"))
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

        context.startActivity(Intent.createChooser(shareIntent, title))
    }

    /**
     * Share single file
     */
    fun shareFile(file: File, mimeType: String = "application/octet-stream") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share file"))
    }
}
