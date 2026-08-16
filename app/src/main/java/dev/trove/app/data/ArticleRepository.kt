package dev.trove.app.data

import dev.trove.app.data.db.AppDatabase
import dev.trove.app.data.db.ArticleEntity
import dev.trove.app.data.db.ArticleTagCrossRef
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.data.db.FolderEntity
import dev.trove.app.data.db.HighlightEntity
import dev.trove.app.data.db.FolderWithCount
import dev.trove.app.data.OfflineRetention
import dev.trove.app.data.db.TagEntity
import dev.trove.app.data.db.TagWithCount
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface AddUrlResult {
    data class Added(val articleId: Long) : AddUrlResult
    data class AlreadySaved(val articleId: Long) : AddUrlResult
    data class Failed(val error: String) : AddUrlResult
}

class ArticleRepository(
    private val db: AppDatabase,
    private val fetcher: WebFetcher,
    private val extractor: ReaderExtractor,
    private val context: android.content.Context,
) {
    private val articles = db.articleDao()
    private val folders = db.folderDao()
    private val tags = db.tagDao()

    // ------------------------------------------------------------ observing

    fun observeInbox(): Flow<List<ArticleWithTags>> = articles.observeAll()
    fun observeFolder(folderId: Long): Flow<List<ArticleWithTags>> = articles.observeByFolder(folderId)
    fun observeByFeed(feedId: Long): Flow<List<ArticleWithTags>> = articles.observeByFeed(feedId)
    fun observeTag(tagId: Long): Flow<List<ArticleWithTags>> = articles.observeByTag(tagId)
    fun search(query: String): Flow<List<ArticleWithTags>> = articles.search(ftsQuery(query))
    fun observeArticle(id: Long): Flow<ArticleWithTags?> = articles.observeById(id)
    fun observeFolders(): Flow<List<FolderWithCount>> = folders.observeAllWithCount()
    fun observeTags(): Flow<List<TagWithCount>> = tags.observeAllWithCount()
    fun observeUnreadCount(): Flow<Int> = articles.observeUnreadCount()

    // ------------------------------------------------------------- adding

    /** Fetches the URL, extracts the article and stores it. */
    suspend fun addUrl(rawUrl: String): AddUrlResult = withContext(Dispatchers.IO) {
        val url = try {
            normalizeUrl(rawUrl)
        } catch (e: Exception) {
            return@withContext AddUrlResult.Failed("That doesn't look like a valid link")
        }
        val existing = articles.getByUrl(url)
        if (existing != null) return@withContext AddUrlResult.AlreadySaved(existing.id)

        try {
            val extracted = fetchExtract(url)
            val id = articles.insert(
                ArticleEntity(
                    url = url,
                    title = extracted.title,
                    siteName = extracted.siteName,
                    byline = extracted.byline,
                    excerpt = extracted.excerpt,
                    // Store what we got even if thin — the reader offers a
                    // refresh, and the excerpt keeps the card useful.
                    contentHtml = extracted.contentHtml.takeIf { extracted.contentText.length >= 40 },
                    contentText = extracted.contentText.takeIf { extracted.contentText.length >= 40 },
                    leadImageUrl = extracted.leadImageUrl,
                    faviconUrl = extracted.faviconUrl,
                    publishedAt = extracted.publishedAt,
                    fetchedAt = System.currentTimeMillis(),
                )
            )
            AddUrlResult.Added(id)
        } catch (e: FetchException.HttpError) {
            val msg = when (e.code) {
                403, 451 -> "This site blocked automated access (${e.code}). Try opening it in a browser instead."
                404 -> "This link doesn't exist (404)."
                429 -> "This site is rate-limiting requests right now (429). Try again later."
                else -> e.message ?: "Couldn't fetch this page"
            }
            AddUrlResult.Failed(msg)
        } catch (e: FetchException) {
            AddUrlResult.Failed(e.message ?: "Couldn't fetch this page")
        } catch (e: Exception) {
            AddUrlResult.Failed("Something went wrong: ${e.message?.take(80)}")
        }
    }

    /** Re-fetches an existing article's content. */
    suspend fun refreshContent(article: ArticleEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val extracted = fetchExtract(article.url)
            articles.setFullContent(article.id)
            articles.update(
                article.copy(
                    title = extracted.title,
                    siteName = extracted.siteName,
                    byline = extracted.byline,
                    excerpt = extracted.excerpt,
                    contentHtml = extracted.contentHtml,
                    contentText = extracted.contentText,
                    leadImageUrl = extracted.leadImageUrl,
                    faviconUrl = extracted.faviconUrl,
                    publishedAt = extracted.publishedAt,
                    fetchedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Fetch + extract, with an RSS/Atom feed fallback for JavaScript-only
     * pages (Substack homepages, SPA blogs, …): if the page yields almost no
     * text, we look for the article in the site's feed, which usually carries
     * the full content server-rendered.
     */
    private suspend fun fetchExtract(url: String): ExtractedArticle {
        val doc = fetcher.fetch(url)
        var extracted = extractor.extract(doc, url)
        // A real article page carries an <article>/<main> container; JS-shell
        // pages (Substack homepages, SPA blogs) don't, and yield only nav
        // junk. Fall back to the site's RSS feed whenever the page looks thin
        // OR has no article structure at all. The fallback must never fail
        // the save, so it's fully guarded.
        val hasArticleStructure = doc.selectFirst("article") != null ||
            doc.selectFirst("main") != null ||
            doc.selectFirst("[itemprop=articleBody]") != null
        val looksThin = extracted.contentText.length < 4000 || !hasArticleStructure
        if (looksThin) {
            val rss = runCatching {
                tryRssFallback(url, preferLatestPost = !hasArticleStructure)
            }.getOrNull()
            if (rss != null && rss.contentText.length > extracted.contentText.length) {
                extracted = rss
            }
        }
        return extracted
    }

    /**
     * @param preferLatestPost when the saved page is a shell (e.g. a Substack
     * homepage), the feed contains post links that will never match the
     * shell URL — in that case fall back to the most recent post, which is
     * exactly what the shell displays.
     */
    private suspend fun tryRssFallback(
        articleUrl: String,
        preferLatestPost: Boolean = false,
    ): ExtractedArticle? {
        val host = runCatching { URI(articleUrl).host }.getOrNull() ?: return null
        val feeds = listOf(
            "https://$host/feed",
            "https://$host/rss",
            "https://$host/rss.xml",
            "https://$host/feed.xml",
            "https://$host/atom.xml",
            "https://$host/index.xml",
        )
        val target = normalizeForMatch(articleUrl)
        var feedFound = false
        for (feedUrl in feeds) {
            if (feedFound) break // one feed per site; don't probe the rest
            val doc = try {
                fetcher.fetchXml(feedUrl)
            } catch (e: Exception) {
                continue
            }
            feedFound = true
            val items = doc.select("item, entry")
            val item = items.firstOrNull { el ->
                val linkEl = el.selectFirst("link")
                val link = linkEl?.attr("href")?.takeIf { it.isNotBlank() }
                    ?: linkEl?.text()
                    ?: ""
                normalizeForMatch(link) == target
            } ?: if (preferLatestPost) items.firstOrNull() else null
            if (item == null) return null
            val raw = item.selectFirst("content\\:encoded, content")?.html()
                ?: item.selectFirst("description")?.html()
                ?: continue
            val (cleanHtml, cleanText) = extractor.cleanArticleHtml(raw, articleUrl)
            if (cleanText.length < 100) continue
            return ExtractedArticle(
                title = item.selectFirst("title")?.text()?.trim() ?: "",
                siteName = host.removePrefix("www."),
                byline = null,
                excerpt = cleanText.take(260).trimEnd() + "…",
                contentHtml = cleanHtml,
                contentText = cleanText,
                leadImageUrl = null,
                faviconUrl = null,
                publishedAt = null,
            )
        }
        return null
    }

    private fun normalizeForMatch(url: String): String =
        url.trim().trimEnd('/').substringBefore('#').substringBefore('?')

    // --------------------------------------------------------- article ops

    suspend fun setRead(id: Long, read: Boolean) = articles.setRead(id, read)

    suspend fun setFavorite(id: Long, fav: Boolean) = articles.setFavorite(id, fav)

    suspend fun setFolder(id: Long, folderId: Long?) = articles.setFolder(id, folderId)

    suspend fun savePosition(id: Long, progress: Float, scrollIndex: Int, scrollOffset: Int) {
        if (progress > 0f) articles.savePosition(id, progress, scrollIndex, scrollOffset)
    }

    suspend fun deleteArticle(article: ArticleEntity) {
        articles.deleteHighlightsForArticle(article.id)
        articles.delete(article)
    }

    /** Marks a feed item as added to the Library (re-dated to now). */
    suspend fun saveFeedItem(id: Long) = articles.saveFeedItem(id, System.currentTimeMillis())

    /** Library mutations (tags, folders, favorites) auto-add feed items. */
    suspend fun ensureSaved(articleId: Long) {
        articles.saveFeedItem(articleId, System.currentTimeMillis())
    }

    suspend fun setFullContent(id: Long) = articles.setFullContent(id)

    fun observeAllFeedItems() = articles.observeAllFeedItems()

    suspend fun cleanupFeedItems(retention: OfflineRetention): Int {
        if (retention == OfflineRetention.ALWAYS) return 0
        val cutoff = System.currentTimeMillis() - retention.millis
        val before = articles.getAll().count { !it.saved && it.feedId != null }
        articles.deleteOldFeedItems(cutoff)
        return (before - articles.getAll().count { !it.saved && it.feedId != null }).coerceAtLeast(0)
    }

    // ------------------------------------------------------------ highlights

    fun observeHighlights(articleId: Long): Flow<List<HighlightEntity>> =
        articles.observeHighlights(articleId)

    suspend fun addHighlight(
        articleId: Long,
        text: String,
        colorIndex: Int,
        blockIndex: Int,
        startOffset: Int,
        endOffset: Int,
    ) {
        articles.insertHighlight(
            HighlightEntity(
                articleId = articleId,
                text = text,
                colorIndex = colorIndex,
                blockIndex = blockIndex,
                startOffset = startOffset,
                endOffset = endOffset,
            )
        )
    }

    suspend fun getHighlights(articleId: Long): List<HighlightEntity> = articles.getHighlights(articleId)

    suspend fun removeHighlight(highlight: HighlightEntity) = articles.deleteHighlight(highlight)

    suspend fun getArticle(id: Long): ArticleEntity? = articles.getById(id)

    /** A random saved article id (app shortcut). */
    suspend fun getRandomArticleId(): Long? = articles.getRandomSavedId()

    /** Library articles, for bulk offline downloads (feed items excluded). */
    suspend fun getAllArticles(): List<ArticleEntity> = articles.getAllSaved()

    /**
     * Deletes offline copies older than the retention window (unless
     * retention is ALWAYS). Returns the number of copies removed.
     */
    suspend fun cleanupOfflineDownloads(retention: OfflineRetention): Int {
        if (retention == OfflineRetention.ALWAYS) return 0
        val cutoff = System.currentTimeMillis() - retention.millis
        val stale = articles.getAll().filter { it.offlineReady && it.offlineAt in 1 until cutoff }
        stale.forEach { a ->
            java.io.File(context.filesDir, "offline/${a.id}").deleteRecursively()
            articles.setOffline(a.id, ready = false, map = null, at = 0L)
        }
        return stale.size
    }

    /** Re-inserts a deleted article (undo) with its original id and tags. */
    suspend fun restoreArticle(article: ArticleEntity, tagIds: List<Long>) {
        articles.insert(article)
        if (tagIds.isNotEmpty()) {
            tags.insertRefs(tagIds.map { ArticleTagCrossRef(article.id, it) })
        }
    }

    /**
     * Downloads every image in the article to app storage so the article
     * can be read fully offline. Returns the number of images saved.
     */
    suspend fun downloadForOffline(article: ArticleEntity): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val urls = extractImageUrls(article.contentHtml)
            if (urls.isEmpty()) return@runCatching 0
            val dir = java.io.File(context.filesDir, "offline/${article.id}").apply { mkdirs() }
            val map = mutableMapOf<String, String>()
            val client = WebFetcher.defaultClient()
            urls.distinct().forEach { url ->
                val file = java.io.File(dir, "${url.hashCode()}.img")
                val ok = if (file.exists() && file.length() > 100) {
                    true
                } else {
                    try {
                        val request = okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", WebFetcher.USER_AGENT)
                            .build()
                        client.newCall(request).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input ->
                                    file.outputStream().use { input.copyTo(it) }
                                }
                                file.length() > 100
                            } else {
                                false
                            }
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
                if (ok) map[url] = file.absolutePath
            }
            if (map.isNotEmpty()) {
                articles.setOffline(
                    article.id,
                    ready = true,
                    map = dev.trove.app.util.LocalImagesJson.encode(map),
                    at = System.currentTimeMillis(),
                )
            }
            map.size
        }
    }

    private fun extractImageUrls(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        return runCatching {
            org.jsoup.Jsoup.parseBodyFragment(html)
                .select("img[src]")
                .mapNotNull { img ->
                    img.absUrl("src").takeIf { it.isNotEmpty() && !it.startsWith("data:") }
                }
        }.getOrDefault(emptyList())
    }

    suspend fun setArticleTags(articleId: Long, tagIds: Set<Long>) {
        tags.clearArticleRefs(articleId)
        if (tagIds.isNotEmpty()) tags.insertRefs(tagIds.map { ArticleTagCrossRef(articleId, it) })
    }

    suspend fun getTags(): List<TagEntity> = tags.getAll()

    suspend fun getArticleTags(articleId: Long): List<TagEntity> = tags.getTagsForArticle(articleId)

    // ---------------------------------------------------------- folder ops

    suspend fun createFolder(name: String, colorIndex: Int, parentId: Long? = null): Long {
        val pos = (folders.maxPosition() ?: -1) + 1
        return folders.insert(FolderEntity(name = name, colorIndex = colorIndex, position = pos, parentId = parentId))
    }

    suspend fun updateFolder(folder: FolderEntity, name: String, colorIndex: Int, parentId: Long?) {
        folders.update(folder.copy(name = name, colorIndex = colorIndex, parentId = parentId))
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        folders.delete(folder)
        // Unfile the articles that lived in it; subfolders move to top level
        articles.getByFolder(folder.id).forEach { articles.setFolder(it.id, null) }
        folders.clearParent(folder.id)
    }

    // ------------------------------------------------------------ tag ops

    suspend fun createTag(name: String, colorIndex: Int): Long {
        tags.getByName(name)?.let { return it.id }
        return tags.insert(TagEntity(name = name, colorIndex = colorIndex))
    }

    suspend fun updateTag(tag: TagEntity, name: String, colorIndex: Int) {
        tags.update(tag.copy(name = name, colorIndex = colorIndex))
    }

    suspend fun deleteTag(tag: TagEntity) {
        tags.clearRefsForTag(tag.id)
        tags.delete(tag)
    }

    // -------------------------------------------------------------- utils

    fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) throw IllegalArgumentException("empty url")
        if (!url.contains("://")) url = "https://$url"
        val uri = URI(url)
        require(uri.host != null && uri.host.contains('.')) { "no host" }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$port${uri.path.ifEmpty { "/" }}" +
            (if (uri.query != null) "?${uri.query}" else "")
    }

    /** Builds a safe FTS5 MATCH query with prefix matching per token. */
    private fun ftsQuery(query: String): String =
        query.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"$it\"*" }
            .ifEmpty { "\"\"" }
}
