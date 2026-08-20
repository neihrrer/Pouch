package dev.trove.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.trove.app.data.FeedFetchInterval
import dev.trove.app.data.OfflineRetention
import dev.trove.app.R
import dev.trove.app.data.ReaderAlign
import dev.trove.app.data.ReaderFont
import dev.trove.app.data.ReaderSettings
import dev.trove.app.data.ThemeMode
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.data.db.FolderEntity
import dev.trove.app.data.db.TagEntity
import dev.trove.app.ui.components.AccentChip
import dev.trove.app.ui.components.ExpressiveButton
import dev.trove.app.ui.home.AddState
import dev.trove.app.ui.theme.AccentColorCount
import dev.trove.app.ui.theme.accentColor

// ---------------------------------------------------------------------------
// Add URL sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUrlSheet(
    prefill: String,
    state: AddState,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val clipboard = LocalClipboardManager.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.sheet_save_link_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.sheet_save_link_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = prefill,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.sheet_url_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let(onTextChange)
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.common_paste))
                    }
                },
                shape = RoundedCornerShape(50),
                singleLine = true,
            )
            when (state) {
                is AddState.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {}
            }
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(
                onClick = onAdd,
                text = if (state is AddState.Working) stringResource(R.string.sheet_fetching) else stringResource(R.string.sheet_save_article),
                icon = Icons.Rounded.AddLink,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primary,
            )
            if (state is AddState.Working) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
