package dev.trove.app.data

import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.MediaModule
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import dev.trove.app.data.db.AppDatabase
import dev.trove.app.data.db.ArticleEntity
import dev.trove.app.data.db.FeedCategoryEntity
import dev.trove.app.data.db.FeedEntity
import dev.trove.app.data.db.FeedWithCount
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

private val enclosureRegex = Regex("""<enclosure\s+url="([^"]+)"\s+type=".*"\s*/>""")
private val imgRegex = Regex("""img.*?src=(["'])((?!data).*?)\1""", RegexOption.DOT_MATCHES_ALL)

/**
 * RSS reader modeled after Read You's approach:
 * - ROME (SyndFeedInput + XmlReader) parses RSS 0.9x/1.0/2.0, Atom and RDF
 *   with proper charset detection (BOM / meta / http header).
 * - When a subscription URL is a website rather than a feed, the feed is
 *   auto-discovered via `<link rel="alternate">`.
 * - Item thumbnails come from enclosures, Media RSS metadata, then a
 *   regex fallback.
 */
class FeedRepository(
    private val db: AppDatabase,
    private val fetcher: WebFetcher,
    private val extractor: ReaderExtractor,
) {
    private val feeds = db.feedDao()
    private val categories = db.feedCategoryDao()
    private val articles = db.articleDao()

    fun observeFeeds(): Flow<List<FeedWithCount>> = feeds.observeAllWithCount()
    fun observeCategories(): Flow<List<FeedCategoryEntity>> = categories.observeAll()

    // ------------------------------------------------------------ managing

    /**
     * Validates a feed URL (or discovers the feed from a site URL) and
     * subscribes. Returns the created feed with the resolved feed URL.
     */
    suspend fun addFeed(rawUrl: String, categoryId: Long?): Result<FeedEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val url = normalizeUrl(rawUrl)
            feeds.getByUrl(url)?.let { return@runCatching it }

            val (syndFeed, resolvedUrl) = searchFeed(url)
            val host = URI(resolvedUrl).host.orEmpty().removePrefix("www.")
            val title = syndFeed.title?.trim()?.ifEmpty { host } ?: host
            val entity = FeedEntity(
                url = resolvedUrl,
                title = title,
                siteUrl = syndFeed.link?.trim()?.ifEmpty { null } ?: URI(resolvedUrl).let { "${it.scheme}://${it.host}" },
                categoryId = categoryId,
            )
            feeds.insert(entity)
            entity
        }
    }

    /** Direct feed parse; if that fails, discover the feed from the page HTML. */
    private suspend fun searchFeed(url: String): Pair<SyndFeed, String> {
        val direct = parseFeed(fetcher.fetchRawXml(url))
        if (direct != null) return direct to url

        // Website URL — discover the feed link, Read You style
        val html = runCatching {
            String(fetcher.fetchRawXml(url).bytes, Charsets.UTF_8)
        }.getOrElse { throw FetchException.Empty("Unable to detect RSS feed URL") }
        val doc = Jsoup.parse(html, url)
        val links = doc.select("head link[rel~=(?i)alternate][href]")
        val preferred = links.firstOrNull {
            val type = it.attr("type").lowercase()
            type == "application/rss+xml" || type == "application/atom+xml" || type == "application/rdf+xml"
        } ?: links.firstOrNull()
        val feedUrl = preferred?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: throw FetchException.Empty("Unable to detect RSS feed URL")

        val feed = parseFeed(fetcher.fetchRawXml(feedUrl))
            ?: throw FetchException.NotHtml("Not a valid feed")
        return feed to feedUrl
    }

    private fun parseFeed(raw: WebFetcher.RawFetch): SyndFeed? = runCatching {
        val contentType = raw.contentType
            ?.let { if (it.contains("charset=", ignoreCase = true)) it else "$it; charset=UTF-8" }
            ?: "text/xml; charset=UTF-8"
        ByteArrayInputStream(raw.bytes).use { input ->
            SyndFeedInput().build(XmlReader(input, contentType))
        }
    }.getOrNull()

    /**
     * Imports all items of a feed as unsaved articles (deduped by guid).
     * Content comes straight from the feed; returns the number of new items.
     */
    suspend fun fetchFeed(feed: FeedEntity): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val synd = parseFeed(fetcher.fetchRawXml(feed.url))
                ?: throw IOException("Not a valid feed")
            var added = 0
            for (entry in synd.entries) {
                val link = entry.link?.trim() ?: continue
                val guid = entry.uri?.takeIf { it.isNotBlank() } ?: link
                if (articles.getByFeedItemId(guid) != null) continue

                val content = entry.contents.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.value }
                    ?: entry.description?.value
                    ?: continue
                val (cleanHtml, cleanText) = extractor.cleanArticleHtml(content, link)
                if (cleanText.length < 40) continue

                val date = (entry.publishedDate ?: entry.updatedDate)?.time ?: System.currentTimeMillis()
                articles.insert(
                    ArticleEntity(
                        url = link,
                        title = decodeHtml(entry.title?.trim() ?: feed.title),
                        siteName = feed.title,
                        excerpt = cleanText.take(260).trimEnd() + "…",
                        contentHtml = cleanHtml,
                        contentText = cleanText,
                        leadImageUrl = thumbnail(entry, content),
                        feedId = feed.id,
                        feedItemId = guid,
                        addedAt = date,
                        fetchedAt = System.currentTimeMillis(),
                        saved = false,
                    )
                )
                added++
            }
            feeds.update(feed.copy(lastFetchedAt = System.currentTimeMillis(), fetchFailed = false))
            added
        }.onFailure {
            feeds.update(feed.copy(lastFetchedAt = System.currentTimeMillis(), fetchFailed = true))
        }
    }

    suspend fun fetchAllFeeds(): Int {
        var total = 0
        for (feed in feeds.getAll()) {
            total += fetchFeed(feed).getOrDefault(0)
        }
        return total
    }

    suspend fun deleteFeed(feed: FeedEntity) {
        articles.unlinkFeed(feed.id)
        feeds.delete(feed)
    }

    suspend fun renameFeed(feed: FeedEntity, title: String) {
        feeds.update(feed.copy(title = title.trim()))
    }

    suspend fun moveFeed(feed: FeedEntity, categoryId: Long?) {
        feeds.update(feed.copy(categoryId = categoryId))
    }

    suspend fun addCategory(name: String): Long =
        categories.getByName(name.trim())?.id ?: categories.insert(FeedCategoryEntity(name = name.trim()))

    suspend fun deleteCategory(category: FeedCategoryEntity) {
        categories.clearFeedCategory(category.id)
        categories.delete(category)
    }

    // ------------------------------------------------------------- helpers

    /** Thumbnail resolution: enclosure → Media RSS metadata → regex. */
    private fun thumbnail(entry: SyndEntry, content: String?): String? {
        entry.enclosures?.firstOrNull()?.url?.takeIf { it.isNotBlank() }?.let { return it }

        val media = entry.getModule(MediaModule.URI) as? MediaEntryModule
        if (media != null) {
            val candidates = buildList {
                add(media.metadata)
                addAll(media.mediaGroups.map { it.metadata })
                addAll(media.mediaContents.map { it.metadata })
            }.flatMap { it.thumbnail.toList() }
            candidates.firstOrNull()?.url?.toString()?.let { return it }
            (media.mediaContents.firstOrNull { it.medium == "image" }?.reference as? UrlReference)
                ?.url?.toString()?.let { return it }
        }

        if (content != null) {
            enclosureRegex.find(content)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
            imgRegex.find(content)?.groupValues?.get(2)?.takeIf { !it.startsWith("data:") }?.let { return it }
        }
        return null
    }

    private fun decodeHtml(text: String): String =
        runCatching { Jsoup.parse(text).text() }.getOrDefault(text)

    private fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (!url.contains("://")) url = "https://$url"
        require(URI(url).host != null) { "invalid url" }
        return url
    }

    // ------------------------------------------------------------------ OPML

    fun exportOpml(
        feeds: List<FeedWithCount>,
        categories: List<FeedCategoryEntity>,
    ): String {
        val byCategory = feeds.groupBy { it.feed.categoryId }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<opml version="2.0">""").append('\n')
        sb.append("  <head><title>Pouch subscriptions</title></head>").append('\n')
        sb.append("  <body>").append('\n')
        for (cat in categories) {
            val catFeeds = byCategory[cat.id].orEmpty()
            if (catFeeds.isEmpty()) continue
            sb.append("    <outline text=\"${esc(cat.name)}\" title=\"${esc(cat.name)}\">").append('\n')
            catFeeds.forEach { sb.append(feedOutline(it, 2)) }
            sb.append("    </outline>").append('\n')
        }
        byCategory[null].orEmpty().forEach { sb.append(feedOutline(it, 1)) }
        sb.append("  </body>").append('\n')
        sb.append("</opml>").append('\n')
        return sb.toString()
    }

    private fun feedOutline(feed: FeedWithCount, indent: Int): String {
        val pad = "    ".repeat(indent)
        return "$pad<outline type=\"rss\" text=\"${esc(feed.feed.title)}\" title=\"${esc(feed.feed.title)}\" " +
            "xmlUrl=\"${esc(feed.feed.url)}\"${feed.feed.siteUrl?.let { " htmlUrl=\"${esc(it)}\"" } ?: ""}/>\n"
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")

    /** Imports OPML. Returns the number of feeds added. */
    suspend fun importOpml(content: String): Int = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(content, "", Parser.xmlParser())
        var added = 0
        for (outline in doc.select("outline[xmlUrl]")) {
            val url = outline.attr("xmlUrl").trim()
            if (url.isEmpty()) continue
            val parent = outline.parent()
            val categoryId = if (parent != null && parent.tagName() == "outline") {
                val catName = parent.attr("title").ifBlank { parent.attr("text") }
                if (catName.isNotBlank()) categories.getByName(catName)?.id
                    ?: categories.insert(FeedCategoryEntity(name = catName))
                else null
            } else null
            if (addFeed(url, categoryId).isSuccess) added++
        }
        added
    }
}
