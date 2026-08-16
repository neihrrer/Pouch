package dev.trove.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.trove.app.data.ArticleRepository
import dev.trove.app.data.BackupRepository
import dev.trove.app.data.FeedRepository
import dev.trove.app.data.OfflineRetention
import dev.trove.app.data.ReaderExtractor
import dev.trove.app.data.SettingsRepository
import dev.trove.app.data.WebFetcher
import dev.trove.app.data.db.AppDatabase
import dev.trove.app.ui.home.HomeViewModel
import dev.trove.app.ui.reader.ReaderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TroveApplication : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ArticleRepository by lazy {
        ArticleRepository(database, WebFetcher(), ReaderExtractor(), this)
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val feedRepository: FeedRepository by lazy {
        FeedRepository(database, WebFetcher(), ReaderExtractor())
    }
    val backupRepository: BackupRepository by lazy { BackupRepository(database) }

    /** URL shared into the app (share sheet / deep link), consumed by Home. */
    val pendingShareUrl = MutableStateFlow<String?>(null)

    /** App-shortcut events, consumed by Home. */
    val pendingAddLink = MutableStateFlow(false)
    val pendingRandomArticleId = MutableStateFlow<Long?>(null)
    val pendingFetchFeeds = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        // Housekeeping: expire offline downloads + schedule feed syncing
        // (periodic per the interval setting, plus one-time on start).
        applicationScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.offlineRetention != OfflineRetention.ALWAYS) {
                repository.cleanupOfflineDownloads(settings.offlineRetention)
            }
            dev.trove.app.data.SyncScheduler.schedule(this@TroveApplication, settings.feedInterval, settings.syncOnStart)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(WebFetcher.defaultClient()))
            }
            .crossfade(180)
            .build()

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer<HomeViewModel> {
            HomeViewModel(applicationContext, repository, settingsRepository, feedRepository, backupRepository)
        }
        initializer<ReaderViewModel> {
            val id = createSavedStateHandle().get<Long>("articleId") ?: 0L
            ReaderViewModel(repository, settingsRepository, id)
        }
    }
}
