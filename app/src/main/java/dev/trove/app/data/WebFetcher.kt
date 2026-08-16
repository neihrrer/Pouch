package dev.trove.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Small exception hierarchy for URL fetching. */
sealed class FetchException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Network(message: String, cause: Throwable? = null) : FetchException(message, cause)
    class Timeout(message: String = "The request timed out", cause: Throwable? = null) :
        FetchException(message, cause)
    class HttpError(val code: Int, message: String) : FetchException(message)
    class TooLarge(message: String = "Page is too large to store") : FetchException(message)
    class NotHtml(message: String = "This link does not point to a web page") : FetchException(message)
    class Empty(message: String = "This page has no readable content") : FetchException(message)
}

class WebFetcher(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun fetch(url: String): Document = fetch(url, allowXml = false)

    /** Fetches an RSS/Atom feed (application/rss+xml etc.). */
    suspend fun fetchXml(url: String): Document = fetch(url, allowXml = true)

    /** Raw bytes + content type of a feed URL, for ROME's own parsing. */
    data class RawFetch(val bytes: ByteArray, val contentType: String?)

    suspend fun fetchRawXml(url: String): RawFetch = withContext(Dispatchers.IO) {
        val attempts = listOf(DesktopChrome, Googlebot)
        var lastError: FetchException = FetchException.Network("Unknown error")
        for (profile in attempts) {
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", profile.userAgent)
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                val response = try {
                    client.newCall(builder.build()).execute()
                } catch (e: java.net.SocketTimeoutException) {
                    throw FetchException.Timeout(cause = e)
                } catch (e: IOException) {
                    throw FetchException.Network(e.message ?: "Network error", e)
                }
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw FetchException.HttpError(resp.code, "Server answered ${resp.code}")
                    }
                    val body = resp.body ?: throw FetchException.Empty()
                    val bytes = body.byteStream().use { it.readNBytes(MAX_BODY_BYTES) }
                    if (bytes.size >= MAX_BODY_BYTES) throw FetchException.TooLarge()
                    if (bytes.isEmpty()) throw FetchException.Empty()
                    return@withContext RawFetch(bytes, resp.header("Content-Type"))
                }
            } catch (e: FetchException) {
                lastError = e
                val retryable = when (e) {
                    is FetchException.HttpError -> e.code == 403 || e.code == 429 || e.code == 451
                    is FetchException.Empty, is FetchException.Timeout, is FetchException.Network -> true
                    else -> false
                }
                if (!retryable) break
            }
        }
        throw lastError
    }

    private suspend fun fetch(url: String, allowXml: Boolean): Document = withContext(Dispatchers.IO) {
        // Try with a desktop Chrome UA first; if the site blocks us, retry
        // with the Googlebot UA — many CDNs serve crawler-friendly HTML to it.
        val attempts = listOf(
            DesktopChrome,
            Googlebot,
        )
        var lastError: FetchException = FetchException.Network("Unknown error")
        for (profile in attempts) {
            try {
                return@withContext fetchOnce(url, profile, allowXml)
            } catch (e: FetchException) {
                lastError = e
                val retryable = when (e) {
                    is FetchException.HttpError -> e.code == 403 || e.code == 429 || e.code == 451
                    is FetchException.Empty, is FetchException.Timeout, is FetchException.Network -> true
                    else -> false
                }
                if (!retryable) break
            } catch (e: IOException) {
                lastError = FetchException.Network(e.message ?: "Network error", e)
                break
            }
        }
        throw lastError
    }

    private fun fetchOnce(url: String, profile: UserAgentProfile, allowXml: Boolean): Document {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", profile.userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            // NOTE: no Accept-Encoding here. OkHttp adds `Accept-Encoding: gzip`
        // itself and transparently decodes it; setting it manually disables
        // that decoding and yields compressed garbage.
            .header("Cache-Control", "no-cache")
        if (profile.secChUa != null) {
            builder.header("sec-ch-ua", profile.secChUa)
            builder.header("sec-ch-ua-mobile", profile.secChUaMobile)
            builder.header("sec-ch-ua-platform", profile.secChUaPlatform)
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: java.net.SocketTimeoutException) {
            throw FetchException.Timeout(cause = e)
        } catch (e: java.net.UnknownHostException) {
            throw FetchException.Network("Could not resolve the host", e)
        } catch (e: IOException) {
            throw FetchException.Network(e.message ?: "Network error", e)
        }

        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw FetchException.HttpError(resp.code, "Server answered ${resp.code}")
            }
            val contentType = resp.header("Content-Type") ?: ""
            val isHtml = contentType.startsWith("text/html") ||
                contentType.startsWith("application/xhtml+xml") ||
                contentType.isBlank() // some servers omit it; let Jsoup decide
            val isXml = contentType.startsWith("application/rss+xml") ||
                contentType.startsWith("application/atom+xml") ||
                contentType.startsWith("application/xml") ||
                contentType.startsWith("text/xml")
            if (!isHtml && !(allowXml && isXml)) throw FetchException.NotHtml()
            val body = resp.body ?: throw FetchException.Empty()
            val bytes = body.byteStream().use { it.readNBytes(MAX_BODY_BYTES) }
            if (bytes.size >= MAX_BODY_BYTES) throw FetchException.TooLarge()
            // Parse from bytes so Jsoup honors the charset declared by the
            // page (BOM / meta), not just UTF-8. Feeds must use the XML
            // parser — the HTML parser treats <link>/<meta> as void elements
            // and would swallow RSS link text.
            val doc = if (allowXml) {
                Jsoup.parse(java.io.ByteArrayInputStream(bytes), null, url, org.jsoup.parser.Parser.xmlParser())
            } else {
                Jsoup.parse(java.io.ByteArrayInputStream(bytes), null, url)
            }
            // Sanity check: if the body is effectively empty or looks like
            // compressed/binary junk (very low letter ratio), treat it as
            // empty so the caller can retry with another UA. (XML docs have
            // no <body> element, so read text from the document root.)
            val text = if (allowXml) doc.text() else doc.body().text()
            val letters = text.count { it.isLetter() }
            if (text.isBlank() || letters * 3 < text.length) throw FetchException.Empty()
            doc
        }
    }

    private class UserAgentProfile(
        val userAgent: String,
        val secChUa: String? = null,
        val secChUaMobile: String = "?0",
        val secChUaPlatform: String = "\"Windows\"",
    )

    companion object {
        private const val MAX_BODY_BYTES = 6 * 1024 * 1024

        /** Shared with the offline-image downloader. */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

        private val DesktopChrome = UserAgentProfile(
            userAgent = USER_AGENT,
            secChUa = "\"Chromium\";v=\"138\", \"Google Chrome\";v=\"138\", \"Not=A?Brand\";v=\"99\"",
        )

        private val Googlebot = UserAgentProfile(
            userAgent = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
