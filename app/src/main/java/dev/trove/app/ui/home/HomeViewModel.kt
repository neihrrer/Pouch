package dev.trove.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.WorkManager
import dev.trove.app.data.ArticleRepository
import dev.trove.app.data.AddUrlResult
import dev.trove.app.data.BackupRepository
import dev.trove.app.data.FeedFetchInterval
import dev.trove.app.data.FeedRepository
import dev.trove.app.data.FeedSyncWorker
import dev.trove.app.data.SyncScheduler
import dev.trove.app.data.OfflineRetention
import dev.trove.app.data.ReaderAlign
import dev.trove.app.data.ReaderFont
import dev.trove.app.data.ReaderSettings
import dev.trove.app.data.SettingsRepository
import dev.trove.app.data.ThemeMode
import dev.trove.app.R
import dev.trove.app.data.db.ArticleWithTags
import dev.trove.app.data.db.FeedCategoryEntity
import dev.trove.app.data.db.FeedWithCount
import dev.trove.app.data.db.FolderWithCount
import dev.trove.app.data.db.TagWithCount
import dev.trove.app.util.SnackbarBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ArticleFilter { ALL, UNREAD, FAVORITES, DONE }

enum class SortOrder { NEWEST, OLDEST, READING_TIME }

sealed interface AddState {
    data object Idle : AddState
    data object Working : AddState
    data class Error(val message: String) : AddState
    data class Done(val articleId: Long) : AddState
}

