package com.cocode.babakcast.util

enum class Platform { YOUTUBE, X, INSTAGRAM, LINKEDIN }

data class ExtractedUrl(val url: String, val platform: Platform)
