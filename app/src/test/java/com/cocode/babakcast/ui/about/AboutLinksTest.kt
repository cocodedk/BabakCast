package com.cocode.babakcast.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AboutLinksTest {
    @Test
    fun updateOpensTheLatestGitHubReleaseUntilTheAppIsOnFDroid() {
        assertEquals(
            "https://github.com/cocodedk/BabakCast/releases/latest",
            aboutUrl(AboutLink.Update, fdroidLive = false)
        )
    }

    @Test
    fun updateOpensTheFDroidPageOnceTheAppIsLiveThere() {
        assertEquals(
            "https://f-droid.org/packages/com.cocode.babakcast/",
            aboutUrl(AboutLink.Update, fdroidLive = true)
        )
    }

    @Test
    fun theAppIsNotLiveOnFDroidYet() {
        // Flip FDROID_LIVE (and this test) when apps.yml says the app is live on F-Droid.
        assertFalse(FDROID_LIVE)
        assertEquals(aboutUrl(AboutLink.Update, fdroidLive = false), aboutUrl(AboutLink.Update))
    }

    @Test
    fun privacyLinkIsThePolicyOnTheSite() {
        assertEquals("https://cast.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy))
    }

    @Test
    fun websiteSourceAndIssuesPointAtTheSiteAndTheRepository() {
        assertEquals("https://cast.cocode.dk", aboutUrl(AboutLink.Website))
        assertEquals("https://github.com/cocodedk/BabakCast", aboutUrl(AboutLink.Source))
        assertEquals("https://github.com/cocodedk/BabakCast/issues", aboutUrl(AboutLink.Issues))
    }

    @Test
    fun madeByOpensCocodeDk() {
        assertEquals("https://cocode.dk", aboutUrl(AboutLink.MadeBy))
    }

    @Test
    fun everyLinkIsAnHttpsAddress() {
        for (fdroidLive in listOf(false, true)) {
            AboutLink.entries.forEach { assertEquals(true, aboutUrl(it, fdroidLive).startsWith("https://")) }
        }
    }

    @Test
    fun versionLineShowsNameAndCode() {
        assertEquals("1.0.89 (89)", versionLine("1.0.89", 89))
    }
}