data class HomeUiState(
    val articles: List<ArticleWithTags> = emptyList(),
    val unreadCount: Int = 0,
    val folders: List<FolderWithCount> = emptyList(),
    val tags: List<TagWithCount> = emptyList(),
    val settingsState: ReaderSettings = ReaderSettings(),
    val tab: Int = 0,
    val filter: ArticleFilter = ArticleFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val searchQuery: String = "",
    val searchActive: Boolean = false,
    val searchResults: List<ArticleWithTags> = emptyList(),
    val browsingFolderId: Long? = null,
    val browsingTagId: Long? = null,
    val browsingFeedId: Long? = null,
    val actionsArticle: ArticleWithTags? = null,
    val addSheetVisible: Boolean = false,
    val addSheetPrefill: String = "",
    val addState: AddState = AddState.Idle,
    val settingsVisible: Boolean = false,
    val newFolderVisible: Boolean = false,
    val newTagVisible: Boolean = false,
    val editingFolder: FolderWithCount? = null,
    val editingTag: TagWithCount? = null,
    // ---- feeds ----
    val feeds: List<FeedWithCount> = emptyList(),
    val feedCategories: List<FeedCategoryEntity> = emptyList(),
    val selectedFeedCategoryId: Long? = null,
    val showAddFeedSheet: Boolean = false,
    val editingFeed: FeedWithCount? = null,
    val showNewCategory: Boolean = false,
    val syncActive: Boolean = false,
    val snackbar: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val appContext: android.content.Context,
    private val repo: ArticleRepository,
    private val settingsRepo: SettingsRepository,
    private val feedRepo: FeedRepository,
    private val backupRepo: BackupRepository,
) : ViewModel() {

    companion object {
        /** Sentinel feed id: "All feeds" combined view. */
        const val ALL_FEEDS = -1L
    }

    private val tab = MutableStateFlow(0)
    private val filter = MutableStateFlow(ArticleFilter.ALL)
    private val sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val browsingFolder = MutableStateFlow<Long?>(null)
    private val browsingTag = MutableStateFlow<Long?>(null)
    private val browsingFeed = MutableStateFlow<Long?>(null)
    private val searchQuery = MutableStateFlow("")
    private val searchActive = MutableStateFlow(false)
    private val actionsArticle = MutableStateFlow<ArticleWithTags?>(null)
    private val addSheetVisible = MutableStateFlow(false)
    private val addSheetPrefill = MutableStateFlow("")
    private val addState = MutableStateFlow<AddState>(AddState.Idle)
    private val settingsVisible = MutableStateFlow(false)
    private val newFolderVisible = MutableStateFlow(false)
    private val newTagVisible = MutableStateFlow(false)
    private val editingFolder = MutableStateFlow<FolderWithCount?>(null)
    private val editingTag = MutableStateFlow<TagWithCount?>(null)
    private val selectedFeedCategoryId = MutableStateFlow<Long?>(null)
    private val showAddFeedSheet = MutableStateFlow(false)
    private val editingFeed = MutableStateFlow<FeedWithCount?>(null)
    private val showNewCategory = MutableStateFlow(false)
    val openArticleEvents = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    private val folderArticles = browsingFolder.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeFolder(id)
    }
    private val tagArticles = browsingTag.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeTag(id)
    }
    private val feedArticles = browsingFeed.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeByFeed(id)
    }

    private val allFeedArticles = repo.observeAllFeedItems()

    private val baseArticles = combine(
        repo.observeInbox(),
        folderArticles,
        tagArticles,
        feedArticles,
        allFeedArticles,
    ) { inbox, folderList, tagList, feedList, allList ->
        when {
            browsingFeed.value == ALL_FEEDS -> allList
            browsingFeed.value != null -> feedList
            browsingFolder.value != null -> folderList
            browsingTag.value != null -> tagList
            else -> inbox
        }
    }

    private val filteredArticles = combine(baseArticles, filter, sortOrder) { list, f, sort ->
        val filtered = when (f) {
            ArticleFilter.ALL -> list
            ArticleFilter.UNREAD -> list.filter { !it.article.isRead }
            ArticleFilter.FAVORITES -> list.filter { it.article.isFavorite }
            ArticleFilter.DONE -> list.filter { it.article.isRead }
        }
        when (sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.article.addedAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.article.addedAt }
            SortOrder.READING_TIME -> filtered.sortedByDescending { readingMinutes(it.article.contentText) }
        }
    }

    /** Estimated reading minutes at ~200 wpm. */
    private fun readingMinutes(contentText: String?): Int {
        if (contentText.isNullOrBlank()) return 0
        return (contentText.split(Regex("\\s+")).size / 200).coerceAtLeast(1)
    }

    private val searchResults = searchQuery
        .debounce(250)
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.length < 2) flowOf(emptyList()) else repo.search(q)
        }

    private val filteredFeeds = combine(feedRepo.observeFeeds(), selectedFeedCategoryId) { list, catId ->
        if (catId == null) list else list.filter { it.feed.categoryId == catId }
    }

    /** True while a feed sync / OPML import is running (UI progress). */
    private val syncActive = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        filteredArticles,
        repo.observeUnreadCount(),
        repo.observeFolders(),
        repo.observeTags(),
        settingsRepo.settings,
        tab,
        filter,
        searchQuery,
        searchActive,
        searchResults,
        browsingFolder,
        browsingTag,
        browsingFeed,
        actionsArticle,
        addSheetVisible,
        addSheetPrefill,
        addState,
        settingsVisible,
        newFolderVisible,
        newTagVisible,
        editingFolder,
        editingTag,
        filteredFeeds,
        feedRepo.observeCategories(),
        selectedFeedCategoryId,
        showAddFeedSheet,
        editingFeed,
        showNewCategory,
        syncActive,
        sortOrder,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            articles = values[0] as List<ArticleWithTags>,
            unreadCount = values[1] as Int,
            folders = values[2] as List<FolderWithCount>,
            tags = values[3] as List<TagWithCount>,
            settingsState = values[4] as ReaderSettings,
            tab = values[5] as Int,
            filter = values[6] as ArticleFilter,
            searchQuery = values[7] as String,
            searchActive = values[8] as Boolean,
            searchResults = values[9] as List<ArticleWithTags>,
            browsingFolderId = values[10] as Long?,
            browsingTagId = values[11] as Long?,
            browsingFeedId = values[12] as Long?,
            actionsArticle = values[13] as ArticleWithTags?,
            addSheetVisible = values[14] as Boolean,
            addSheetPrefill = values[15] as String,
            addState = values[16] as AddState,
            settingsVisible = values[17] as Boolean,
            newFolderVisible = values[18] as Boolean,
            newTagVisible = values[19] as Boolean,
            editingFolder = values[20] as FolderWithCount?,
            editingTag = values[21] as TagWithCount?,
            feeds = values[22] as List<FeedWithCount>,
            feedCategories = values[23] as List<FeedCategoryEntity>,
            selectedFeedCategoryId = values[24] as Long?,
            showAddFeedSheet = values[25] as Boolean,
            editingFeed = values[26] as FeedWithCount?,
            showNewCategory = values[27] as Boolean,
            syncActive = values[28] as Boolean,
            sortOrder = values[29] as SortOrder,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    // ------------------------------------------------------------------ tabs

    fun selectTab(i: Int) {
        tab.value = i
        browsingFolder.value = null
        browsingTag.value = null
        browsingFeed.value = null
    }

    fun openFeed(id: Long) {
        browsingFeed.value = id
    }

    fun openAllFeeds() {
        browsingFeed.value = ALL_FEEDS
    }

    fun closeFeed() {
        browsingFeed.value = null
    }

    fun setFilter(f: ArticleFilter) {
        filter.value = f
    }

    fun setSortOrder(order: SortOrder) {
        sortOrder.value = order
    }

    fun openFolder(id: Long) {
        browsingFolder.value = id
        browsingTag.value = null
    }

    fun openTag(id: Long) {
        browsingTag.value = id
        browsingFolder.value = null
    }

    fun closeBrowsing() {
        browsingFolder.value = null
        browsingTag.value = null
    }

    // ---------------------------------------------------------------- search

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }

    fun setSearchActive(active: Boolean) {
        searchActive.value = active
        if (!active) searchQuery.value = ""
    }

    // -------------------------------------------------------------- add link

    fun showAddSheet(prefill: String = "", startImmediately: Boolean = false) {
        addSheetPrefill.value = prefill
        addState.value = AddState.Idle
        addSheetVisible.value = true
        if (startImmediately && prefill.isNotBlank()) addUrl()
    }

    fun hideAddSheet() {
        addSheetVisible.value = false
        addState.value = AddState.Idle
    }

    fun addUrl() {
        val url = addSheetPrefill.value
        if (url.isBlank()) return
        viewModelScope.launch {
            addState.value = AddState.Working
            when (val result = repo.addUrl(url)) {
                is AddUrlResult.Added -> {
                    addState.value = AddState.Done(result.articleId)
                    openArticleEvents.emit(result.articleId)
                    addSheetVisible.value = false
                    addState.value = AddState.Idle
                    if (uiState.value.settingsState.autoOffline) {
                        autoDownloadOffline(result.articleId)
                    }
                }
                is AddUrlResult.AlreadySaved -> {
                    SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_already_library)))
                    addSheetVisible.value = false
                    addState.value = AddState.Idle
                    openArticleEvents.emit(result.articleId)
                }
                is AddUrlResult.Failed -> {
                    addState.value = AddState.Error(result.error)
                }
            }
        }
    }

    fun setAddUrlText(text: String) {
        addSheetPrefill.value = text
    }

    // ------------------------------------------------------------ article ops

    fun openActions(article: ArticleWithTags) {
        actionsArticle.value = article
    }

    fun closeActions() {
        actionsArticle.value = null
    }

    fun toggleRead(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.setRead(article.article.id, !article.article.isRead)
            actionsArticle.value = null
        }
    }

    fun toggleFavorite(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.setFavorite(article.article.id, !article.article.isFavorite)
        }
    }

    fun moveToFolder(articleId: Long, folderId: Long?) {
        viewModelScope.launch {
            repo.setFolder(articleId, folderId)
        }
    }

    fun applyTags(articleId: Long, tagIds: Set<Long>) {
        viewModelScope.launch {
            repo.setArticleTags(articleId, tagIds)
        }
    }

    fun deleteArticle(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.deleteArticle(article.article)
            actionsArticle.value = null
            val tagIds = article.tags.map { it.id }
            SnackbarBus.post(
                SnackbarBus.Event(appContext.getString(R.string.snack_article_deleted), appContext.getString(R.string.common_undo)) {
                    viewModelScope.launch { repo.restoreArticle(article.article, tagIds) }
                }
            )
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

    private fun autoDownloadOffline(articleId: Long) {
        viewModelScope.launch {
            repo.getArticle(articleId)?.let { repo.downloadForOffline(it) }
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
                SnackbarBus.Event(
                    appContext.getString(R.string.snack_offline_done, done, all.size)
                )
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
                    SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_offline_expired, "$removed")))
                }
            }
        }
    }

    fun refresh(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.refreshContent(article.article)
        }
    }

    // ---------------------------------------------------------------- library

    fun createFolder(name: String, colorIndex: Int, parentId: Long? = null) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            repo.createFolder(name.trim(), colorIndex, parentId)
            newFolderVisible.value = false
            editingFolder.value = null
        }
    }

    fun showEditFolder(folder: FolderWithCount) {
        editingFolder.value = folder
    }

    fun hideEditFolder() {
        editingFolder.value = null
    }

    fun updateFolder(folder: FolderWithCount, name: String, colorIndex: Int, parentId: Long?) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            repo.updateFolder(folder.folder, name.trim(), colorIndex, parentId)
            editingFolder.value = null
        }
    }

    fun deleteFolderWithMessage(folder: FolderWithCount) {
        viewModelScope.launch {
            repo.deleteFolder(folder.folder)
            editingFolder.value = null
            if (browsingFolder.value == folder.folder.id) browsingFolder.value = null
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_folder_deleted)))
        }
    }

    fun showEditTag(tag: TagWithCount) {
        editingTag.value = tag
    }

    fun hideEditTag() {
        editingTag.value = null
    }

    fun updateTag(tag: TagWithCount, name: String, colorIndex: Int) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            repo.updateTag(tag.tag, name.trim(), colorIndex)
            editingTag.value = null
        }
    }

    fun deleteTagWithMessage(tag: TagWithCount) {
        viewModelScope.launch {
            repo.deleteTag(tag.tag)
            editingTag.value = null
            if (browsingTag.value == tag.tag.id) browsingTag.value = null
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_tag_deleted)))
        }
    }

    /** Creates a folder and immediately files the given article into it. */
    fun createFolderAndApply(articleId: Long, name: String, colorIndex: Int) {
        viewModelScope.launch {
            val id = repo.createFolder(name.trim(), colorIndex)
            repo.setFolder(articleId, id)
            newFolderVisible.value = false
            actionsArticle.value = null
        }
    }

    fun createTag(name: String, colorIndex: Int) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            repo.createTag(name.trim(), colorIndex)
            newTagVisible.value = false
        }
    }

    /** Creates a tag and applies it to the given article. */
    fun createTagAndApply(articleId: Long, name: String, colorIndex: Int) {
        viewModelScope.launch {
            val id = repo.createTag(name.trim(), colorIndex)
            val current = repo.getArticleTags(articleId).map { it.id }.toMutableSet()
            current += id
            repo.setArticleTags(articleId, current)
            newTagVisible.value = false
            actionsArticle.value = null
        }
    }

    fun showNewFolder() {
        newFolderVisible.value = true
    }

    fun hideNewFolder() {
        newFolderVisible.value = false
    }

    fun showNewTag() {
        newTagVisible.value = true
    }

    fun hideNewTag() {
        newTagVisible.value = false
    }

    // --------------------------------------------------------------- settings

    // ---------------------------------------------------------------- feeds

    fun setFeedCategoryFilter(id: Long?) {
        selectedFeedCategoryId.value = id
    }

    fun showAddFeed() {
        showAddFeedSheet.value = true
    }

    fun hideAddFeed() {
        showAddFeedSheet.value = false
    }

    fun addFeed(url: String, categoryId: Long?) {
        viewModelScope.launch {
            val result = feedRepo.addFeed(url, categoryId)
            result.fold(
                onSuccess = { feed ->
                    showAddFeedSheet.value = false
                    SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_subscribed, "${feed.title}")))
                    viewModelScope.launch {
                        val n = feedRepo.fetchFeed(feed).getOrDefault(0)
                        if (n > 0) SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_imported_articles, "$n")))
                    }
                },
                onFailure = { SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_couldnt_add_feed, "${it.message?.take(60)}"))) },
            )
        }
    }

    fun saveFeedItem(article: ArticleWithTags) {
        viewModelScope.launch {
            repo.saveFeedItem(article.article.id)
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_added_library)))
        }
    }

    fun fetchFeed(feed: FeedWithCount) {
        viewModelScope.launch {
            val n = feedRepo.fetchFeed(feed.feed).getOrDefault(0)
            SnackbarBus.post(
                SnackbarBus.Event(
                    if (n > 0) "Imported $n new articles" else "No new articles in ${feed.feed.title}"
                )
            )
        }
    }

    fun fetchAllFeeds() {
        viewModelScope.launch {
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_refreshing_feeds)))
            val n = feedRepo.fetchAllFeeds()
            SnackbarBus.post(SnackbarBus.Event(if (n > 0) appContext.getString(R.string.snack_imported_new, n)
                        else appContext.getString(R.string.snack_feeds_up_to_date)))
        }
    }

    fun showEditFeed(feed: FeedWithCount) {
        editingFeed.value = feed
    }

    fun hideEditFeed() {
        editingFeed.value = null
    }

    fun updateFeed(feed: FeedWithCount, title: String, categoryId: Long?) {
        viewModelScope.launch {
            feedRepo.renameFeed(feed.feed, title)
            feedRepo.moveFeed(feed.feed, categoryId)
            editingFeed.value = null
        }
    }

    fun deleteFeedWithMessage(feed: FeedWithCount) {
        viewModelScope.launch {
            feedRepo.deleteFeed(feed.feed)
            editingFeed.value = null
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_feed_removed)))
        }
    }

    fun showNewCategory() {
        showNewCategory.value = true
    }

    fun hideNewCategory() {
        showNewCategory.value = false
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            feedRepo.addCategory(name.trim())
            showNewCategory.value = false
        }
    }

    fun deleteCategoryWithMessage(category: FeedCategoryEntity) {
        viewModelScope.launch {
            feedRepo.deleteCategory(category)
            if (selectedFeedCategoryId.value == category.id) selectedFeedCategoryId.value = null
            SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_category_deleted)))
        }
    }

    fun importOpml(content: String) {
        syncActive.value = true
        SyncScheduler.importOpml(appContext, content)
        observeWork(FeedSyncWorker.OPML_WORK)
    }

    fun fetchAllFeedsViaWorker() {
        syncActive.value = true
        val request = androidx.work.OneTimeWorkRequestBuilder<FeedSyncWorker>().build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "pouch-feed-refresh", androidx.work.ExistingWorkPolicy.REPLACE, request,
        )
        observeWork("pouch-feed-refresh")
    }

    private fun observeWork(name: String) {
        viewModelScope.launch {
            WorkManager.getInstance(appContext).getWorkInfosForUniqueWorkFlow(name).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                if (info.state.isFinished) {
                    syncActive.value = false
                    val added = info.outputData.getInt(FeedSyncWorker.KEY_ADDED, 0)
                    val imported = info.outputData.getInt(FeedSyncWorker.KEY_IMPORTED, 0)
                    val msg = when {
                        name == FeedSyncWorker.OPML_WORK && imported > 0 ->
                            appContext.getString(R.string.snack_imported_feeds, imported)
                        name == FeedSyncWorker.OPML_WORK -> appContext.getString(R.string.snack_no_feeds_found)
                        added > 0 -> appContext.getString(R.string.snack_imported_new, added)
                        else -> appContext.getString(R.string.snack_feeds_up_to_date)
                    }
                    SnackbarBus.post(SnackbarBus.Event(msg))
                }
            }
        }
    }

    fun exportOpml(): String =
        feedRepo.exportOpml(uiState.value.feeds, uiState.value.feedCategories)

    suspend fun exportBackup(): String = backupRepo.export()

    suspend fun importBackup(json: String): String = backupRepo.import(json)

    /** Persists a backup to a file the caller picked (SAF). */
    fun writeBackupTo(uri: android.net.Uri) {
        viewModelScope.launch {
            val json = backupRepo.export()
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(json) }
                SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_backup_exported)))
            }.onFailure { SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_backup_export_failed))) }
        }
    }

    /** Reads a backup file the caller picked (SAF) and imports it. */
    fun readBackupFrom(uri: android.net.Uri) {
        viewModelScope.launch {
            val json = runCatching {
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (json.isNullOrBlank()) {
                SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_backup_import_failed)))
                return@launch
            }
            val summary = backupRepo.import(json)
            SnackbarBus.post(SnackbarBus.Event(summary)) // localized in BackupRepository
        }
    }

    fun exportBackupFile() {
        SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_backup_choose_save)))
        backupExportRequested = true
    }

    var backupExportRequested by mutableStateOf(false)
        private set
    fun onBackupExportHandled() { backupExportRequested = false }

    var backupImportRequested by mutableStateOf(false)
        private set
    fun importBackupFile() {
        SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_backup_choose_import)))
        backupImportRequested = true
    }
    fun onBackupImportHandled() { backupImportRequested = false }

    // ------------------------------------------------------- feed scheduling

    fun setFeedInterval(interval: FeedFetchInterval) {
        viewModelScope.launch {
            settingsRepo.setFeedInterval(interval)
            SyncScheduler.schedule(appContext, interval, uiState.value.settingsState.syncOnStart)
        }
    }

    fun setFeedRetention(retention: OfflineRetention) {
        viewModelScope.launch {
            settingsRepo.setFeedRetention(retention)
            val removed = repo.cleanupFeedItems(retention)
            if (removed > 0) SnackbarBus.post(SnackbarBus.Event(appContext.getString(R.string.snack_feed_items_expired, "$removed")))
        }
    }

    fun setSyncOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setSyncOnStart(enabled)
            SyncScheduler.schedule(appContext, uiState.value.settingsState.feedInterval, enabled)
        }
    }

    fun setSyncOnlyWifi(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSyncOnlyWifi(enabled) }
    }

    fun setSyncOnlyCharging(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSyncOnlyCharging(enabled) }
    }

    // --------------------------------------------------------------- settings

    fun showSettings() {
        settingsVisible.value = true
    }

    fun hideSettings() {
        settingsVisible.value = false
    }

    // ------------------------------------------------------------ settings

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun setOled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setOled(enabled) }
    }

    fun setReaderFont(font: ReaderFont) {
        viewModelScope.launch { settingsRepo.setReaderFont(font) }
    }

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch { settingsRepo.setFontSizeScale(scale) }
    }

    fun setLineHeightScale(scale: Float) {
        viewModelScope.launch { settingsRepo.setLineHeightScale(scale) }
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
