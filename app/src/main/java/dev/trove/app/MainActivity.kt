package dev.trove.app

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dev.trove.app.data.FeedSyncWorker
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trove.app.data.ReaderSettings
import dev.trove.app.data.ThemeMode
import dev.trove.app.ui.navigation.AppNavHost
import dev.trove.app.ui.theme.TroveTheme

class MainActivity : ComponentActivity() {

    private val app by lazy { application as TroveApplication }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = ReaderSettings()
            )
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SEPIA -> false
            }
            TroveTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                oled = settings.oled,
                sepia = settings.themeMode == ThemeMode.SEPIA,
            ) {
                AppNavHost(
                    factory = app.viewModelFactory,
                    onShareUrl = ::shareUrl,
                    pendingShareUrl = app.pendingShareUrl,
                    onPendingShareHandled = { app.pendingShareUrl.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let {
                    app.pendingShareUrl.value = it
                }
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { app.pendingShareUrl.value = it }
            ACTION_SAVE_LINK -> app.pendingAddLink.value = true
            ACTION_OPEN_RANDOM -> lifecycleScope.launch {
                app.pendingRandomArticleId.value = app.repository.getRandomArticleId()
            }
            ACTION_FETCH_FEEDS -> {
                app.pendingFetchFeeds.value = true
                val request = androidx.work.OneTimeWorkRequestBuilder<FeedSyncWorker>().build()
                androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                    "pouch-feed-refresh", androidx.work.ExistingWorkPolicy.REPLACE, request,
                )
            }
        }
    }

    companion object {
        const val ACTION_SAVE_LINK = "dev.trove.app.action.SAVE_LINK"
        const val ACTION_OPEN_RANDOM = "dev.trove.app.action.OPEN_RANDOM"
        const val ACTION_FETCH_FEEDS = "dev.trove.app.action.FETCH_FEEDS"
    }

    private fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("""https?://[^\s<>"']+""")
        return regex.find(text)?.value
    }

    private fun shareUrl(url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        runCatching {
            startActivity(Intent.createChooser(send, getString(R.string.reader_share_link)))
        }
    }
}
