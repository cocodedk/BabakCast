package com.cocode.babakcast.data.model

/**
 * Models for X/Twitter tweet media returned by the syndication API.
 */
data class TweetMediaResult(
    val text: String,
    val media: List<TweetMedia>
)

sealed class TweetMedia {
    data class Photo(val url: String, val originalUrl: String) : TweetMedia()
    data class Video(val url: String?, val thumbnailUrl: String?) : TweetMedia()
    data class AnimatedGif(val url: String?, val thumbnailUrl: String?) : TweetMedia()
}
