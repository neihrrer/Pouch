package dev.trove.app.data

import java.net.URI
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Extracts a clean, readable version of a web page:
 * metadata (title, site, byline, lead image, favicon) plus the main article
 * content as normalized HTML. The algorithm is a lightweight port of the
 * classic Readability scoring heuristics tuned for modern markup.
 */
class ReaderExtractor {

    fun extract(doc: Document, sourceUrl: String): ExtractedArticle {
        // Primary extraction: readability4j — a faithful Mozilla Readability
        // port (the same approach Read You uses). Falls back to the local
        // scoring heuristics when it finds nothing substantial.
        val readability = runCatching {
            Readability4JExtended(sourceUrl, doc).parse()
        }.getOrNull()
        val r4jContent = readability?.articleContent
            ?.takeIf { it.text().trim().length >= MIN_CONTENT_CHARS }

        val content = if (r4jContent != null) {
            // Wrap in a body fragment so cleanup never unwraps the root node
            val fragment = Jsoup.parseBodyFragment(r4jContent.html(), sourceUrl).body()
            cleanContent(fragment)
            fragment
        } else {
            var c = findContent(doc)
            cleanContent(c)
            // If scoring found nothing substantial, fall back to the largest
            // paragraph cluster in the page, then finally to the whole body.
            if (c.text().trim().length < MIN_CONTENT_CHARS) {
                c = fallbackContent(doc)
                cleanContent(c)
            }
            c
        }

        val title = readability?.title?.trim()?.ifEmpty { null }
            ?: meta(doc, "og:title")?.trim()
            ?: doc.title().trim().ifEmpty { null }
            ?: content.selectFirst("h1")?.text()?.trim()
            ?: hostOf(sourceUrl)

        val siteName = meta(doc, "og:site_name")?.trim() ?: hostOf(sourceUrl)
        val byline = meta(doc, "article:author")?.trim()
            ?: meta(doc, "author")?.trim()
            ?: content.selectFirst("a[rel=author]")?.text()?.trim()

        val publishedAt = parseTimestamp(
            meta(doc, "article:published_time")
                ?: meta(doc, "date")
                ?: content.selectFirst("time[datetime]")?.attr("datetime")
        )

        val leadImage = meta(doc, "og:image")?.trim()
            ?.takeUnless { it.startsWith("data:") }
            ?: bestImage(content)

        val html = content.html()
        val text = content.text().replace(Regex("\\s+"), " ").trim()

        return ExtractedArticle(
            title = title,
            siteName = siteName,
            byline = byline,
            excerpt = excerptFrom(html),
            contentHtml = html,
            contentText = text,
            leadImageUrl = leadImage,
            faviconUrl = faviconOf(doc, sourceUrl),
            publishedAt = publishedAt,
        )
    }

    /**
     * Cleans arbitrary article HTML (e.g. from an RSS feed) using the same
     * pipeline as full-page extraction. Returns (cleanHtml, cleanText).
     */
    fun cleanArticleHtml(html: String, baseUrl: String): Pair<String, String> {
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        cleanContent(doc.body())
        val text = doc.body().text().replace(Regex("\\s+"), " ").trim()
        return doc.body().html() to text
    }

    // ---------------------------------------------------------------- content

    private fun findContent(doc: Document): Element {
        val candidates = doc.body().select(CANDIDATE_SELECTOR).filter(::isVisible)
        var best: Element? = null
        var bestScore = 0.0
        for (el in candidates) {
            val score = score(el)
            if (score > bestScore) {
                bestScore = score
                best = el
            }
        }
        val chosen = best?.takeIf { scoreText(it) >= MIN_CONTENT_CHARS }
            ?: doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.body()
        // Walk down to the densest child container so we don't keep the whole page.
        return squeeze(chosen)
    }

    /**
     * Last-resort extraction: clone the body, strip chrome and pick the
     * container holding the most paragraph text.
     */
    private fun fallbackContent(doc: Document): Element {
        val clone = doc.body().clone()
        clone.select(STRIP_SELECTOR).remove()
        clone.select("*")
            .filter { hasNegativeHints(it) && it.select("p").isEmpty() }
            .forEach { it.remove() }
        var best: Element? = null
        var bestLen = 0
        for (el in clone.select("div, section, article, main, body")) {
            if (!isVisible(el)) continue
            val len = el.select("p").sumOf { it.text().trim().length }
            if (len > bestLen) {
                bestLen = len
                best = el
            }
        }
        return best ?: clone
    }

