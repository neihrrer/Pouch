package dev.trove.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.trove.app.data.db.ArticleEntity
import dev.trove.app.ui.components.rememberMissingImagePainter
import dev.trove.app.ui.theme.ExpressiveSprings
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// --------------------------------------------------------------------------
// Parsed article model
// --------------------------------------------------------------------------

sealed interface ArticleBlock {
    data class Heading(val level: Int, val spans: List<InlineSpan>) : ArticleBlock
    data class Paragraph(val spans: List<InlineSpan>) : ArticleBlock
    data class Quote(val spans: List<InlineSpan>) : ArticleBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<InlineSpan>>) : ArticleBlock
    data class Code(val text: String) : ArticleBlock
    data class Image(val url: String, val caption: String?) : ArticleBlock
    data class Table(val rows: List<List<List<InlineSpan>>>) : ArticleBlock
    data object Divider : ArticleBlock
}

data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/** A stored highlight: character range within a block's plain text. */
data class HighlightRange(
    val start: Int,
    val end: Int,
    val colorIndex: Int,
    val text: String,
)

/** Theme-contrast highlight colors (translucent on any background). */
val HighlightPalette = listOf(
    androidx.compose.ui.graphics.Color(0xFFF9A825), // amber
    androidx.compose.ui.graphics.Color(0xFF43A047), // green
    androidx.compose.ui.graphics.Color(0xFFE53935), // red
    androidx.compose.ui.graphics.Color(0xFF1E88E5), // blue
    androidx.compose.ui.graphics.Color(0xFFD81B60), // pink
    androidx.compose.ui.graphics.Color(0xFF8E24AA), // purple
)

/** Parses normalized article HTML into renderable blocks. */
object ArticleHtmlParser {

    fun parse(html: String?): List<ArticleBlock> {
        if (html.isNullOrBlank()) return emptyList()
        val doc = runCatching { Jsoup.parseBodyFragment(html) }.getOrNull() ?: return emptyList()
        return doc.body().children().mapNotNull { toBlock(it) }
    }

    private fun toBlock(el: Element): ArticleBlock? {
        return when (el.tagName()) {
            "h1", "h2", "h3", "h4", "h5", "h6" ->
                ArticleBlock.Heading(el.tagName().substring(1).toInt(), inline(el))
            "p" -> ArticleBlock.Paragraph(inline(el))
            "blockquote" -> ArticleBlock.Quote(inline(el))
            "ul", "ol" -> {
                val items = el.children().filter { it.tagName() == "li" }
                    .map { inline(it) }
                    .filter { it.isNotEmpty() }
                if (items.isEmpty()) null else ArticleBlock.ListBlock(el.tagName() == "ol", items)
            }
            "pre" -> {
                val code = el.select("code").firstOrNull() ?: el
                ArticleBlock.Code(code.text().trim())
            }
            "code" -> ArticleBlock.Code(el.text().trim())
            "img" -> el.absUrl("src").takeIf { it.isNotEmpty() }?.let {
                ArticleBlock.Image(it, null)
            }
            "figure" -> {
                val img = el.selectFirst("img")
                val caption = el.selectFirst("figcaption")?.text()?.trim()
                if (img != null) {
                    val src = img.absUrl("src")
                    if (src.isNotEmpty()) ArticleBlock.Image(src, caption?.takeIf { it.isNotEmpty() })
                    else null
                } else {
                    toBlockChildren(el).firstOrNull()
                }
            }
            "table" -> {
                val rows = el.select("tr").map { tr ->
                    tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
                        .map { inline(it) }
                }.filter { it.isNotEmpty() }
                if (rows.isEmpty()) null else ArticleBlock.Table(rows)
            }
            "hr" -> ArticleBlock.Divider
            "div", "section", "article", "main", "center", "aside" -> {
                val blocks = toBlockChildren(el)
                blocks.firstOrNull() // unwrap one level; nested blocks handled by caller
            }
            else -> null
        }
    }

    private fun toBlockChildren(el: Element): List<ArticleBlock> =
        el.children().mapNotNull { toBlock(it) }

