package com.cocode.babakcast.util.urlparsing

import com.cocode.babakcast.util.ExtractedUrl
import com.cocode.babakcast.util.Platform

object MediaUrlExtractor {
    fun extractFromText(text: String?): ExtractedUrl? {
        // YouTube checked first: if text contains both a YouTube and an X URL, YouTube wins.
        val youtubeUrl = YouTubeUrlExtractor.extractYouTubeUrlFromText(text)
        if (youtubeUrl != null) return ExtractedUrl(youtubeUrl, Platform.YOUTUBE)

        val xUrl = XUrlExtractor.extractXUrlFromText(text)
        if (xUrl != null) return ExtractedUrl(xUrl, Platform.X)

        val instagramUrl = InstagramUrlExtractor.extractInstagramUrlFromText(text)
        if (instagramUrl != null) return ExtractedUrl(instagramUrl, Platform.INSTAGRAM)

        val linkedInUrl = LinkedInUrlExtractor.extractLinkedInUrlFromText(text)
        if (linkedInUrl != null) return ExtractedUrl(linkedInUrl, Platform.LINKEDIN)

        return null
    }
}
