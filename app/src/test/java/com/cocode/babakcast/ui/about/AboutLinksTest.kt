package com.cocode.babakcast.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AboutLinksTest {
    @Test
    fun updateOpensTheLatestGitHubReleaseUntilTheAppIsOnFDroid() {
        assertEquals(
            "https://github.com/cocodedk/BabakCast/releases/latest",
            aboutUrl(AboutLink.Update, "en", fdroidLive = false)
        )
    }

    @Test
    fun updateOpensTheFDroidPageOnceTheAppIsLiveThere() {
        assertEquals(
            "https://f-droid.org/packages/com.cocode.babakcast/",
            aboutUrl(AboutLink.Update, "en", fdroidLive = true)
        )
    }

    @Test
    fun theAppIsNotLiveOnFDroidYet() {
        // Flip FDROID_LIVE (and this test) when apps.yml says the app is live on F-Droid.
        assertFalse(FDROID_LIVE)
        assertEquals(aboutUrl(AboutLink.Update, "en", fdroidLive = false), aboutUrl(AboutLink.Update, "en"))
    }

    @Test
    fun privacyLinkIsThePolicyOnTheSite() {
        assertEquals("https://cast.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy, "en"))
    }

    @Test
    fun danishOpensTheDanishWebsiteAndPrivacyPolicy() {
        assertEquals("https://cast.cocode.dk/da/", aboutUrl(AboutLink.Website, "da"))
        assertEquals("https://cast.cocode.dk/da/privacy/", aboutUrl(AboutLink.Privacy, "da"))
    }

    @Test
    fun aLanguageTheSiteLacksOpensTheEnglishPages() {
        // Persian has a home page on the site but no privacy page of its own, so it falls back too.
        for (language in listOf("fa", "de", "")) {
            assertEquals("https://cast.cocode.dk", aboutUrl(AboutLink.Website, language))
            assertEquals("https://cast.cocode.dk/privacy/", aboutUrl(AboutLink.Privacy, language))
        }
    }

    @Test
    fun theLanguageDoesNotChangeTheOtherLinks() {
        for (link in listOf(AboutLink.Update, AboutLink.Source, AboutLink.Issues, AboutLink.MadeBy)) {
            assertEquals(aboutUrl(link, "en"), aboutUrl(link, "da"))
        }
    }

    @Test
    fun websiteSourceAndIssuesPointAtTheSiteAndTheRepository() {
        assertEquals("https://cast.cocode.dk", aboutUrl(AboutLink.Website, "en"))
        assertEquals("https://github.com/cocodedk/BabakCast", aboutUrl(AboutLink.Source, "en"))
        assertEquals("https://github.com/cocodedk/BabakCast/issues", aboutUrl(AboutLink.Issues, "en"))
    }

    @Test
    fun madeByOpensCocodeDk() {
        assertEquals("https://cocode.dk", aboutUrl(AboutLink.MadeBy, "en"))
    }

    @Test
    fun everyLinkIsAnHttpsAddress() {
        for (language in listOf("en", "da")) {
            for (fdroidLive in listOf(false, true)) {
                AboutLink.entries.forEach {
                    assertEquals(true, aboutUrl(it, language, fdroidLive).startsWith("https://"))
                }
            }
        }
    }

    @Test
    fun versionLineShowsNameAndCode() {
        assertEquals("1.0.89 (89)", versionLine("1.0.89", 89))
    }
}
