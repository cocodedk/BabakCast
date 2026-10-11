package com.cocode.babakcast.data.repository

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/** Whether an address is acceptable. [require] throws when it is not. */
internal class UrlPolicy(private val allows: (HttpUrl) -> Boolean) {

    fun require(url: HttpUrl) {
        if (!allows(url)) throw IOException("The update may not use ${url.scheme}://${url.host}${url.encodedPath}")
    }
}

/**
 * Where the yt-dlp update may go. It downloads a program the phone then runs, so every address
 * must be HTTPS on GitHub and, for the files, inside one release of one repository:
 *  - [api] covers the request for the latest release;
 *  - [release] covers the requests for that release's files, once its tag is known: on github.com
 *    only /yt-dlp/<repo>/releases/download/<tag>/yt-dlp and .../SHA2-256SUMS, matched segment by
 *    segment, so a redirect to another release or repository is refused before it is followed;
 *    a download on github.com redirects to release-assets.githubusercontent.com (or
 *    objects.githubusercontent.com), which is allowed too;
 *  - [assetUrl] is the one address each file of a release must have.
 * No path may hide a separator or a dot in percent-encoding, or hold a backslash. That is checked
 * twice: on the parsed address here, and on the text as it arrived ([isCleanAddress]), because
 * parsing already folds `%2E%2E` and `\` away.
 */
internal class ReleaseSource(
    val apiUrl: String,
    val api: UrlPolicy,
    val release: (tag: String) -> UrlPolicy,
    val assetUrl: (tag: String, name: String) -> String
) {
    companion object {
        private val ASSET_HOSTS = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")

        private fun clean(url: HttpUrl) =
            url.isHttps && url.port == 443 && !url.encodedPath.contains('\\') &&
                !HIDDEN_SEPARATOR.containsMatchIn(url.encodedPath)

        /** The latest release of github.com/yt-dlp/[repo]. */
        fun github(repo: String) = ReleaseSource(
            apiUrl = "https://api.github.com/repos/yt-dlp/$repo/releases/latest",
            api = UrlPolicy { clean(it) && it.host == "api.github.com" },
            release = { tag ->
                val files = listOf(YtDlpInstaller.FILE, YtDlpInstaller.SUMS)
                    .map { listOf("yt-dlp", repo, "releases", "download", tag, it) }
                UrlPolicy { url ->
                    clean(url) && (url.host in ASSET_HOSTS || (url.host == "github.com" && url.encodedPathSegments in files))
                }
            },
            assetUrl = { tag, name -> "https://github.com/yt-dlp/$repo/releases/download/$tag/$name" }
        )
    }
}

private val HIDDEN_SEPARATOR = Regex("(?i)%(2f|5c|2e)")
private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/**
 * The path of [raw], an address exactly as it was written, found without parsing it: a scheme only
 * at the very start (`https:`), then, when `//` follows (an absolute address, or a scheme-relative
 * one that starts with `//`), an authority up to the first `/`; anything else is already a path.
 * The path ends at the first `?` or `#`. A `://` further along is part of the path, not a scheme.
 */
internal fun rawPath(raw: String): String {
    val beforeQuery = raw.substringBefore('#').substringBefore('?')
    val afterScheme = SCHEME.find(beforeQuery)?.let { beforeQuery.substring(it.value.length) } ?: beforeQuery
    if (!afterScheme.startsWith("//")) return afterScheme
    val authorityAndPath = afterScheme.substring(2)
    return authorityAndPath.substring(authorityAndPath.indexOf('/').takeIf { it >= 0 } ?: authorityAndPath.length)
}

/**
 * Whether [raw], an address exactly as it was written (a Location header, an address from a
 * listing), hides anything in its path. OkHttp's parser resolves `%2E`, `%2e` and `..` dot segments
 * and turns a backslash into a slash, so an address checked only after parsing can look
 * harmless and still lead somewhere else. A path holds no backslash, `%2F`, `%5C` or `%2E` in
 * any case. The query is left alone: GitHub's signed download links have percent codes in it.
 */
internal fun isCleanAddress(raw: String): Boolean {
    if (raw.substringBefore('#').substringBefore('?').contains('\\')) return false
    return !HIDDEN_SEPARATOR.containsMatchIn(rawPath(raw))
}

/**
 * GET requests for the yt-dlp update. Redirects are followed here, not by OkHttp, so each
 * address is checked against [policy] before it is requested. [get] has one deadline for the
 * whole chain of requests and reads the answer through [read], which must bound what it reads.
 */
internal class YtDlpTransport(client: OkHttpClient, private val policy: UrlPolicy) {

    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    private sealed interface Step<out T> {
        class Done<T>(val value: T) : Step<T>
        class Redirect(val url: HttpUrl) : Step<Nothing>
    }

    fun <T> get(url: String, timeoutMs: Long, read: (Response) -> T): T {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        if (!isCleanAddress(url)) throw IOException("The update may not use $url")
        var current = url.toHttpUrlOrNull() ?: throw IOException("Not an address: $url")
        for (hop in 0..MAX_REDIRECTS) {
            policy.require(current)
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw IOException("Out of time")
            val request = Request.Builder().url(current).header("Accept", "application/vnd.github+json").build()
            val bounded = client.newBuilder().callTimeout(remaining, TimeUnit.NANOSECONDS).build()
            val step: Step<T> = bounded.newCall(request).execute().use { response ->
                when {
                    response.isRedirect -> {
                        val location = response.header("Location") ?: throw IOException("A redirect with no address")
                        if (!isCleanAddress(location)) throw IOException("A redirect to an address that hides a dot or a separator")
                        Step.Redirect(current.resolve(location) ?: throw IOException("A redirect to a bad address"))
                    }
                    !response.isSuccessful -> throw IOException("GitHub answered ${response.code}")
                    else -> Step.Done(read(response))
                }
            }
            when (step) {
                is Step.Done -> return step.value
                is Step.Redirect -> current = step.url
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
    }
}

/** The answer as text, or an exception when it is longer than [maxBytes]. */
internal fun Response.readLimited(maxBytes: Long): String {
    val source = body.source()
    val buffer = Buffer()
    var total = 0L
    while (true) {
        val n = source.read(buffer, READ_CHUNK)
        if (n < 0) return buffer.readUtf8()
        total += n
        if (total > maxBytes) throw IOException("The answer is larger than $maxBytes bytes")
    }
}

private const val READ_CHUNK = 8L * 1024
