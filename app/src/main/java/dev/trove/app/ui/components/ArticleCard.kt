package dev.trove.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.ui.theme.accentColor
import dev.trove.app.util.LocalImagesJson
import dev.trove.app.util.timeAgo

/**
 * Expressive list card for a saved article: lead image, title, excerpt,
 * source + time, tag chips and unread/favorite markers.
 */
@Composable
fun ArticleCard(
    article: ArticleWithTags,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Swipe right: favorite (card settles back)
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleFavorite()
                    false
                }
                // Swipe left: delete (card dismisses; Undo via snackbar)
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Directional background: amber + "Favorite" on the right-swipe
            // side, red + "Delete" on the left-swipe side.
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBackground(
                    color = Color(0xFFF5A623),
                    icon = if (article.article.isFavorite) Icons.Rounded.Bookmark
                    else Icons.Rounded.BookmarkBorder,
                    label = if (article.article.isFavorite) "Unfavorite" else "Favorite",
                    alignEnd = false,
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeBackground(
                    color = Color(0xFFC62828),
                    icon = Icons.Rounded.Delete,
                    label = "Delete",
                    alignEnd = true,
                )
                else -> {}
            }
        },
        modifier = modifier,
    ) {
        ExpressiveCard(onClick = onClick, onLongClick = onLongClick) {
            Row(modifier = Modifier.padding(14.dp)) {
                ArticleThumb(article)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Favicon(article.article.faviconUrl, article.article.url, size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = article.article.siteName ?: article.article.url,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = timeAgo(article.article.addedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (article.article.isFavorite) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Rounded.Bookmark,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (article.article.offlineReady) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = "Offline copy saved",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = article.article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (!article.article.isRead) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (article.article.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!article.article.excerpt.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = article.article.excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (article.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            article.tags.take(3).forEach { tag ->
                                val accent = accentColor(tag.colorIndex)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = accent.container.copy(alpha = 0.75f),
                                ) {
                                    Text(
                                        tag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent.onContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (!article.article.isRead) {
                    Box(
                        Modifier
                            .padding(start = 10.dp, top = 8.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeBackground(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    alignEnd: Boolean,
) {
    Surface(color = color, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        ) {
            if (alignEnd) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Icon(icon, contentDescription = label, tint = Color.White)
            } else {
                Icon(icon, contentDescription = label, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ArticleThumb(article: ArticleWithTags) {
    val lead = article.article.leadImageUrl
    if (lead != null) {
        val missing = rememberMissingImagePainter()
        val local = remember(article.article.localImages) {
            LocalImagesJson.decode(article.article.localImages)
        }
        val model: Any? = local[lead]?.let { android.net.Uri.fromFile(java.io.File(it)) } ?: lead
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            error = missing,
            placeholder = missing,
        )
    } else {
        // initial-letter tile
        val accent = accentColor(Math.abs(article.article.title.hashCode()) % 12)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.container,
            modifier = Modifier.size(84.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = article.article.title.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent.onContainer,
                )
            }
        }
    }
}