    /** Moves from a big wrapper down to the child that holds most of the text. */
    private fun squeeze(start: Element): Element {
        var current = start
        while (true) {
            val children = current.children().filter { isVisible(it) && it.tagName() in WRAPPERS }
            if (children.size != 1) break
            val child = children.first()
            val keep = !hasNegativeHints(child) && !hasPositiveHints(current)
            if (!keep) break
            if (scoreText(child) < scoreText(current) * 0.75) break
            current = child
        }
        return current
    }

    private fun score(el: Element): Double {
        var score = 0.0
        val hints = el.id() + " " + el.className()
        if (hasPositiveHints(el)) score += 40
        if (hasNegativeHints(el)) score -= 60
        if (el.attr("role") == "main") score += 30
        when (el.tagName()) {
            "article" -> score += 30
            "main" -> score += 20
            "blockquote" -> score += 5
        }
        // Short paragraphs are common in interviews and news briefs — don't
        // skip them entirely, just weight them less.
        for (p in el.select("p, pre, td, blockquote")) {
            if (!isVisible(p)) continue
            val text = p.text().trim()
            if (text.length < 15) continue
            val links = p.select("a").sumOf { it.text().length }
            val density = if (text.isEmpty()) 1.0 else links.toDouble() / text.length
            score += minOf(text.length.toDouble(), 300.0) * (1.0 - density)
        }
        // Headings carry weight too — pages that render text in headings
        // (rare but real) still need to be found.
        for (h in el.select("h1, h2, h3, h4")) {
            val text = h.text().trim()
            if (text.length >= 20) score += minOf(text.length.toDouble(), 120.0)
        }
        return score
    }

    private fun scoreText(el: Element): Int =
        el.select("p, pre, td, blockquote, h1, h2, h3").sumOf { it.text().trim().length }

    // ------------------------------------------------------------- cleaning

    private fun cleanContent(content: Element) {
        // Strip entire subtrees we never want.
        content.select(STRIP_SELECTOR).remove()
        // Drop elements with negative class/id hints (comments, sidebars, ads...).
        content.select("*").filter { hasNegativeHints(it) && it.select("p, img").isEmpty() }.forEach { it.remove() }
        // Drop near-empty paragraphs and link farms.
        content.select("p").forEach { p ->
            val text = p.text().trim()
            if (text.length < 20 && p.select("img").isEmpty()) p.remove()
            else if (linkDensity(p) > 0.55) p.remove()
        }
        // Keep only meaningful elements; flatten the rest.
        flatten(content)
        // Resolve relative URLs against the document and drop broken images.
        content.select("img").forEach { img ->
            val src = img.absUrl("src")
            if (src.isEmpty() || src.startsWith("data:") || src.startsWith("blob:")) img.remove()
            else img.attr("src", src)
        }
        content.select("a").forEach { a ->
            val href = a.absUrl("href")
            if (href.isNotEmpty()) a.attr("href", href)
        }
        content.select("source").remove()
        content.select("picture").unwrap()
        content.select("[style]").removeAttr("style")
        content.select("[class]").removeAttr("class")
        content.select("[id]").removeAttr("id")
        content.select("div").unwrap()
        content.select("section").unwrap()
        content.select("span").unwrap()
        content.select("br").remove()
        content.select("p").forEach { p ->
            if (p.text().isBlank() && p.select("img").isEmpty()) p.remove()
        }
        content.select("figure").forEach { fig ->
            if (fig.select("img").isEmpty()) fig.unwrap()
        }
    }

    /** Recursively unwrap unknown wrappers, keeping text and known semantic tags. */
    private fun flatten(el: Element) {
        val children = el.children().toList()
        for (child in children) {
            if (child.tagName() in KEEP_TAGS) flatten(child) else child.unwrap()
        }
    }

    private fun linkDensity(el: Element): Double {
        val text = el.text().trim()
        if (text.isEmpty()) return 1.0
        return el.select("a").sumOf { it.text().trim().length }.toDouble() / text.length
    }

