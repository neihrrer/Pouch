package dev.trove.app

import android.content.Intent
import android.os.Bundle
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
        val url = when (intent?.action) {
            Intent.ACTION_SEND -> extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!url.isNullOrBlank()) {
            app.pendingShareUrl.value = url
        }
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
            startActivity(Intent.createChooser(send, "Share link"))
        }
    }
}
