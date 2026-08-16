package dev.trove.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.trove.app.util.domainOf

/**
 * Favicon for an article: the site's own icon when available, otherwise
 * Google's favicon service keyed by the article's domain, with a globe icon
 * as the last-resort placeholder.
 */
@Composable
fun Favicon(
    faviconUrl: String?,
    articleUrl: String,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val model = remember(faviconUrl, articleUrl) {
        if (!faviconUrl.isNullOrEmpty()) {
            faviconUrl
        } else {
            val domain = domainOf(articleUrl)
            if (domain.isEmpty()) null
            else "https://www.google.com/s2/favicons?domain=$domain&sz=64"
        }
    }
    val missing = rememberMissingFaviconPainter()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
                error = missing,
                placeholder = missing,
            )
        } else {
            Icon(
                missing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.8f),
            )
        }
    }
}