    /** Inline content of an element: text with formatting and links. */
    private fun inline(el: Element): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        val pending = StringBuilder()
        fun flush() {
            if (pending.isNotEmpty()) {
                spans += InlineSpan(pending.toString())
                pending.clear()
            }
        }
        fun walk(node: Node, bold: Boolean, italic: Boolean, code: Boolean, link: String?) {
            when (node) {
                is TextNode -> pending.append(node.text())
                is Element -> {
                    val children = node.childNodes()
                    when (node.tagName()) {
                        "br" -> {
                            flush()
                            spans += InlineSpan("\n")
                        }
                        "img" -> {
                            flush()
                            node.absUrl("src").takeIf { it.isNotEmpty() }?.let {
                                spans += InlineSpan(" [image] ", link = it)
                            }
                        }
                        "a" -> {
                            val href = node.absUrl("href")
                            children.forEach { walk(it, bold, italic, code, href.takeIf { h -> h.isNotEmpty() } ?: link) }
                        }
                        "strong", "b" -> children.forEach { walk(it, true, italic, code, link) }
                        "em", "i" -> children.forEach { walk(it, bold, true, code, link) }
                        "code", "kbd", "samp", "tt" -> children.forEach { walk(it, bold, italic, true, link) }
                        else -> children.forEach { walk(it, bold, italic, code, link) }
                    }
                }
            }
        }
        el.childNodes().forEach { walk(it, bold = false, italic = false, code = false, link = null) }
        flush()

        // Merge adjacent spans
        val merged = mutableListOf<InlineSpan>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic && last.code == s.code && last.link == s.link) {
                merged[merged.size - 1] = last.copy(text = last.text + s.text)
            } else {
                merged += s
            }
        }
        return merged.filter { it.text.isNotBlank() }
    }
}

// --------------------------------------------------------------------------
// Reader typography derived from settings
// --------------------------------------------------------------------------
// Reader typography derived from settings
// --------------------------------------------------------------------------

data class ReaderTextStyle(
    val bodySize: Int,
    val bodyLineHeight: Int,
    val headingScale: Float,
    val fontFamily: FontFamily,
    val textAlign: TextAlign = TextAlign.Start,
    val letterSpacing: Float = 0.2f,
    val wordSpacing: Float = 0f,
)

@Composable
fun readerFontFamily(font: dev.trove.app.data.ReaderFont): FontFamily = when (font) {
    dev.trove.app.data.ReaderFont.SANS -> FontFamily.Default
    dev.trove.app.data.ReaderFont.SERIF -> FontFamily.Serif
    dev.trove.app.data.ReaderFont.MONOSPACE -> dev.trove.app.ui.theme.GoogleSansCode
    dev.trove.app.data.ReaderFont.OPENDYSLEXIC -> dev.trove.app.ui.theme.OpenDyslexic
}

@Composable
fun rememberReaderTextStyle(
    readerFont: dev.trove.app.data.ReaderFont,
    fontSizeScale: Float,
    lineHeightScale: Float,
    letterSpacing: Float,
    wordSpacing: Float,
    textAlign: dev.trove.app.data.ReaderAlign,
): ReaderTextStyle {
    val base = MaterialTheme.typography.bodyLarge.fontSize.value
    val size = (base * fontSizeScale).toInt()
    val line = (MaterialTheme.typography.bodyLarge.lineHeight.value * fontSizeScale * lineHeightScale).toInt()
    val family = readerFontFamily(readerFont)
    val align = when (textAlign) {
        dev.trove.app.data.ReaderAlign.LEFT -> TextAlign.Start
        dev.trove.app.data.ReaderAlign.JUSTIFIED -> TextAlign.Justify
        dev.trove.app.data.ReaderAlign.CENTER -> TextAlign.Center
    }
    return ReaderTextStyle(
        bodySize = size,
        bodyLineHeight = line,
        headingScale = fontSizeScale,
        fontFamily = family,
        textAlign = align,
        letterSpacing = letterSpacing,
        wordSpacing = wordSpacing,
    )
}

/**
 * Emulated word spacing: inserts thin spaces (U+2009) after word gaps.
 * Compose has no native word-spacing property, and thin spaces stretch
 * along with regular ones under justification.
 */
