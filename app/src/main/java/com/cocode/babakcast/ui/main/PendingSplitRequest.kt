package com.cocode.babakcast.ui.main

import com.cocode.babakcast.data.model.VideoInfo
import java.io.File

internal sealed class PendingSplitRequest {
    data class Video(val videoInfo: VideoInfo) : PendingSplitRequest()
    data class Audio(
        val videoInfo: VideoInfo,
        val videoFile: File,
        val audioFile: File
    ) : PendingSplitRequest()
}
