package dev.trove.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.data.db.FolderWithCount
import dev.trove.app.data.db.TagWithCount
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.trove.app.BuildConfig
import dev.trove.app.R
import dev.trove.app.ui.components.AccentChip
import dev.trove.app.ui.components.ArticleCard
import dev.trove.app.ui.components.EmptyState
import dev.trove.app.ui.components.ExpressiveButton
import dev.trove.app.ui.components.ExpressiveCard
import dev.trove.app.ui.components.SectionHeader
import dev.trove.app.ui.sheets.AddUrlSheet
import dev.trove.app.ui.sheets.ArticleActionsSheet
import dev.trove.app.ui.sheets.NewEntitySheet
import dev.trove.app.ui.sheets.SettingsSheet
import dev.trove.app.ui.theme.accentColor
import dev.trove.app.util.SnackbarBus
import dev.trove.app.util.daySection

@Composable
fun HomeScreen(
    onOpenArticle: (Long) -> Unit,
    onShareUrl: (String) -> Unit,
    pendingShareUrl: String?,
    onPendingShareHandled: () -> Unit,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
) {
    val vm: HomeViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // Backup export / import (SAF)
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::writeBackupTo) }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::readBackupFrom) }
    LaunchedEffect(vm.backupExportRequested) {
        if (vm.backupExportRequested) {
            backupExportLauncher.launch("pouch-backup.json")
            vm.onBackupExportHandled()
        }
    }
    LaunchedEffect(vm.backupImportRequested) {
        if (vm.backupImportRequested) {
            backupImportLauncher.launch(arrayOf("*/*"))
            vm.onBackupImportHandled()
        }
    }

    // Incoming share intent → pre-filled add sheet; the fetch starts
    // automatically so sharing into Trove just works.
    LaunchedEffect(pendingShareUrl) {
        if (!pendingShareUrl.isNullOrBlank()) {
            vm.showAddSheet(pendingShareUrl, startImmediately = true)
            onPendingShareHandled()
        }
    }

    // Navigate to a freshly added article
    LaunchedEffect(Unit) {
        vm.openArticleEvents.collect { onOpenArticle(it) }
    }

    // App shortcuts
    val app = context.applicationContext as dev.trove.app.TroveApplication
    LaunchedEffect(Unit) {
        app.pendingAddLink.collect { if (it) { vm.showAddSheet(); app.pendingAddLink.value = false } }
    }
    LaunchedEffect(Unit) {
        app.pendingRandomArticleId.collect { id ->
            if (id != null) {
                onOpenArticle(id)
                app.pendingRandomArticleId.value = null
            }
        }
    }
    LaunchedEffect(Unit) {
        app.pendingFetchFeeds.collect { if (it) { vm.fetchAllFeedsViaWorker(); app.pendingFetchFeeds.value = false } }
    }

    // Snackbars (shared bus, supports Undo actions)
    LaunchedEffect(Unit) {
        SnackbarBus.events.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) event.action()
            SnackbarBus.clear()
        }
    }

    BackHandler(
        enabled = state.searchActive || state.browsingFolderId != null ||
            state.browsingTagId != null || state.browsingFeedId != null
    ) {
        when {
            state.searchActive -> vm.setSearchActive(false)
            state.browsingFeedId != null -> vm.closeFeed()
            else -> vm.closeBrowsing()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!state.searchActive && state.browsingFolderId == null &&
                state.browsingTagId == null && state.browsingFeedId == null
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    NavigationBarItem(
                        selected = state.tab == 0,
                        onClick = { vm.selectTab(0) },
                        icon = { Icon(Icons.Rounded.RssFeed, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_feeds)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                    NavigationBarItem(
                        selected = state.tab == 1,
                        onClick = { vm.selectTab(1) },
                        icon = { Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_library)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                    NavigationBarItem(
                        selected = state.tab == 2,
                        onClick = { vm.selectTab(2) },
                        icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_folders)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                    NavigationBarItem(
                        selected = state.tab == 3,
                        onClick = { vm.selectTab(3) },
                        icon = { Icon(Icons.Rounded.Sell, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_tags)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.tab == 1 && !state.searchActive && state.browsingFolderId == null &&
                state.browsingTagId == null && state.browsingFeedId == null
            ) {
                FloatingActionButton(
                    onClick = { vm.showAddSheet() },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.lib_add_link))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.searchActive -> SearchOverlay(
                    query = state.searchQuery,
                    results = state.searchResults,
                    onQueryChange = vm::setSearchQuery,
                    onClose = { vm.setSearchActive(false) },
                    onOpenArticle = onOpenArticle,
                )
                state.browsingFolderId != null -> {
                    val folder = state.folders.firstOrNull { it.folder.id == state.browsingFolderId }
                    val subfolders = state.folders.filter { it.folder.parentId == state.browsingFolderId }
                    BrowseList(
                        title = folder?.folder?.name ?: stringResource(R.string.tab_folders),
                        subtitle = stringResource(R.string.lib_count_saved, state.articles.size),
                        articles = state.articles,
                        onBack = vm::closeBrowsing,
                        onOpenArticle = { onOpenArticle(it.article.id) },
                        onDelete = { vm.deleteArticle(it) },
                        onToggleFavorite = { vm.toggleFavorite(it) },
                        onActions = vm::openActions,
                        subfolders = subfolders,
                        onOpenSubfolder = vm::openFolder,
                        onEdit = folder?.let { { vm.showEditFolder(it) } },
                    )
                }
                state.browsingTagId != null -> {
                    val tag = state.tags.firstOrNull { it.tag.id == state.browsingTagId }
                    BrowseList(
                        title = "#${tag?.tag?.name ?: "Tag"}",
                        subtitle = stringResource(R.string.lib_count_saved, state.articles.size),
                        articles = state.articles,
                        onBack = vm::closeBrowsing,
                        onOpenArticle = { onOpenArticle(it.article.id) },
                        onDelete = { vm.deleteArticle(it) },
                        onToggleFavorite = { vm.toggleFavorite(it) },
                        onActions = vm::openActions,
                        onEdit = tag?.let { { vm.showEditTag(it) } },
                    )
                }
                state.tab == 0 -> FeedsTab(state, vm, onOpenArticle = onOpenArticle)
                state.tab == 1 -> InboxTab(state, vm, onOpenArticle = onOpenArticle)
                state.tab == 2 -> FoldersTab(state, vm)
                else -> TagsTab(state, vm)
            }
        }
    }

    // ----------------------------------------------------------------- sheets
    if (state.addSheetVisible) {
        AddUrlSheet(
            prefill = state.addSheetPrefill,
            state = state.addState,
            onTextChange = vm::setAddUrlText,
            onAdd = vm::addUrl,
            onDismiss = vm::hideAddSheet,
        )
    }

    state.actionsArticle?.let { article ->
        ArticleActionsSheet(
            article = article,
            folders = state.folders.map { it.folder },
            tags = state.tags.map { it.tag },
            onToggleRead = { vm.toggleRead(article) },
            onToggleFavorite = { vm.toggleFavorite(article) },
            onMoveToFolder = { vm.moveToFolder(article.article.id, it) },
            onToggleTag = { tagId ->
                val selected = article.tags.map { it.id }.toSet()
                vm.applyTags(
                    article.article.id,
                    if (tagId in selected) selected - tagId else selected + tagId,
                )
            },
            onNewFolder = vm::showNewFolder,
            onNewTag = vm::showNewTag,
            onRefresh = { vm.refresh(article) },
            onDownloadOffline = { vm.downloadOffline(article) },
            onShare = { onShareUrl(article.article.url) },
            onOpenInBrowser = {
                vm.closeActions()
                runCatching { uriHandler.openUri(article.article.url) }
            },
            onDelete = { vm.deleteArticle(article) },
            onDismiss = vm::closeActions,
        )
    }

    if (state.newFolderVisible) {
        NewEntitySheet(
            title = stringResource(R.string.folders_new),
            onCreate = { name, color, parentId ->
                val article = state.actionsArticle
                if (article != null) vm.createFolderAndApply(article.article.id, name, color)
                else vm.createFolder(name, color, parentId)
            },
            onDismiss = vm::hideNewFolder,
            folders = state.folders.map { it.folder },
            showParentPicker = true,
        )
    }
    if (state.newTagVisible) {
        NewEntitySheet(
            title = stringResource(R.string.tags_new),
            onCreate = { name, color, _ ->
                val article = state.actionsArticle
                if (article != null) vm.createTagAndApply(article.article.id, name, color)
                else vm.createTag(name, color)
            },
            onDismiss = vm::hideNewTag,
        )
    }
    state.editingFolder?.let { folder ->
        NewEntitySheet(
            title = stringResource(R.string.folders_edit),
            initialName = folder.folder.name,
            initialColorIndex = folder.folder.colorIndex,
            initialParentId = folder.folder.parentId,
            isEdit = true,
            onDelete = { vm.deleteFolderWithMessage(folder) },
            onCreate = { name, color, parentId ->
                vm.updateFolder(folder, name, color, parentId)
            },
            onDismiss = vm::hideEditFolder,
            folders = state.folders.map { it.folder }.filter { it.id != folder.folder.id },
            showParentPicker = true,
        )
    }
    state.editingTag?.let { tag ->
        NewEntitySheet(
            title = stringResource(R.string.tags_edit),
            initialName = tag.tag.name,
            initialColorIndex = tag.tag.colorIndex,
            isEdit = true,
            onDelete = { vm.deleteTagWithMessage(tag) },
            onCreate = { name, color, _ -> vm.updateTag(tag, name, color) },
            onDismiss = vm::hideEditTag,
        )
    }
    if (state.settingsVisible) {
        SettingsSheet(
            settings = state.settingsState,
            onThemeMode = vm::setThemeMode,
            onDynamicColor = vm::setDynamicColor,
            onOled = vm::setOled,
            onReaderFont = vm::setReaderFont,
            onFontSize = vm::setFontSizeScale,
            onLineHeight = vm::setLineHeightScale,
            onLetterSpacing = vm::setLetterSpacing,
            onWordSpacing = vm::setWordSpacing,
            onTextAlign = vm::setTextAlign,
            onFeedInterval = vm::setFeedInterval,
            onFeedRetention = vm::setFeedRetention,
            onSyncOnStart = vm::setSyncOnStart,
            onSyncOnlyWifi = vm::setSyncOnlyWifi,
            onSyncOnlyCharging = vm::setSyncOnlyCharging,
            onExportBackup = vm::exportBackupFile,
            onImportBackup = vm::importBackupFile,
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = vm::hideSettings,
        )
    }
}

// ---------------------------------------------------------------------------
// Inbox
// ---------------------------------------------------------------------------

@Composable
private fun InboxTab(state: HomeUiState, vm: HomeViewModel, onOpenArticle: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header — same style as Folders/Tags, with search + settings actions
        SectionHeader(
            title = stringResource(R.string.tab_library),
            count = state.articles.size,
            large = true,
            modifier = Modifier.padding(top = 16.dp),
            action = {
                var sortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { vm.setSearchActive(true) }) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.common_search))
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.lib_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (order) {
                                            SortOrder.NEWEST -> stringResource(R.string.lib_sort_newest)
                                            SortOrder.OLDEST -> stringResource(R.string.lib_sort_oldest)
                                            SortOrder.READING_TIME -> stringResource(R.string.lib_sort_longest)
                                        }
                                    )
                                },
                                onClick = {
                                    vm.setSortOrder(order)
                                    sortMenu = false
                                },
                                trailingIcon = {
                                    if (state.sortOrder == order) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = vm::showSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.common_settings))
                }
            },
        )

        // Filter chips
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_all),
                    selected = state.filter == ArticleFilter.ALL,
                    onClick = { vm.setFilter(ArticleFilter.ALL) },
                )
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_unread),
                    selected = state.filter == ArticleFilter.UNREAD,
                    onClick = { vm.setFilter(ArticleFilter.UNREAD) },
                )
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_done),
                    selected = state.filter == ArticleFilter.DONE,
                    onClick = { vm.setFilter(ArticleFilter.DONE) },
                )
            }
            item {
                FilterChipPill(
                    label = stringResource(R.string.lib_filter_favorites),
                    selected = state.filter == ArticleFilter.FAVORITES,
                    onClick = { vm.setFilter(ArticleFilter.FAVORITES) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.articles.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.CollectionsBookmark,
                title = when (state.filter) {
                    ArticleFilter.ALL -> stringResource(R.string.lib_empty_all_title)
                    ArticleFilter.UNREAD -> stringResource(R.string.lib_empty_unread_title)
                    ArticleFilter.FAVORITES -> stringResource(R.string.lib_empty_fav_title)
                    ArticleFilter.DONE -> stringResource(R.string.lib_empty_done_title)
                },
                subtitle = when (state.filter) {
                    ArticleFilter.ALL -> stringResource(R.string.lib_empty_all_subtitle)
                    ArticleFilter.UNREAD -> stringResource(R.string.lib_empty_unread_subtitle)
                    ArticleFilter.FAVORITES -> stringResource(R.string.lib_empty_fav_subtitle)
                    ArticleFilter.DONE -> stringResource(R.string.lib_empty_done_subtitle)
                },
                actionLabel = if (state.filter == ArticleFilter.ALL) stringResource(R.string.lib_save_link) else null,
                onAction = if (state.filter == ArticleFilter.ALL) ({ vm.showAddSheet() }) else null,
                modifier = Modifier.weight(1f),
            )
        } else {
            ArticleList(
                articles = state.articles,
                onOpenArticle = { onOpenArticle(it.article.id) },
                onDelete = { vm.deleteArticle(it) },
                onToggleFavorite = { vm.toggleFavorite(it) },
                onActions = vm::openActions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

data class FilterChip(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** Standard filter row — identical layout in every menu for smooth switching. */
@Composable
fun FilterChipRow(chips: List<FilterChip>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.label }) { chip ->
            FilterChipPill(
                label = chip.label,
                selected = chip.selected,
                onClick = chip.onClick,
            )
        }
    }
}

@Composable
fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = bg) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Shared article list with day grouping
// ---------------------------------------------------------------------------

