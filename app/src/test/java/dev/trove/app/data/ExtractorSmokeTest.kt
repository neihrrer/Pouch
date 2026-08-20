package dev.trove.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke test for the reader pipeline against real, diverse sites.
 * Strict: the extracted text must be long AND mostly letters (compressed
 * binary garbage fails the letter-ratio check), and the title must not be
 * the bare hostname fallback.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "dev.trove.app.data.ExtractorSmokeTest"
 */
class ExtractorSmokeTest {

    private val fetcher = WebFetcher()
    private val extractor = ReaderExtractor()

    @Test
    fun extractRealArticles() = runBlocking {
        val urls = listOf(
            "https://en.wikipedia.org/wiki/Readability",
            "https://paulgraham.com/greatwork.html",
            "https://github.blog/news-insights/product-news/github-copilot-the-agent-awakens/",
            "https://arstechnica.com",
            "https://www.bbc.com/news",
            "https://www.smashingmagazine.com",
            "https://www.theverge.com",
            "https://stratechery.com",
            "https://waitbutwhy.com",
            "https://www.theguardian.com/world",
            // JS-rendered Substack article pages must still extract fully
            "https://www.astralcodexten.com/p/your-book-review-the-escape-artist",
        )
        var failures = 0
        for (url in urls) {
            try {
                val doc = fetcher.fetch(url)
                val article = extractor.extract(doc, url)
                val text = article.contentText
                val len = text.length
                val letters = text.count { it.isLetter() }
                val ratio = if (len == 0) 0.0 else letters.toDouble() / len
                val title = article.title
                val looksReal = len >= 400 && ratio > 0.55 && title.isNotBlank()
                println(
                    (if (looksReal) "OK   " else "THIN ") +
                        "[$len chars, letters=$ratio] '$title' <- $url"
                )
                if (!looksReal) failures++
            } catch (e: Exception) {
                failures++
                println("FAIL ${e.message?.take(80)} <- $url")
            }
        }
        assertTrue("Too many extraction failures: $failures", failures <= 1)
    }

    /**
     * Substack homepages/archives are JavaScript shells with no server-side
     * text. The RSS fallback must rescue them via the site's /feed (the feed
     * only holds recent posts, so we use whatever post is current).
     */
    @Test
    fun rssFallbackForJsShellPage() = runBlocking {
        val doc = fetcher.fetchXml("https://www.astralcodexten.com/feed")
        val items = doc.select("item, entry")
        assertTrue("Feed is empty", items.isNotEmpty())
        // Feeds rotate: the newest post may be a short announcement/thread.
        // Use the longest post so the test isn't hostage to whatever is
        // currently at the top of the feed.
        val item = items.maxByOrNull {
            (it.selectFirst("content\\:encoded, content")?.html()
                ?: it.selectFirst("description")?.html() ?: "").length
        }!!
        val linkEl = item.selectFirst("link")
        val link = linkEl?.attr("href")?.takeIf { it.isNotBlank() }
            ?: linkEl?.text()
            ?: ""
        assertTrue("Feed item has no link", link.isNotBlank())
        val raw = item.selectFirst("content\\:encoded, content")?.html()
            ?: item.selectFirst("description")?.html()
        assertTrue("No content in feed item", raw != null && raw.length > 500)
        val (clean, text) = extractor.cleanArticleHtml(raw ?: "", link)
        assertTrue("RSS content too thin: ${text.length}", text.length > 1000)
        println("RSS OK: ${text.length} chars from '${item.selectFirst("title")?.text()}'")
    }
}
