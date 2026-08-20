package dev.trove.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.trove.app.data.ArticleRepository
import dev.trove.app.data.OfflineRetention
import dev.trove.app.data.ReaderAlign
import dev.trove.app.data.ReaderFont
import dev.trove.app.data.ReaderSettings
import dev.trove.app.data.SettingsRepository
import dev.trove.app.data.ThemeMode
import dev.trove.app.R
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.data.db.FolderEntity
import dev.trove.app.data.db.HighlightEntity
import dev.trove.app.data.db.TagEntity
import dev.trove.app.util.SnackbarBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val article: ArticleWithTags? = null,
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val settings: ReaderSettings = ReaderSettings(),
    val actionsSheetVisible: Boolean = false,
    val manageHighlightsVisible: Boolean = false,
    val fontSheetVisible: Boolean = false,
    val readerSettingsVisible: Boolean = false,
    val newFolderVisible: Boolean = false,
    val newTagVisible: Boolean = false,
    val refreshing: Boolean = false,
    val highlights: List<HighlightEntity> = emptyList(),
)

class ReaderViewModel(
    private val appContext: android.content.Context,
    private val repo: ArticleRepository,
    private val settingsRepo: SettingsRepository,
    articleId: Long,
) : ViewModel() {

    private val actionsSheetVisible = MutableStateFlow(false)
    private val manageHighlightsVisible = MutableStateFlow(false)
    private val fontSheetVisible = MutableStateFlow(false)
    private val readerSettingsVisible = MutableStateFlow(false)
    private val newFolderVisible = MutableStateFlow(false)
    private val newTagVisible = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private var saveJob: Job? = null

    val uiState: StateFlow<ReaderUiState> = combine(
        repo.observeArticle(articleId),
        repo.observeFolders(),
        repo.observeTags(),
        settingsRepo.settings,
        actionsSheetVisible,
        manageHighlightsVisible,
        fontSheetVisible,
        readerSettingsVisible,
        newFolderVisible,
        newTagVisible,
        refreshing,
        repo.observeHighlights(articleId),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ReaderUiState(
            article = values[0] as ArticleWithTags?,
            folders = (values[1] as List<dev.trove.app.data.db.FolderWithCount>).map { it.folder },
            tags = (values[2] as List<dev.trove.app.data.db.TagWithCount>).map { it.tag },
            settings = values[3] as ReaderSettings,
            actionsSheetVisible = values[4] as Boolean,
            manageHighlightsVisible = values[5] as Boolean,
            fontSheetVisible = values[6] as Boolean,
            readerSettingsVisible = values[7] as Boolean,
            newFolderVisible = values[8] as Boolean,
            newTagVisible = values[9] as Boolean,
            refreshing = values[10] as Boolean,
            highlights = values[11] as List<HighlightEntity>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderUiState())

    // ----------------------------------------------------------- positioning

    /** Debounced save of reading position while scrolling. */
    fun onScrolled(articleId: Long, index: Int, offset: Int, progress: Float) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            repo.savePosition(articleId, progress, index, offset)
        }
    }

    fun saveNow(articleId: Long, index: Int, offset: Int, progress: Float) {
        saveJob?.cancel()
        viewModelScope.launch {
            repo.savePosition(articleId, progress, index, offset)
        }
    }

    // ------------------------------------------------------------- actions

    fun toggleFavorite(articleId: Long, current: Boolean) {
        viewModelScope.launch {
            repo.ensureSaved(articleId)
            repo.setFavorite(articleId, !current)
        }
    }

    fun toggleRead(articleId: Long, current: Boolean) {
        viewModelScope.launch { repo.setRead(articleId, !current) }
    }

    fun moveToFolder(articleId: Long, folderId: Long?) {
        viewModelScope.launch {
            repo.ensureSaved(articleId)
            repo.setFolder(articleId, folderId)
        }
    }

    fun applyTags(articleId: Long, tagIds: Set<Long>) {
        viewModelScope.launch {
            repo.ensureSaved(articleId)
            repo.setArticleTags(articleId, tagIds)
        }
    }

    fun refresh(article: ArticleWithTags) {
        viewModelScope.launch {
            refreshing.value = true
            val result = repo.refreshContent(article.article)
            refreshing.value = false
            if (result.isFailure) {
                SnackbarBus.post(
                    SnackbarBus.Event("Couldn't refresh: ${result.exceptionOrNull()?.message?.take(80)}")
                )
            }
        }
    }

    fun deleteArticle(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.deleteArticle(article.article)
            hideActionsSheet()
            val tagIds = article.tags.map { it.id }
            SnackbarBus.post(
                SnackbarBus.Event(
                    appContext.getString(R.string.snack_article_deleted),
                    appContext.getString(R.string.common_undo)
                ) {
                    viewModelScope.launch { repo.restoreArticle(article.article, tagIds) }
                }
            )
        }
    }

    fun addHighlight(
        articleId: Long,
        text: String,
        colorIndex: Int,
        blockIndex: Int,
        startOffset: Int,
        endOffset: Int,
    ) {
        viewModelScope.launch {
            repo.addHighlight(articleId, text.trim(), colorIndex, blockIndex, startOffset, endOffset)
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_highlight_added)))
        }
    }

    fun removeHighlight(highlight: HighlightEntity) {
        viewModelScope.launch { repo.removeHighlight(highlight) }
    }

    fun saveToLibrary(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.saveFeedItem(article.article.id)
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_added_library)))
            hideActionsSheet()
        }
    }

    fun downloadOffline(article: ArticleWithTags) {
        viewModelScope.launch {
            val result = repo.downloadForOffline(article.article)
            val msg = result.fold(
                onSuccess = { n ->
                    if (n > 0) appContext.getString(R.string.snack_offline_saved, n)
                    else appContext.getString(R.string.snack_offline_text_only)
                },
                onFailure = { appContext.getString(R.string.snack_offline_failed) },
            )
            SnackbarBus.post(SnackbarBus.Event(msg))
        }
    }

    fun downloadAllOffline() {
        viewModelScope.launch {
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_offline_downloading)))
            val all = repo.getAllArticles()
            var done = 0
            all.forEach { article ->
                val r = repo.downloadForOffline(article)
                if (r.isSuccess && r.getOrNull()!! > 0) done++
            }
            SnackbarBus.post(
                SnackbarBus.Event("Offline downloads complete ($done of ${all.size} articles)")
            )
        }
    }

    fun setAutoOffline(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoOffline(enabled) }
    }

    fun setOfflineRetention(retention: OfflineRetention) {
        viewModelScope.launch {
            settingsRepo.setOfflineRetention(retention)
            if (retention != OfflineRetention.ALWAYS) {
                val removed = repo.cleanupOfflineDownloads(retention)
                if (removed > 0) {
                    SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_offline_expired, removed)))
                }
            }
        }
    }

    fun showActionsSheet() {
        actionsSheetVisible.value = true
    }

    fun showManageHighlights() {
        actionsSheetVisible.value = false
        manageHighlightsVisible.value = true
    }

    fun hideManageHighlights() {
        manageHighlightsVisible.value = false
    }

    fun hideActionsSheet() {
        actionsSheetVisible.value = false
    }

    fun showNewFolder() {
        actionsSheetVisible.value = false
        newFolderVisible.value = true
    }

    fun hideNewFolder() {
        newFolderVisible.value = false
    }

    /** Creates a folder and files the article into it. */
    fun createFolderAndApply(articleId: Long, name: String, colorIndex: Int) {
        viewModelScope.launch {
            val id = repo.createFolder(name.trim(), colorIndex)
            repo.setFolder(articleId, id)
            newFolderVisible.value = false
        }
    }

    fun showNewTag() {
        actionsSheetVisible.value = false
        newTagVisible.value = true
    }

    fun hideNewTag() {
        newTagVisible.value = false
    }

    /** Creates a tag and applies it to the article. */
    fun createTagAndApply(articleId: Long, name: String, colorIndex: Int) {
        viewModelScope.launch {
            val id = repo.createTag(name.trim(), colorIndex)
            val current = repo.getArticleTags(articleId).map { it.id }.toMutableSet()
            current += id
            repo.setArticleTags(articleId, current)
            newTagVisible.value = false
        }
    }

    fun showFontSheet() {
        fontSheetVisible.value = true
    }

    fun hideFontSheet() {
        fontSheetVisible.value = false
    }

    fun showReaderSettings() {
        readerSettingsVisible.value = true
    }

    fun hideReaderSettings() {
        readerSettingsVisible.value = false
    }

    // ------------------------------------------------------ theme settings

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun setOled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setOled(enabled) }
    }

    // ------------------------------------------------------------ settings

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch { settingsRepo.setFontSizeScale(scale) }
    }

    fun setLineHeightScale(scale: Float) {
        viewModelScope.launch { settingsRepo.setLineHeightScale(scale) }
    }

    fun setReaderFont(font: ReaderFont) {
        viewModelScope.launch { settingsRepo.setReaderFont(font) }
    }

    fun setLetterSpacing(sp: Float) {
        viewModelScope.launch { settingsRepo.setLetterSpacing(sp) }
    }

    fun setWordSpacing(scale: Float) {
        viewModelScope.launch { settingsRepo.setWordSpacing(scale) }
    }

    fun setTextAlign(align: ReaderAlign) {
        viewModelScope.launch { settingsRepo.setTextAlign(align) }
    }
}
