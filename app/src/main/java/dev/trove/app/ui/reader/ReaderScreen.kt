package dev.trove.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import dev.trove.app.ui.reader.HighlightRange
import dev.trove.app.ui.reader.HighlightPalette
import dev.trove.app.ui.components.EmptyState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.trove.app.BuildConfig
import dev.trove.app.R
import dev.trove.app.data.db.HighlightEntity
import dev.trove.app.data.FeedFetchInterval
import dev.trove.app.data.OfflineRetention
import dev.trove.app.ui.components.ExpressiveButton
import dev.trove.app.ui.sheets.ArticleActionsSheet
import dev.trove.app.ui.sheets.NewEntitySheet
import dev.trove.app.ui.sheets.SettingsSheet
import dev.trove.app.ui.theme.ExpressiveSprings
import dev.trove.app.util.LocalImagesJson
import dev.trove.app.util.SnackbarBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    articleId: Long,
    onBack: () -> Unit,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
) {
    val vm: ReaderViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val article = state.article
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val blocks = remember(article?.article?.contentHtml) {
        ArticleHtmlParser.parse(article?.article?.contentHtml)
    }
    val localImages = remember(article?.article?.localImages) {
        LocalImagesJson.decode(article?.article?.localImages)
    }
    val blockHighlights = remember(state.highlights) {
        val byBlock = mutableMapOf<Int, MutableList<HighlightRange>>()
        state.highlights.forEach { h ->
            byBlock.getOrPut(h.blockIndex) { mutableListOf() }
                .add(HighlightRange(h.startOffset, h.endOffset, h.colorIndex, h.text))
        }
        byBlock
    }
    val style = rememberReaderTextStyle(
        readerFont = state.settings.readerFont,
        fontSizeScale = state.settings.fontSizeScale,
        lineHeightScale = state.settings.lineHeightScale,
        letterSpacing = state.settings.letterSpacing,
        wordSpacing = state.settings.wordSpacing,
        textAlign = state.settings.textAlign,
    )
    val listState = rememberLazyListState()
    val itemCount = if (article == null) 0 else 1 + blocks.size + 1

    // Restore reading position once content is available.
    LaunchedEffect(article?.article?.id, itemCount) {
        val a = article?.article ?: return@LaunchedEffect
        if (a.scrollIndex in 0 until itemCount) {
            listState.scrollToItem(a.scrollIndex, a.scrollOffset)
        }
    }

    // Persist scroll position (debounced).
    LaunchedEffect(article?.article?.id) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .debounce(400)
            .collect { (index, offset) ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && article?.article != null) {
                    val progress = ((index + 1).toFloat() / total).coerceIn(0f, 1f)
                    vm.onScrolled(article.article.id, index, offset, progress)
                }
            }
    }

    // Final position flush on leaving the screen.
    DisposableEffect(article?.article?.id) {
        onDispose {
            val a = article?.article ?: return@onDispose
            val total = listState.layoutInfo.totalItemsCount
            val progress = if (total > 0) {
                ((listState.firstVisibleItemIndex + 1).toFloat() / total).coerceIn(0f, 1f)
            } else 0f
            vm.saveNow(
                a.id,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                progress,
            )
        }
    }

    // Top bar collapses when scrolling down; reappears when scrolling up.
    // A cumulative threshold keeps it responsive but not twitchy.
    var topBarVisible by remember { mutableStateOf(true) }
    LaunchedEffect(article?.article?.id) {
        topBarVisible = true
        var prev = 0L
        var accumulated = 0L
        snapshotFlow {
            listState.firstVisibleItemIndex.toLong() * 10000L +
                listState.firstVisibleItemScrollOffset
        }.collect { pos ->
            val atTop = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
            val delta = pos - prev
            prev = pos
            if (atTop) {
                topBarVisible = true
                accumulated = 0
            } else {
                accumulated += delta
                if (accumulated > SCROLL_HIDE_THRESHOLD) {
                    topBarVisible = false
                    accumulated = 0
                } else if (accumulated < -SCROLL_HIDE_THRESHOLD) {
                    topBarVisible = true
                    accumulated = 0
                }
            }
        }
    }

    // Feed items (and content-less articles) auto-fetch their full text
    // once on open — never retried after a failure.
    val autoFetchedIds = remember { mutableStateListOf<Long>() }
    LaunchedEffect(article?.article?.id, state.refreshing) {
        val a = article?.article ?: return@LaunchedEffect
        if ((!a.fullContent || blocks.isEmpty()) && !state.refreshing && a.id !in autoFetchedIds) {
            autoFetchedIds += a.id
            vm.refresh(article)
        }
    }

    // Content slides up under the collapsed bar for an immersive feel.
    val topContentPadding by animateDpAsState(
        targetValue = if (topBarVisible) 132.dp else 56.dp,
        animationSpec = ExpressiveSprings.defaultSpatial(),
        label = "topContentPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {            article == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            blocks.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        article.article.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.reader_no_content_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    ExpressiveButton(
                        onClick = { vm.refresh(article) },
                        text = if (state.refreshing) stringResource(R.string.reader_refreshing) else stringResource(R.string.reader_fetch_content),
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topContentPadding,
                        bottom = 72.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "header") {
                        ArticleHeader(article.article, style, localImages = localImages)
                    }
                    items(blocks.size, key = { "b$it" }) { i ->
                        ArticleBlockView(
                            block = blocks[i],
                            style = style,
                            onImageClick = { url -> runCatching { uriHandler.openUri(url) } },
                            localImages = localImages,
                            blockIndex = i,
                            highlightRanges = blockHighlights[i].orEmpty(),
                            onHighlight = { bi, st, en, text, color ->
                                vm.addHighlight(article.article.id, text, color, bi, st, en)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "end") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.reader_end),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- top bar
        if (article != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Solid scrim over the status bar — always visible so system
                // icons never sit on top of article text.
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                )
                AnimatedVisibility(
                    visible = topBarVisible,
                    enter = slideInVertically(
                        animationSpec = ExpressiveSprings.defaultSpatial<androidx.compose.ui.unit.IntOffset>()
                    ) { -it } + fadeIn(),
                    exit = slideOutVertically(
                        animationSpec = ExpressiveSprings.defaultSpatial<androidx.compose.ui.unit.IntOffset>()
                    ) { -it } + fadeOut(),
                ) {
                    // The background travels with the buttons, so there's no
                    // lingering surface band once they slide away.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        Spacer(Modifier.weight(1f))
                        RoundIconButton(
                            onClick = {
                                vm.setFontSizeScale(
                                    (state.settings.fontSizeScale - 0.1f).coerceAtLeast(0.8f)
                                )
                            },
                        ) {
                            Icon(Icons.Rounded.TextDecrease, contentDescription = stringResource(R.string.reader_smaller_text))
                        }
                        RoundIconButton(
                            onClick = {
                                vm.setFontSizeScale(
                                    (state.settings.fontSizeScale + 0.1f).coerceAtMost(1.5f)
                                )
                            },
                        ) {
                            Icon(Icons.Rounded.TextIncrease, contentDescription = stringResource(R.string.reader_larger_text))
                        }
                        RoundIconButton(
                            onClick = { vm.toggleFavorite(article.article.id, article.article.isFavorite) },
                        ) {
                            Icon(
                                if (article.article.isFavorite) Icons.Rounded.Bookmark
                                else Icons.Rounded.BookmarkBorder,
                                contentDescription = stringResource(R.string.lib_swipe_favorite),
                                tint = if (article.article.isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        RoundIconButton(onClick = vm::showActionsSheet) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    // -------------------------------------------------------------- sheets
    if (state.actionsSheetVisible && article != null) {
        ArticleActionsSheet(
            article = article,
            folders = state.folders,
            tags = state.tags,
            isSaved = article.article.saved,
            onSaveToLibrary = { vm.saveToLibrary(article) },
            onToggleRead = { vm.toggleRead(article.article.id, article.article.isRead) },
            onToggleFavorite = { vm.toggleFavorite(article.article.id, article.article.isFavorite) },
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
            onShare = {
                scope.launch {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, article.article.url)
                    }
                    vm.hideActionsSheet()
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(intent, context.getString(R.string.reader_share_link))
                        )
                    }
                }
            },
            onOpenInBrowser = {
                vm.hideActionsSheet()
                runCatching { uriHandler.openUri(article.article.url) }
            },
            onDownloadOffline = { vm.downloadOffline(article) },
            onDelete = {
                scope.launch {
                    vm.deleteArticle(article)
                    onBack()
                }
            },
            onDismiss = vm::hideActionsSheet,
        )
    }

    if (state.manageHighlightsVisible) {
        HighlightsSheet(
            highlights = state.highlights,
            onDelete = vm::removeHighlight,
            onDismiss = vm::hideManageHighlights,
        )
    }
    if (state.newFolderVisible && article != null) {
        NewEntitySheet(
            title = stringResource(R.string.folders_new),
            onCreate = { name, color, _ -> vm.createFolderAndApply(article.article.id, name, color) },
            onDismiss = vm::hideNewFolder,
        )
    }
    if (state.newTagVisible && article != null) {
        NewEntitySheet(
            title = stringResource(R.string.tags_new),
            onCreate = { name, color, _ -> vm.createTagAndApply(article.article.id, name, color) },
            onDismiss = vm::hideNewTag,
        )
    }
    if (state.readerSettingsVisible) {
        SettingsSheet(
            settings = state.settings,
            onThemeMode = vm::setThemeMode,
            onDynamicColor = vm::setDynamicColor,
            onOled = vm::setOled,
            onReaderFont = vm::setReaderFont,
            onFontSize = vm::setFontSizeScale,
            onLineHeight = vm::setLineHeightScale,
            onLetterSpacing = vm::setLetterSpacing,
            onWordSpacing = vm::setWordSpacing,
            onTextAlign = vm::setTextAlign,
            onFeedInterval = {},
            onFeedRetention = {},
            onSyncOnStart = {},
            onSyncOnlyWifi = {},
            onSyncOnlyCharging = {},
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = vm::hideReaderSettings,
        )
    }
}

@Composable
private fun RoundIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private const val SCROLL_HIDE_THRESHOLD = 80L

/** Collapses whitespace so stored highlight text matches block text. */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HighlightsSheet(
    highlights: List<dev.trove.app.data.db.HighlightEntity>,
    onDelete: (dev.trove.app.data.db.HighlightEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (highlights.isEmpty()) stringResource(R.string.reader_no_highlights_title) else stringResource(R.string.reader_highlights_count, highlights.size),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.reader_no_highlights_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (highlights.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.FormatColorFill,
                    title = stringResource(R.string.reader_nothing_highlighted),
                    subtitle = stringResource(R.string.reader_highlight_hint),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(highlights, key = { it.id }) { highlight ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = HighlightPalette[highlight.colorIndex % HighlightPalette.size],
                                modifier = Modifier.size(22.dp),
                            ) {}
                            Spacer(Modifier.width(12.dp))
                            Text(
                                highlight.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDelete(highlight) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.reader_remove_highlight),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
