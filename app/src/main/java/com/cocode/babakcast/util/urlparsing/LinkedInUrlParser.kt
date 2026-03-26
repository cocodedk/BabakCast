package com.cocode.babakcast.util.urlparsing

object LinkedInUrlParser {
    // Matches the numeric suffix (10+ digits) of a /posts/ slug
    private val postIdPattern = Regex(
        "linkedin\\.com/posts/[A-Za-z0-9_-]+-([0-9]{10,})"
    )
    // Matches urn:li:activity:12345 inside /feed/update/
    private val activityPattern = Regex(
        "linkedin\\.com/feed/update/urn:li:activity:([0-9]+)"
    )

    /**
     * Returns a stable numeric ID suitable for use as a filename component.
     * Returns null for unrecognised URL patterns (profile pages, short links, etc.)
     * so that [MediaRepository.identifyMedia] can fail cleanly.
     */
    fun extractPostId(url: String): String? {
        postIdPattern.find(url)?.groupValues?.get(1)?.let { return it }
        activityPattern.find(url)?.groupValues?.get(1)?.let { return it }
        return null
    }
}