    // ------------------------------------------------------------- metadata

    private fun meta(doc: Document, key: String): String? {
        doc.select("meta").forEach { m ->
            val name = m.attr("name").lowercase()
            val property = m.attr("property").lowercase()
            if (name == key.lowercase() || property == key.lowercase()) {
                val c = m.attr("content").trim()
                if (c.isNotEmpty()) return c
            }
        }
        return null
    }

    private fun faviconOf(doc: Document, url: String): String? {
        val links = doc.select("link[rel]").filter {
            it.attr("rel").lowercase().split(" ").any { r -> r == "icon" || r == "shortcut icon" }
        }
        val best = links.mapNotNull { it.absUrl("href") }
            .firstOrNull { !it.startsWith("data:") }
            ?: runCatching { URI(url).let { u -> "${u.scheme}://${u.host}/favicon.ico" } }.getOrNull()
        return best
    }

    private fun bestImage(el: Element): String? {
        el.select("img").forEach { img ->
            val w = img.attr("width").toIntOrNull() ?: 0
            val h = img.attr("height").toIntOrNull() ?: 0
            if (w >= 200 && h >= 120) {
                val src = img.absUrl("src")
                if (src.isNotEmpty() && !src.startsWith("data:")) return src
            }
        }
        return null
    }

    private fun parseTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        return runCatching {
            val normalized = when {
                value.endsWith("Z") -> value.replace("Z", "+00:00")
                value.endsWith("+0000") -> value.dropLast(5) + "+00:00"
                else -> value
            }
            val instant = runCatching { java.time.Instant.parse(normalized) }
                .getOrElse { java.time.OffsetDateTime.parse(normalized).toInstant() }
            instant.toEpochMilli()
        }.getOrNull()
    }

    private fun excerptFrom(html: String): String? {
        val text = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        val cut = text.indexOf(' ', 260).takeIf { it > 0 } ?: text.length
        val excerpt = text.substring(0, minOf(cut, text.length))
        return excerpt.trim() + if (excerpt.length < text.length) "…" else ""
    }

    private fun isVisible(el: Element): Boolean {
        if (el.hasAttr("hidden") || el.attr("aria-hidden") == "true") return false
        val style = el.attr("style").lowercase()
        if ("display:none" in style || "visibility:hidden" in style || "opacity:0" in style) return false
        return true
    }

    private fun hasPositiveHints(el: Element): Boolean =
        POSITIVE_HINTS.containsMatchIn(el.id() + " " + el.className())

    private fun hasNegativeHints(el: Element): Boolean =
        NEGATIVE_HINTS.containsMatchIn(el.id() + " " + el.className())

    private fun hostOf(url: String): String =
        runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

    companion object {
        private const val MIN_CONTENT_CHARS = 140
        private val CANDIDATE_SELECTOR = "article, main, section, div, td, pre, blockquote"
        private val WRAPPERS = setOf("div", "section", "article", "main")
        private val KEEP_TAGS = setOf(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote",
            "ul", "ol", "li", "img", "figure", "figcaption", "table", "thead",
            "tbody", "tr", "th", "td", "hr", "strong", "em", "b", "i", "u",
            "a", "code", "sub", "sup", "mark", "small", "del", "s",
        )
        private val STRIP_SELECTOR = "script, style, noscript, iframe, form, button, input, " +
            "select, textarea, nav, aside, footer, header, svg, canvas, video, audio, " +
            "template, object, embed, dialog, figure:empty"
        private val NEGATIVE_HINTS = Regex(
            """(?i)comment|meta|foot|side|advert|ads-|social|share|related|promo|menu|nav|header|banner|newsletter|signup|widget|cookie|popup|overlay|modal|breadcrumb|pagination|rating|author|bio|profile|recent|popular|recommend|trend|sponsor|suggested|must.?read|also.?read"""
        )
        private val POSITIVE_HINTS =
            Regex("""(?i)article|post|story|entry|content|main|body|readable|text|page""")
    }
}

data class ExtractedArticle(
    val title: String,
    val siteName: String,
    val byline: String?,
    val excerpt: String?,
    val contentHtml: String,
    val contentText: String,
    val leadImageUrl: String?,
    val faviconUrl: String?,
    val publishedAt: Long?,
)