private fun applyWordSpacing(text: String, scale: Float): String {
    if (scale <= 0.02f) return text
    val extra = (scale * 2f).toInt().coerceIn(0, 3)
    if (extra == 0) return text
    val thin = "\u2009".repeat(extra)
    return text.replace(" ", " $thin")
}

// --------------------------------------------------------------------------
// Block renderer
// --------------------------------------------------------------------------

@Composable
fun ArticleBlockView(
    block: ArticleBlock,
    style: ReaderTextStyle,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    localImages: Map<String, String> = emptyMap(),
    blockIndex: Int = 0,
    highlightRanges: List<HighlightRange> = emptyList(),
    onHighlight: ((Int, Int, Int, String, Int) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val family = style.fontFamily

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    when (block) {
        is ArticleBlock.Heading -> {
            val size = when (block.level) {
                1 -> 34
                2 -> 28
                3 -> 24
                else -> 20
            }
            Text(
                text = buildAnnotatedText(block.spans, style, ::open, style.wordSpacing),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (size * style.headingScale).sp,
                    lineHeight = ((size + 8) * style.headingScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = family,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
            )
        }
        is ArticleBlock.Paragraph -> {
            val plain = block.spans.joinToString("") { it.text }
            val validRanges = remember(highlightRanges, plain) {
                highlightRanges.filter { r ->
                    r.end <= plain.length &&
                        plain.substring(r.start, r.end).trim() == r.text.trim()
                }
            }
            val selectionState = remember { SelectionState() }
            var selectedPlain by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(block, selectionState) {
                // selectedTexts exposes the exact selection (word-spacing thin
                // spaces stripped) — no anchor math needed.
                snapshotFlow { selectionState.selectedTexts }
                    .collect { texts ->
                        selectedPlain = texts.firstOrNull()?.text?.replace("\u2009", "")
                    }
            }
            val annotated = buildAnnotatedText(block.spans, style, ::open, style.wordSpacing, validRanges)
            SelectionContainer(selectionState, modifier = modifier) {
                Text(
                    text = annotated,
                    style = baseBodyStyle(style),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            val selection = selectedPlain
            if (selection != null && selection.isNotBlank() && onHighlight != null) {
                val start = plain.indexOf(selection)
                if (start >= 0) {
                    val end = start + selection.length
                    val density = LocalDensity.current.density
                    Popup(
                        alignment = Alignment.BottomCenter,
                        offset = IntOffset(0, -(150 * density).roundToInt()),
                        onDismissRequest = { selectedPlain = null },
                        properties = PopupProperties(focusable = true),
                    ) {
                        HighlightBar(
                            onPick = { color ->
                                onHighlight(blockIndex, start, end, selection, color)
                                selectedPlain = null
                            },
                        )
                    }
                }
            }
        }
        is ArticleBlock.Quote -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            ) {
                Text(
                    text = buildAnnotatedText(block.spans, style, ::open, style.wordSpacing),
                    style = baseBodyStyle(style).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is ArticleBlock.ListBlock -> {
            Column(modifier = modifier.padding(vertical = 6.dp)) {
                block.items.forEachIndexed { i, item ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = if (block.ordered) "${i + 1}." else "•",
                            style = baseBodyStyle(style).copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = buildAnnotatedText(item, style, ::open, style.wordSpacing),
                            style = baseBodyStyle(style),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        is ArticleBlock.Code -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is ArticleBlock.Image -> {
            val missing = rememberMissingImagePainter()
            val local = localImages[block.url]?.let { android.net.Uri.fromFile(java.io.File(it)) }
            Column(modifier = modifier.padding(vertical = 12.dp)) {
                AsyncImage(
                    model = local ?: block.url,
                    contentDescription = block.caption,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    error = missing,
                    placeholder = missing,
                )
                if (!block.caption.isNullOrBlank()) {
                    Text(
                        text = block.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
        is ArticleBlock.Table -> {
            Column(modifier = modifier.padding(vertical = 8.dp)) {
                block.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, cell ->
                            Text(
                                text = buildAnnotatedText(cell, style, ::open, style.wordSpacing),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = family),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(vertical = 2.dp, horizontal = 4.dp),
                            )
                            if (i < row.size - 1) Spacer(Modifier.width(8.dp))
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
        ArticleBlock.Divider -> {
            HorizontalDivider(
                modifier = modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun baseBodyStyle(style: ReaderTextStyle) =
    MaterialTheme.typography.bodyLarge.copy(
        fontSize = style.bodySize.sp,
        lineHeight = style.bodyLineHeight.sp,
        fontFamily = style.fontFamily,
        letterSpacing = style.letterSpacing.sp,
        textAlign = style.textAlign,
    )

@Composable
private fun buildAnnotatedText(
    spans: List<InlineSpan>,
    style: ReaderTextStyle,
    onClick: (String) -> Unit,
    wordSpacing: Float,
    highlightRanges: List<HighlightRange> = emptyList(),
): AnnotatedString {
    // Plain text + per-span bounds
    val plain = StringBuilder()
    val bounds = mutableListOf<Pair<Int, Int>>()
    spans.forEach { span ->
        bounds.add(plain.length to plain.length + span.text.length)
        plain.append(span.text)
    }
    // Segment boundaries: span edges + highlight edges
    val cuts = sortedSetOf(0, plain.length)
    bounds.forEach { cuts.add(it.first); cuts.add(it.second) }
    highlightRanges.forEach { cuts.add(it.start); cuts.add(it.end) }

    return buildAnnotatedString {
        val sorted = cuts.toList()
        for (i in 0 until sorted.size - 1) {
            val s = sorted[i]
            val e = sorted[i + 1]
            if (e <= s) continue
            val text = plain.substring(s, e)
            val span = bounds.indexOfFirst { it.first <= s && e <= it.second }
                .takeIf { it >= 0 }?.let { spans[it] }
            val hl = highlightRanges.firstOrNull { it.start <= s && e <= it.end }

            var spanStyle = SpanStyle(
                fontFamily = if (span?.code == true) FontFamily.Monospace else style.fontFamily,
                fontWeight = if (span?.bold == true) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (span?.italic == true) androidx.compose.ui.text.font.FontStyle.Italic else null,
            )
            if (span?.code == true) spanStyle = spanStyle.copy(color = MaterialTheme.colorScheme.primary)
            if (hl != null) {
                spanStyle = spanStyle.copy(
                    background = HighlightPalette[hl.colorIndex % HighlightPalette.size].copy(alpha = 0.30f)
                )
            }
            val out = applyWordSpacing(text, wordSpacing)
            if (span?.link != null) {
                val link = LinkAnnotation.Clickable(span.link) { onClick(span.link) }
                withLink(link) {
                    withStyle(spanStyle.copy(color = MaterialTheme.colorScheme.primary)) { append(out) }
                }
            } else {
                withStyle(spanStyle) { append(out) }
            }
        }
    }
}

@Composable
private fun HighlightBar(
    onPick: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Highlight",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HighlightPalette.forEachIndexed { index, color ->
                Surface(
                    onClick = { onPick(index) },
                    shape = RoundedCornerShape(50),
                    color = color,
                    modifier = Modifier.size(26.dp),
                ) {}
            }
        }
    }
}

/** Simple article header rendered above the body in the reader. */
@Composable
fun ArticleHeader(
    article: ArticleEntity,
    style: ReaderTextStyle,
    modifier: Modifier = Modifier,
    localImages: Map<String, String> = emptyMap(),
) {
    val family = style.fontFamily
    Column(modifier = modifier.fillMaxWidth()) {
        article.leadImageUrl?.let { url ->
            val missing = rememberMissingImagePainter()
            val local = localImages[url]?.let { android.net.Uri.fromFile(java.io.File(it)) }
            AsyncImage(
                model = local ?: url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                error = missing,
                placeholder = missing,
            )
            Spacer(Modifier.height(24.dp))
        }
        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = (36 * style.headingScale).sp,
                lineHeight = (42 * style.headingScale).sp,
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        val meta = listOfNotNull(
            article.siteName,
            article.byline,
            article.publishedAt?.let { dev.trove.app.util.fullDate(it) },
            article.contentText?.let { "${it.split(" ").size} words" },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        Spacer(Modifier.height(8.dp))
    }
}
