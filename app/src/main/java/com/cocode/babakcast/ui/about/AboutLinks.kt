package com.cocode.babakcast.ui.about

enum class AboutLink { Update, Website, Privacy, Source, Issues, MadeBy }

/**
 * True once `apps.yml` in cocode-apps lists BabakCast as live on F-Droid. Until then the update
 * button opens the latest GitHub release. The app never checks for updates over the network: the
 * button only opens a page in the browser.
 */
const val FDROID_LIVE = false

private const val APPLICATION_ID = "com.cocode.babakcast"
private const val REPO = "https://github.com/cocodedk/BabakCast"
private const val SITE = "https://cast.cocode.dk"

/**
 * The languages that have their own website pages, home and privacy policy both
 * (`website/<code>/` and `website/<code>/privacy/`). Persian has a home page but no privacy
 * page of its own, so a Persian reader gets the English pages.
 */
private val SITE_LANGUAGES = setOf("da")

/** A site page in the app's language, or the English page when the site has none in that language. */
private fun sitePage(language: String, path: String): String = when {
    language in SITE_LANGUAGES -> "$SITE/$language/$path"
    path.isEmpty() -> SITE
    else -> "$SITE/$path"
}

/**
 * Where each About-page link goes. The targets come from `apps.yml`. The website and privacy
 * links follow [language] (a code such as "da", from the app's current locale); the others do
 * not change.
 */
fun aboutUrl(link: AboutLink, language: String, fdroidLive: Boolean = FDROID_LIVE): String = when (link) {
    AboutLink.Update ->
        if (fdroidLive) "https://f-droid.org/packages/$APPLICATION_ID/" else "$REPO/releases/latest"
    AboutLink.Website -> sitePage(language, "")
    AboutLink.Privacy -> sitePage(language, "privacy/")
    AboutLink.Source -> REPO
    AboutLink.Issues -> "$REPO/issues"
    AboutLink.MadeBy -> "https://cocode.dk"
}

fun versionLine(name: String, code: Int): String = "$name ($code)"