@Composable
private fun ArticleList(
    articles: List<ArticleWithTags>,
    onOpenArticle: (ArticleWithTags) -> Unit,
    onDelete: (ArticleWithTags) -> Unit,
    onToggleFavorite: (ArticleWithTags) -> Unit,
    onActions: (ArticleWithTags) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var lastSection: String? = null
        articles.forEach { item ->
            val section = daySection(item.article.addedAt)
            if (section != lastSection) {
                lastSection = section
                item(key = "hdr-$section") {
                    SectionHeader(title = section, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(key = item.article.id) {
                ArticleCard(
                    article = item,
                    onClick = { onOpenArticle(item) },
                    onDelete = { onDelete(item) },
                    onToggleFavorite = { onToggleFavorite(item) },
                    onLongClick = { onActions(item) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Browsing (folder / tag contents)
// ---------------------------------------------------------------------------

@Composable
private fun BrowseList(
    title: String,
    subtitle: String,
    articles: List<ArticleWithTags>,
    onBack: () -> Unit,
    onOpenArticle: (ArticleWithTags) -> Unit,
    onDelete: (ArticleWithTags) -> Unit,
    onToggleFavorite: (ArticleWithTags) -> Unit,
    onActions: (ArticleWithTags) -> Unit,
    subfolders: List<dev.trove.app.data.db.FolderWithCount> = emptyList(),
    onOpenSubfolder: ((Long) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
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
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.common_edit))
                }
            }
        }
        if (subfolders.isNotEmpty() && onOpenSubfolder != null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(subfolders, key = { it.folder.id }) { sub ->
                    AccentChip(
                        text = sub.folder.name,
                        colorIndex = sub.folder.colorIndex,
                        selected = false,
                        onClick = { onOpenSubfolder(sub.folder.id) },
                        icon = Icons.Rounded.Folder,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (articles.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = stringResource(R.string.lib_browse_empty_title),
                subtitle = stringResource(R.string.lib_browse_empty_subtitle),
                modifier = Modifier.weight(1f),
            )
        } else {
            ArticleList(
                articles = articles,
                onOpenArticle = onOpenArticle,
                onDelete = onDelete,
                onToggleFavorite = onToggleFavorite,
                onActions = onActions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Folders tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldersTab(
    state: HomeUiState,
    vm: HomeViewModel,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = stringResource(R.string.tab_folders),
            count = state.folders.size,
            large = true,
            modifier = Modifier.padding(top = 16.dp),
            action = {
                IconButton(onClick = vm::showNewFolder) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.folders_new))
                }
            },
        )
        if (state.folders.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = stringResource(R.string.folders_empty_title),
                subtitle = stringResource(R.string.folders_empty_subtitle),
                actionLabel = stringResource(R.string.folders_create),
                onAction = vm::showNewFolder,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(state.folders, key = { it.folder.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { vm.openFolder(folder.folder.id) },
                        onLongClick = { vm.showEditFolder(folder) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(folder: FolderWithCount, onClick: () -> Unit, onLongClick: () -> Unit) {
    val accent = accentColor(folder.folder.colorIndex)
    ExpressiveCard(onClick = onClick, onLongClick = onLongClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.container,
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = accent.onContainer,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                folder.folder.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.lib_count_saved, folder.articleCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tags tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsTab(
    state: HomeUiState,
    vm: HomeViewModel,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = stringResource(R.string.tab_tags),
            count = state.tags.size,
            large = true,
            modifier = Modifier.padding(top = 16.dp),
            action = {
                IconButton(onClick = vm::showNewTag) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tags_new))
                }
            },
        )
        if (state.tags.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Sell,
                title = stringResource(R.string.tags_empty_title),
                subtitle = stringResource(R.string.tags_empty_subtitle),
                actionLabel = stringResource(R.string.tags_create),
                onAction = vm::showNewTag,
                modifier = Modifier.weight(1f),
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.tags.forEach { tag ->
                    AccentChip(
                        text = tag.tag.name,
                        colorIndex = tag.tag.colorIndex,
                        selected = false,
                        onClick = { vm.openTag(tag.tag.id) },
                        count = tag.articleCount,
                        large = true,
                        onLongClick = { vm.showEditTag(tag) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search overlay
// ---------------------------------------------------------------------------

@Composable
private fun SearchOverlay(
    query: String,
    results: List<ArticleWithTags>,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenArticle: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_back))
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.lib_search_hint),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)

        if (query.length < 2) {
            EmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.lib_search_empty_title),
                subtitle = stringResource(R.string.lib_search_empty_subtitle),
                modifier = Modifier.weight(1f),
            )
        } else if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.lib_no_matches),
                subtitle = stringResource(R.string.lib_no_matches_for, query),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results, key = { it.article.id }) { item ->
                    ArticleCard(
                        article = item,
                        onClick = { onOpenArticle(item.article.id) },
                        onDelete = {},
                        onToggleFavorite = {},
                        onLongClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
