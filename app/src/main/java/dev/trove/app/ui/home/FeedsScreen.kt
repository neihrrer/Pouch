package dev.trove.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.trove.app.R
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.data.db.FeedWithCount
import dev.trove.app.ui.components.AccentChip
import dev.trove.app.ui.components.ExpressiveButton
import dev.trove.app.ui.components.EmptyState
import dev.trove.app.ui.components.ExpressiveCard
import dev.trove.app.ui.components.Favicon
import dev.trove.app.ui.components.SectionHeader
import dev.trove.app.ui.sheets.NewEntitySheet
import dev.trove.app.util.SnackbarBus
import dev.trove.app.util.timeAgo

@Composable
fun FeedsTab(state: HomeUiState, vm: HomeViewModel, onOpenArticle: (Long) -> Unit) {
    val context = LocalContext.current
    // Pre-resolved strings for non-composable callbacks
    val strExported = stringResource(R.string.feeds_exported)
    val strExportFailed = stringResource(R.string.feeds_export_failed)
    val strNoFeeds = stringResource(R.string.snack_no_feeds_found)

    // Browsing a feed's items (or the combined "All feeds" view)
    if (state.browsingFeedId != null) {
        val feed = state.feeds.firstOrNull { it.feed.id == state.browsingFeedId }
        val allFeeds = state.browsingFeedId == HomeViewModel.ALL_FEEDS
        FeedBrowse(
            feedTitle = if (allFeeds) stringResource(R.string.feeds_all) else (feed?.feed?.title ?: stringResource(R.string.tab_feeds)),
            subtitle = if (allFeeds) stringResource(R.string.feeds_items_all, state.articles.size) else null,
            items = state.articles,
            onBack = vm::closeFeed,
            onRefresh = feed?.let { { vm.fetchFeed(it) } } ?: {},
            onOpenArticle = onOpenArticle,
            onSave = vm::saveFeedItem,
        )
        return
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (!text.isNullOrBlank()) vm.importOpml(text) else SnackbarBus.post(SnackbarBus.Event(strNoFeeds))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val opml = vm.exportOpml()
        runCatching {
            context.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(opml) }
            SnackbarBus.post(SnackbarBus.Event(strExported))
        }.onFailure {
            SnackbarBus.post(SnackbarBus.Event(strExportFailed))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = stringResource(R.string.tab_feeds),
            count = state.feeds.size,
            large = true,
            modifier = Modifier.padding(top = 16.dp),
            action = {
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.feeds_import))
                }
                IconButton(onClick = { exportLauncher.launch("trove-feeds.opml") }) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = stringResource(R.string.feeds_export))
                }
                IconButton(onClick = vm::fetchAllFeedsViaWorker) {
                    Icon(Icons.Rounded.RssFeed, contentDescription = stringResource(R.string.feeds_refresh_all))
                }
                IconButton(onClick = vm::showAddFeed) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.feeds_add_sheet_title))
                }
            },
        )

        // Category filter chips — same row component as every other menu
        FilterChipRow(
            chips = listOf(
                FilterChip(stringResource(R.string.lib_filter_all), state.selectedFeedCategoryId == null) { vm.setFeedCategoryFilter(null) },
            ) + state.feedCategories.map { category ->
                FilterChip(
                    label = category.name,
                    selected = state.selectedFeedCategoryId == category.id,
                ) { vm.setFeedCategoryFilter(category.id) }
            } + listOf(
                FilterChip(stringResource(R.string.feeds_category_new), false) { vm.showNewCategory() },
            ),
        )

        Spacer(Modifier.height(8.dp))

        if (state.syncActive) {
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.feeds.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.RssFeed,
                title = stringResource(R.string.feeds_empty_title),
                subtitle = stringResource(R.string.feeds_empty_subtitle),
                actionLabel = stringResource(R.string.feeds_add),
                onAction = vm::showAddFeed,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // "All feeds" behaves like any other feed — first in the list
                item(key = "all-feeds") {
                    AllFeedsCard(
                        totalUnread = state.feeds.sumOf { it.unreadCount },
                        onClick = vm::openAllFeeds,
                        modifier = Modifier.animateItem(),
                    )
                }
                items(state.feeds, key = { it.feed.id }) { feed ->
                    FeedCard(
                        feed = feed,
                        onClick = { vm.openFeed(feed.feed.id) },
                        onLongClick = { vm.showEditFeed(feed) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (state.showAddFeedSheet) {
        FeedEditSheet(
            title = stringResource(R.string.feeds_add_sheet_title),
            categories = state.feedCategories,
            onSave = { url, categoryId -> vm.addFeed(url, categoryId) },
            onCreateCategory = vm::addCategory,
            onDismiss = vm::hideAddFeed,
        )
    }
    state.editingFeed?.let { feed ->
        FeedEditSheet(
            title = stringResource(R.string.feeds_edit_sheet_title),
            categories = state.feedCategories,
            initialUrl = feed.feed.url,
            initialName = feed.feed.title,
            initialCategoryId = feed.feed.categoryId,
            isEdit = true,
            onSave = { _, categoryId ->
                vm.updateFeed(feed, feed.feed.title, categoryId)
            },
            onDelete = { vm.deleteFeedWithMessage(feed) },
            onCreateCategory = vm::addCategory,
            onDismiss = vm::hideEditFeed,
        )
    }
    if (state.showNewCategory) {
        NewEntitySheet(
            title = stringResource(R.string.common_new_category),
            showColorPicker = false,
            onCreate = { name, _ -> vm.addCategory(name) },
            onDismiss = vm::hideNewCategory,
        )
    }
}

@Composable
private fun AllFeedsCard(
    totalUnread: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Box(modifier = Modifier.padding(10.dp).size(24.dp)) {
                    Icon(
                        Icons.Rounded.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.feeds_all), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.feeds_all_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (totalUnread > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiary,
                ) {
                    Text(
                        "$totalUnread",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedCard(
    feed: FeedWithCount,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(modifier = Modifier.padding(10.dp).size(24.dp)) {
                    Favicon(null, feed.feed.url, size = 24.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        feed.feed.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (feed.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiary,
                        ) {
                            Text(
                                "${feed.unreadCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(feed.feed.siteUrl?.removePrefix("https://")?.removePrefix("http://") ?: "")
                        if (feed.articleCount > 0) append(" · " + stringResource(R.string.lib_count_saved, feed.articleCount))
                        if (feed.feed.lastFetchedAt > 0) append(" · " + stringResource(R.string.feeds_fetch_status, timeAgo(feed.feed.lastFetchedAt)))
                        if (feed.feed.fetchFailed) append(" · " + stringResource(R.string.feeds_failed))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feed.feed.fetchFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedEditSheet(
    title: String,
    categories: List<dev.trove.app.data.db.FeedCategoryEntity>,
    onSave: (String, Long?) -> Unit,
    onDismiss: () -> Unit,
    initialUrl: String = "",
    initialName: String = "",
    initialCategoryId: Long? = null,
    isEdit: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onCreateCategory: ((String) -> Unit)? = null,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var categoryId by rememberSaveable { mutableStateOf(initialCategoryId) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isEdit) initialName else stringResource(R.string.feeds_url_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (!isEdit) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.feeds_url_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { url = it }
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.feeds_paste_clipboard))
                        }
                    },
                    shape = RoundedCornerShape(50),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(stringResource(R.string.feeds_category), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                AccentChip(
                    text = stringResource(R.string.common_none),
                    colorIndex = 0,
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                )
                categories.forEach { category ->
                    AccentChip(
                        text = category.name,
                        colorIndex = category.id.toInt() % 12,
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                    )
                }
                if (onCreateCategory != null) {
                    AccentChip(
                        text = stringResource(R.string.feeds_category_new),
                        colorIndex = 5,
                        selected = false,
                        onClick = { showNewCategory = true },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(
                onClick = { if (url.isNotBlank()) onSave(url.trim(), categoryId) },
                text = if (isEdit) stringResource(R.string.common_save) else stringResource(R.string.feeds_add_sheet_title),
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isEdit && onDelete != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feeds_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showNewCategory && onCreateCategory != null) {
        NewEntitySheet(
            title = stringResource(R.string.common_new_category),
            showColorPicker = false,
            onCreate = { name, _ ->
                onCreateCategory(name)
                showNewCategory = false
            },
            onDismiss = { showNewCategory = false },
        )
    }
}

@Composable
private fun FeedBrowse(
    feedTitle: String,
    items: List<ArticleWithTags>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    onSave: (ArticleWithTags) -> Unit,
    subtitle: String? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(feedTitle, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle ?: stringResource(R.string.feeds_items_hint, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.RssFeed, contentDescription = stringResource(R.string.feeds_refresh))
            }
        }
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.RssFeed,
                title = stringResource(R.string.feeds_no_items_title),
                subtitle = stringResource(R.string.feeds_no_items_subtitle),
                actionLabel = stringResource(R.string.feeds_refresh),
                onAction = onRefresh,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.article.id }) { item ->
                    FeedItemCard(
                        item = item,
                        onOpen = { onOpenArticle(item.article.id) },
                        onSave = { onSave(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedItemCard(
    item: ArticleWithTags,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(onClick = onOpen, modifier = modifier) {
        Row(modifier = Modifier.padding(14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.article.excerpt.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.article.excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    timeAgo(item.article.addedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (item.article.saved) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.feeds_in_library),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.feeds_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onSave,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.feeds_add_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
