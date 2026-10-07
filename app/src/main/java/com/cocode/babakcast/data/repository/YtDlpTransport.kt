package com.cocode.babakcast.data.repository

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * Which addresses the yt-dlp update may talk to. It downloads a program the phone then runs, so
 * every address must be HTTPS and on GitHub: the API, the release page, and the two hosts GitHub
 * serves release files from (a download on github.com answers with a redirect to one of them).
 * Checked before each request, so nothing is ever sent to an address that is not on the list.
 */
internal class UrlPolicy(private val allows: (HttpUrl) -> Boolean) {

    fun require(url: HttpUrl) {
        if (!allows(url)) throw IOException("The update may not use ${url.scheme}://${url.host}")
    }

    companion object {
        private val HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )

        val GITHUB = UrlPolicy { url ->
            url.isHttps && url.port == 443 && url.host in HOSTS &&
                // On github.com only yt-dlp's own releases.
                (url.host != "github.com" || url.encodedPath.startsWith("/yt-dlp/"))
        }
    }
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
