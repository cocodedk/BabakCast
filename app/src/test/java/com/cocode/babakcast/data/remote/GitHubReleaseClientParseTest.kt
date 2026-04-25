package com.cocode.babakcast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseClientParseTest {

    @Test
    fun parses_realShapePayload_andPicksApkAsset() {
        val json = """
            {
                "tag_name": "v1.0.39",
                "name": "Release 1.0.39",
                "assets": [
                    {
                        "name": "BabakCast.apk",
                        "size": 269495206,
                        "browser_download_url": "https://github.com/cocodedk/BabakCast/releases/download/v1.0.39/BabakCast.apk"
                    }
                ]
            }
        """.trimIndent()

        val release = GitHubReleaseClient.parseLatestResponse(json)

        assertEquals("v1.0.39", release?.tagName)
        assertEquals(
            "https://github.com/cocodedk/BabakCast/releases/download/v1.0.39/BabakCast.apk",
            release?.apkDownloadUrl
        )
        assertEquals(269495206L, release?.apkSizeBytes)
    }

    @Test
    fun ignoresNonApkAssets() {
        val json = """
            {
                "tag_name": "v1.0.40",
                "assets": [
                    { "name": "checksums.txt", "size": 64, "browser_download_url": "https://example.com/checksums.txt" },
                    { "name": "BabakCast.apk", "size": 100, "browser_download_url": "https://example.com/BabakCast.apk" },
                    { "name": "release-notes.md", "size": 1024, "browser_download_url": "https://example.com/notes.md" }
                ]
            }
        """.trimIndent()

        val release = GitHubReleaseClient.parseLatestResponse(json)

        assertEquals("https://example.com/BabakCast.apk", release?.apkDownloadUrl)
        assertEquals(100L, release?.apkSizeBytes)
    }

    @Test
    fun returnsNull_whenNoApkAsset() {
        val json = """
            {
                "tag_name": "v1.0.40",
                "assets": [
                    { "name": "release-notes.md", "size": 1024, "browser_download_url": "https://example.com/notes.md" }
                ]
            }
        """.trimIndent()

        assertNull(GitHubReleaseClient.parseLatestResponse(json))
    }

    @Test
    fun returnsNull_whenAssetsMissing() {
        assertNull(GitHubReleaseClient.parseLatestResponse("""{ "tag_name": "v1.0.40" }"""))
    }

    @Test
    fun returnsNull_onMalformedJson() {
        assertNull(GitHubReleaseClient.parseLatestResponse("not json"))
        assertNull(GitHubReleaseClient.parseLatestResponse(""))
    }
}
