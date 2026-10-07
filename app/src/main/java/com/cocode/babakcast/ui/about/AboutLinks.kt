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

/** Where each About-page link goes. The targets come from `apps.yml`. */
fun aboutUrl(link: AboutLink, fdroidLive: Boolean = FDROID_LIVE): String = when (link) {
    AboutLink.Update ->
        if (fdroidLive) "https://f-droid.org/packages/$APPLICATION_ID/" else "$REPO/releases/latest"
    AboutLink.Website -> "https://cast.cocode.dk"
    AboutLink.Privacy -> "https://cast.cocode.dk/privacy/"
    AboutLink.Source -> REPO
    AboutLink.Issues -> "$REPO/issues"
    AboutLink.MadeBy -> "https://cocode.dk"
}

fun versionLine(name: String, code: Int): String = "$name ($code)"
