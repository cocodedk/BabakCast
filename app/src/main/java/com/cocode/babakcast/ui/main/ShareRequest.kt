package com.cocode.babakcast.ui.main

import java.io.File

sealed class ShareRequest {
    data class AudioTwoStep(
        val caption: String,
        val files: List<File>,
        val mimeType: String,
        val title: String
    ) : ShareRequest()
}