@Composable
private fun SettingsNavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Article actions sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleActionsSheet(
    article: ArticleWithTags,
    folders: List<FolderEntity>,
    tags: List<TagEntity>,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToFolder: (Long?) -> Unit,
    onToggleTag: (Long) -> Unit,
    onNewFolder: () -> Unit,
    onNewTag: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadOffline: () -> Unit,
    onManageHighlights: (() -> Unit)? = null,
    highlightCount: Int = 0,
    onShare: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    isSaved: Boolean = true,
    onSaveToLibrary: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val a = article.article
        var selectedFolderId by remember { mutableStateOf(a.folderId) }
        var selectedTagIds by remember { mutableStateOf(article.tags.map { it.id }.toSet()) }
        androidx.compose.runtime.LaunchedEffect(a.folderId) { if (selectedFolderId != a.folderId) selectedFolderId = a.folderId }
        androidx.compose.runtime.LaunchedEffect(article.tags) { val external = article.tags.map { it.id }.toSet(); if (selectedTagIds != external) selectedTagIds = external }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                a.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                a.siteName ?: a.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Text("Folder", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentChip(
                    text = stringResource(R.string.sheet_no_folder),
                    colorIndex = 0,
                    selected = selectedFolderId == null,
                    onClick = { selectedFolderId = null; onMoveToFolder(null) },
                    icon = Icons.Rounded.FolderOpen,
                )
                folders.forEach { f ->
                    AccentChip(
                        text = f.name,
                        colorIndex = f.colorIndex,
                        selected = selectedFolderId == f.id,
                        onClick = { selectedFolderId = f.id; onMoveToFolder(f.id) },
                        icon = Icons.Rounded.Folder,
                    )
                }
                AccentChip(
                    text = stringResource(R.string.common_create),
                    colorIndex = 4,
                    selected = false,
                    onClick = onNewFolder,
                    icon = Icons.Rounded.Add,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Tags", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { t ->
                    AccentChip(
                        text = t.name,
                        colorIndex = t.colorIndex,
                        selected = t.id in selectedTagIds,
                        onClick = {
                            selectedTagIds = if (t.id in selectedTagIds) selectedTagIds - t.id else selectedTagIds + t.id
                            onToggleTag(t.id)
                        },
                    )
                }
                AccentChip(
                    text = stringResource(R.string.common_create),
                    colorIndex = 5,
                    selected = false,
                    onClick = onNewTag,
                    icon = Icons.Rounded.Add,
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Spacer(Modifier.height(8.dp))

            if (!isSaved && onSaveToLibrary != null) {
                ActionRow(
                    icon = Icons.Rounded.BookmarkAdd,
                    label = stringResource(R.string.sheet_add_to_library),
                    onClick = onSaveToLibrary,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            ActionRow(
                icon = if (a.isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                label = if (a.isRead) stringResource(R.string.sheet_mark_unread) else stringResource(R.string.sheet_mark_read),
                onClick = onToggleRead,
            )
            ActionRow(
                icon = if (a.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = if (a.isFavorite) stringResource(R.string.sheet_remove_favorite) else stringResource(R.string.sheet_add_to_favorites),
                onClick = onToggleFavorite,
            )
            ActionRow(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.sheet_refresh_content),
                onClick = onRefresh,
            )
            ActionRow(
                icon = if (a.offlineReady) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                label = if (a.offlineReady) stringResource(R.string.sheet_offline_saved) else stringResource(R.string.sheet_download_offline),
                onClick = onDownloadOffline,
            )
            if (onManageHighlights != null) {
                ActionRow(
                    icon = Icons.Rounded.FormatColorFill,
                    label = if (highlightCount > 0) stringResource(R.string.reader_highlights_count, highlightCount) else stringResource(R.string.reader_highlights),
                    onClick = onManageHighlights,
                )
            }
            ActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.reader_share_link),
                onClick = onShare,
            )
            ActionRow(
                icon = Icons.Rounded.OpenInBrowser,
                label = stringResource(R.string.sheet_open_browser),
                onClick = onOpenInBrowser,
            )
            ActionRow(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.sheet_delete_article),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}

// ---------------------------------------------------------------------------
// New / edit folder, tag or category sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewEntitySheet(
    title: String,
    onCreate: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialColorIndex: Int = 0,
    isEdit: Boolean = false,
    onDelete: (() -> Unit)? = null,
    showColorPicker: Boolean = true,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var colorIndex by rememberSaveable { mutableStateOf(initialColorIndex) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.common_name)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                shape = RoundedCornerShape(50),
                singleLine = true,
            )

            if (showColorPicker) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.common_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0 until AccentColorCount).forEach { i ->
                        val accent = accentColor(i)
                        val selected = colorIndex == i
                        Surface(
                            onClick = { colorIndex = i },
                            shape = RoundedCornerShape(50),
                            color = if (selected) accent.main else accent.container,
                            border = if (selected) null
                            else BorderStroke(2.dp, accent.main.copy(alpha = 0.7f)),
                            modifier = Modifier.size(if (selected) 36.dp else 32.dp),
                        ) {
                            if (selected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Done,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            ExpressiveButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), colorIndex) },
                text = if (isEdit) stringResource(R.string.common_save_changes) else stringResource(R.string.common_create),
                icon = if (isEdit) Icons.Rounded.Done else Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isEdit && onDelete != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: ReaderSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onOled: (Boolean) -> Unit,
    onReaderFont: (ReaderFont) -> Unit,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onLetterSpacing: (Float) -> Unit,
    onWordSpacing: (Float) -> Unit,
    onTextAlign: (ReaderAlign) -> Unit,
    onFeedInterval: (FeedFetchInterval) -> Unit,
    onFeedRetention: (OfflineRetention) -> Unit,
    onSyncOnStart: (Boolean) -> Unit,
    onSyncOnlyWifi: (Boolean) -> Unit,
    onSyncOnlyCharging: (Boolean) -> Unit,
    onExportBackup: (() -> Unit)? = null,
    onImportBackup: (() -> Unit)? = null,
    versionName: String = "",
    onDismiss: () -> Unit,
) {
    var section by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Sliders drag on local state, persisted only when the gesture ends.
    var fontSize by remember(settings.fontSizeScale) { mutableStateOf(settings.fontSizeScale) }
    var lineHeight by remember(settings.lineHeightScale) { mutableStateOf(settings.lineHeightScale) }
    var letterSpacing by remember(settings.letterSpacing) { mutableStateOf(settings.letterSpacing) }
    var wordSpacing by remember(settings.wordSpacing) { mutableStateOf(settings.wordSpacing) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (section == null) {
                // ------------------------------------------------- overview
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                SettingsNavRow(stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_subtitle), Icons.Rounded.Palette) { section = "appearance" }
                SettingsNavRow(stringResource(R.string.settings_reading), stringResource(R.string.settings_reading_subtitle), Icons.Rounded.TextFields) { section = "reading" }
                SettingsNavRow(stringResource(R.string.settings_feeds), stringResource(R.string.settings_feeds_subtitle), Icons.Rounded.RssFeed) { section = "feeds" }
                if (onExportBackup != null || onImportBackup != null) {
                    SettingsNavRow(stringResource(R.string.settings_data), stringResource(R.string.settings_data_subtitle), Icons.Rounded.Save) { section = "data" }
                }
                SettingsNavRow(stringResource(R.string.settings_about), versionName.ifBlank { stringResource(R.string.app_name) }, Icons.Rounded.Info) { section = "about" }
            } else {
                // ------------------------------------------------- section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { section = null }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        when (section) {
                            "appearance" -> stringResource(R.string.settings_appearance)
                            "reading" -> stringResource(R.string.settings_reading)
                            "feeds" -> stringResource(R.string.settings_feeds)
                            "data" -> stringResource(R.string.settings_data)
                            else -> stringResource(R.string.settings_about)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Spacer(Modifier.height(12.dp))

                when (section) {
                    "appearance" -> {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                                ) {
                                    Text(themeModeLabel(mode))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SettingRow(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                            trailing = { Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColor) },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_oled),
                            subtitle = stringResource(R.string.settings_oled_subtitle),
                            trailing = { Switch(checked = settings.oled, onCheckedChange = onOled) },
                        )
                    }

                    "reading" -> {
                        Text(
                            stringResource(R.string.settings_reading_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_typeface), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReaderFont.entries.forEach { font ->
                                val selected = settings.readerFont == font
                                AccentChip(
                                    text = readerFontLabel(font),
                                    colorIndex = font.ordinal + 2,
                                    selected = selected,
                                    onClick = { onReaderFont(font) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_alignment), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReaderAlign.entries.forEach { align ->
                                val selected = settings.textAlign == align
                                AccentChip(
                                    text = readerAlignLabel(align),
                                    colorIndex = 0,
                                    selected = selected,
                                    onClick = { onTextAlign(align) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.labelLarge)
                        Slider(value = fontSize, onValueChange = { fontSize = it },
                            onValueChangeFinished = { onFontSize(fontSize) }, valueRange = 0.8f..1.5f, steps = 7)
                        Text(stringResource(R.string.settings_line_spacing), style = MaterialTheme.typography.labelLarge)
                        Slider(value = lineHeight, onValueChange = { lineHeight = it },
                            onValueChangeFinished = { onLineHeight(lineHeight) }, valueRange = 0.8f..1.6f, steps = 7)
                        Text(stringResource(R.string.settings_letter_spacing), style = MaterialTheme.typography.labelLarge)
                        Slider(value = letterSpacing, onValueChange = { letterSpacing = it },
                            onValueChangeFinished = { onLetterSpacing(letterSpacing) }, valueRange = 0f..2f, steps = 7)
                        Text(stringResource(R.string.settings_word_spacing), style = MaterialTheme.typography.labelLarge)
                        Slider(value = wordSpacing, onValueChange = { wordSpacing = it },
                            onValueChangeFinished = { onWordSpacing(wordSpacing) }, valueRange = 0f..1.5f, steps = 7)
                    }

                    "feeds" -> {
                        Text(stringResource(R.string.settings_feed_interval), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeedFetchInterval.entries.forEach { interval ->
                                val selected = settings.feedInterval == interval
                                AccentChip(
                                    text = intervalLabel(interval),
                                    colorIndex = 1,
                                    selected = selected,
                                    onClick = { onFeedInterval(interval) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_feed_retention), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OfflineRetention.entries.forEach { retention ->
                                val selected = settings.feedRetention == retention
                                AccentChip(
                                    text = retentionLabel(retention),
                                    colorIndex = 2,
                                    selected = selected,
                                    onClick = { onFeedRetention(retention) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SettingRow(
                            title = stringResource(R.string.settings_sync_on_start),
                            subtitle = stringResource(R.string.settings_sync_on_start_subtitle),
                            trailing = { Switch(checked = settings.syncOnStart, onCheckedChange = onSyncOnStart) },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_wifi_only),
                            subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                            trailing = { Switch(checked = settings.syncOnlyWifi, onCheckedChange = onSyncOnlyWifi) },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_charging_only),
                            subtitle = stringResource(R.string.settings_charging_only_subtitle),
                            trailing = { Switch(checked = settings.syncOnlyCharging, onCheckedChange = onSyncOnlyCharging) },
                        )
                    }

                    "data" -> {
                        if (onExportBackup != null) {
                            ExpressiveButton(
                                onClick = onExportBackup,
                                text = stringResource(R.string.settings_export_data),
                                icon = Icons.Rounded.FileUpload,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (onImportBackup != null) {
                            ExpressiveButton(
                                onClick = onImportBackup,
                                text = stringResource(R.string.settings_import_data),
                                icon = Icons.Rounded.FileDownload,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_backup_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.settings_version, versionName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_about_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_license),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_attributions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { uriHandler.openUri("https://github.com/neihrrer/Pouch") }) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("github.com/neihrrer/Pouch")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}


@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.mode_system
        ThemeMode.LIGHT -> R.string.mode_light
        ThemeMode.DARK -> R.string.mode_dark
        ThemeMode.SEPIA -> R.string.mode_sepia
    }
)

@Composable
private fun readerFontLabel(font: ReaderFont): String = stringResource(
    when (font) {
        ReaderFont.SANS -> R.string.font_sans
        ReaderFont.SERIF -> R.string.font_serif
        ReaderFont.OPENDYSLEXIC -> R.string.font_opendyslexic
        ReaderFont.MONOSPACE -> R.string.font_monospace
    }
)

@Composable
private fun readerAlignLabel(align: ReaderAlign): String = stringResource(
    when (align) {
        ReaderAlign.LEFT -> R.string.align_left
        ReaderAlign.JUSTIFIED -> R.string.align_justified
        ReaderAlign.CENTER -> R.string.align_center
    }
)

@Composable
private fun retentionLabel(retention: OfflineRetention): String = stringResource(
    when (retention) {
        OfflineRetention.WEEK -> R.string.retention_week
        OfflineRetention.MONTH -> R.string.retention_month
        OfflineRetention.THREE_MONTHS -> R.string.retention_three_months
        OfflineRetention.ALWAYS -> R.string.retention_always
    }
)

@Composable
private fun intervalLabel(interval: FeedFetchInterval): String = stringResource(
    when (interval) {
        FeedFetchInterval.OFF -> R.string.interval_off
        FeedFetchInterval.FIFTEEN -> R.string.interval_15
        FeedFetchInterval.THIRTY -> R.string.interval_30
        FeedFetchInterval.HOURLY -> R.string.interval_1h
        FeedFetchInterval.THREE_HOURS -> R.string.interval_3h
        FeedFetchInterval.SIX_HOURS -> R.string.interval_6h
        FeedFetchInterval.TWELVE_HOURS -> R.string.interval_12h
        FeedFetchInterval.DAILY -> R.string.interval_24h
    }
)
